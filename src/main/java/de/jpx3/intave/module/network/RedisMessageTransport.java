/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.network;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.network.redis.RedisCommands;
import de.jpx3.intave.module.network.redis.RedisEndpoint;
import de.jpx3.intave.module.network.redis.RedisSubscriber;
import de.jpx3.wureta.proxy.protocol.FrameCodec;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import de.jpx3.wureta.proxy.protocol.NetworkLimits;
import de.jpx3.wureta.proxy.protocol.ReplayGuard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Frames over Redis pub/sub ({@code <key-prefix>:frames}). Every frame is HMAC-signed with the network secret
 * ({@link FrameCodec}); a received frame must verify, be inside the clock window and not be a replay before it is
 * handed to the network module on the global region thread. Unlike plugin messages, no online player is needed.
 *
 * <p>Publishing runs on one daemon thread in send order; receiving on the {@link RedisSubscriber} thread. Neither
 * touches a server or region thread.</p>
 */
public final class RedisMessageTransport implements NetworkTransport {
  private static final int MAX_QUEUED_PUBLISHES = 10_000;

  private final NetworkSettings settings;
  private final RedisNetworkSettings redisSettings;
  private final Consumer<NetworkFrame> receiver;
  private final RedisEndpoint endpoint;
  private final RedisCommands commands;
  private final ReplayGuard replayGuard = new ReplayGuard(NetworkLimits.MAX_CLOCK_SKEW_MILLIS, 20_000);
  private final AtomicLong droppedFrames = new AtomicLong();
  private final AtomicLong failedPublishes = new AtomicLong();
  private final AtomicLong sentFrames = new AtomicLong();
  private final AtomicLong receivedFrames = new AtomicLong();
  private RedisSubscriber subscriber;
  private ThreadPoolExecutor publisher;
  private volatile long lastWarning;

  public RedisMessageTransport(
    NetworkSettings settings, RedisNetworkSettings redisSettings, Consumer<NetworkFrame> receiver
  ) {
    this.settings = settings;
    this.redisSettings = redisSettings;
    this.receiver = receiver;
    this.endpoint = RedisEndpoint.parse(redisSettings.uri(), redisSettings.timeoutMillis());
    this.commands = new RedisCommands(endpoint);
  }

  /**
   * Probes the server once (PING, SET/GET/DEL of a probe key, PUBLISH) so a wrong address, password or ACL is
   * reported at boot with Redis's own error (e.g. {@code NOPERM}), then starts the subscriber.
   */
  @Override
  public boolean register() {
    String probeKey = redisSettings.keyPrefix() + ":probe:" + settings.serverId();
    try {
      commands.ping();
      byte[] probe = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
      commands.set(probeKey, probe, 10_000L);
      byte[] read = commands.get(probeKey);
      if (read == null || !java.util.Arrays.equals(read, probe)) {
        throw new IOException("probe key did not read back");
      }
      commands.delete(probeKey);
      commands.publish(redisSettings.framesChannel(), new byte[0]);
    } catch (IOException | RuntimeException exception) {
      IntaveLogger.logger().error(
        "Redis transport unavailable at " + endpoint.describe() + ": " + exception.getMessage()
      );
      commands.close();
      return false;
    }
    publisher = new ThreadPoolExecutor(
      1, 1, 0L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<Runnable>(MAX_QUEUED_PUBLISHES),
      runnable -> {
        Thread thread = new Thread(runnable, "WuretaCore-redis-publisher");
        thread.setDaemon(true);
        return thread;
      },
      (runnable, executor) -> droppedFrames.incrementAndGet()
    );
    subscriber = new RedisSubscriber(
      endpoint, redisSettings.framesChannel(), this::receive,
      message -> IntaveLogger.logger().warn("[network] " + message)
    );
    subscriber.start();
    return true;
  }

  @Override
  public boolean send(NetworkFrame frame) {
    ThreadPoolExecutor executor = publisher;
    if (executor == null || executor.isShutdown()) {
      return false;
    }
    final byte[] encoded;
    try {
      encoded = FrameCodec.encode(frame, settings.secretBytes());
    } catch (RuntimeException exception) {
      droppedFrames.incrementAndGet();
      debug("Dropped outgoing frame: " + exception.getMessage());
      return false;
    }
    try {
      executor.execute(() -> publish(encoded));
    } catch (RuntimeException rejected) {
      droppedFrames.incrementAndGet();
      return false;
    }
    return true;
  }

  private void publish(byte[] encoded) {
    try {
      commands.publish(redisSettings.framesChannel(), encoded);
      sentFrames.incrementAndGet();
    } catch (IOException exception) {
      failedPublishes.incrementAndGet();
      warnRateLimited("Redis publish failed: " + exception.getMessage());
    }
  }

  private void receive(byte[] data) {
    if (data.length == 0) {
      return; // the boot probe
    }
    final NetworkFrame frame;
    try {
      frame = FrameCodec.decode(data, settings.secretBytes());
    } catch (RuntimeException exception) {
      debug("Dropped invalid Redis frame: " + exception.getMessage());
      return;
    }
    if (frame.source().equals(settings.serverId())) {
      return; // our own publish echoed back
    }
    if (!replayGuard.accept(frame.requestId(), frame.timestamp())) {
      return;
    }
    receivedFrames.incrementAndGet();
    Synchronizer.synchronize(() -> receiver.accept(frame));
  }

  /** Blocking access for the storage gateway; callers are background threads. */
  RedisCommands commands() {
    return commands;
  }

  @Override
  public void flush() {
    // Redis needs no carrier; nothing is queued for later.
  }

  @Override
  public void cleanup() {
    replayGuard.cleanup(System.currentTimeMillis());
  }

  @Override
  public void close() {
    if (subscriber != null) {
      subscriber.close();
      subscriber = null;
    }
    ThreadPoolExecutor executor = publisher;
    publisher = null;
    if (executor != null) {
      executor.shutdown();
      try {
        executor.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
    // Module disable order is not fixed: StorageLoader may still be writing the last player storage on background
    // threads after this module stopped. Keep the command connection for those writes and close it a little later.
    Thread closer = new Thread(() -> {
      try {
        Thread.sleep(10_000L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      commands.close();
    }, "WuretaCore-redis-closer");
    closer.setDaemon(true);
    closer.start();
  }

  @Override
  public String describe() {
    return "redis " + endpoint.describe() + " (" + redisSettings.framesChannel() + ")";
  }

  public boolean subscribed() {
    RedisSubscriber current = subscriber;
    return current != null && current.connected();
  }

  @Override
  public int queuedFrames() {
    ThreadPoolExecutor executor = publisher;
    return executor == null ? 0 : executor.getQueue().size();
  }

  @Override
  public long sentFrames() {
    return sentFrames.get();
  }

  @Override
  public long receivedFrames() {
    return receivedFrames.get();
  }

  @Override
  public long droppedFrames() {
    return droppedFrames.get();
  }

  public long failedPublishes() {
    return failedPublishes.get();
  }

  private void warnRateLimited(String message) {
    long now = System.currentTimeMillis();
    if (now - lastWarning < 60_000L) {
      return;
    }
    lastWarning = now;
    IntaveLogger.logger().warn("[network] " + message);
  }

  private void debug(String message) {
    if (settings.debug()) {
      IntaveLogger.logger().info("[network] " + message);
    }
  }
}
