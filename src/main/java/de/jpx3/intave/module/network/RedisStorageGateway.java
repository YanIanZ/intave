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
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.network.redis.RedisCommands;
import de.jpx3.intave.module.player.StorageIOProcessor;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.wureta.proxy.protocol.FrameType;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import de.jpx3.wureta.proxy.protocol.NetworkLimits;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Player storage through Redis — the role WuretaProxy's cache played, without needing a player as carrier.
 *
 * <ul>
 *   <li>Save: the signed blob ({@link StoredStorageBlob}) is written to {@code <prefix>:storage:<uuid>} with the
 *       storage TTL, then a STORE frame tells servers that are waiting for this player.</li>
 *   <li>Request: a blob younger than {@code fresh-ms} answers at once. Otherwise a REQUEST frame asks the server
 *       that still holds the player (it answers with RESPONSE and refreshes the key); a STORE for the player also
 *       answers. After {@code pending-timeout-ms} the stored blob (or nothing) is delivered.</li>
 * </ul>
 * All Redis I/O runs on background executors.
 */
public final class RedisStorageGateway implements NetworkStorageGateway {
  private final RedisMessageTransport transport;
  private final NetworkSettings settings;
  private final RedisNetworkSettings redisSettings;
  private final Consumer<Runnable> executor;
  private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
  private volatile long lastWarning;

  public RedisStorageGateway(
    RedisMessageTransport transport, NetworkSettings settings, RedisNetworkSettings redisSettings
  ) {
    this.transport = transport;
    this.settings = settings;
    this.redisSettings = redisSettings;
    this.executor = BackgroundExecutors::execute;
  }

  @Override
  public void requestStorage(UUID id, Consumer<ByteBuffer> lazyReturn) {
    executor.accept(() -> {
      long now = System.currentTimeMillis();
      StoredStorageBlob stored = null;
      try {
        stored = StoredStorageBlob.decode(commands().get(redisSettings.storageKey(id)), settings.secretBytes());
      } catch (IOException exception) {
        warn("Redis storage read failed: " + exception.getMessage());
      }
      if (stored != null && now - stored.savedAt <= redisSettings.freshMillis()) {
        deliver(id, lazyReturn, stored.payload);
        return;
      }
      UUID requestId = UUID.randomUUID();
      Pending request = new Pending(
        id, lazyReturn, now + redisSettings.pendingTimeoutMillis(), stored == null ? new byte[0] : stored.payload
      );
      pending.put(requestId, request);
      boolean sent = transport.send(new NetworkFrame(
        FrameType.REQUEST, requestId, id, settings.serverId(), "", now, new byte[0]
      ));
      if (!sent) {
        Pending removed = pending.remove(requestId);
        if (removed != null) {
          deliver(id, removed.consumer, removed.fallback);
        }
      }
    });
  }

  @Override
  public void saveStorage(UUID id, ByteBuffer storage) {
    if (storage == null) {
      return;
    }
    ByteBuffer copy = storage.duplicate();
    byte[] payload = new byte[copy.remaining()];
    copy.get(payload);
    if (payload.length == 0 || payload.length > NetworkLimits.MAX_PAYLOAD_BYTES) {
      return;
    }
    // StorageLoader already calls this on a background thread: write directly, so a save queued during shutdown
    // is not lost to another executor hop (or to a dropped queue).
    store(id, payload, true);
  }

  /** Writes the blob; with {@code announce}, also tells servers waiting for this player. */
  private void store(UUID id, byte[] payload, boolean announce) {
    long now = System.currentTimeMillis();
    try {
      commands().set(
        redisSettings.storageKey(id),
        StoredStorageBlob.encode(payload, now, settings.secretBytes()),
        redisSettings.storageTtlMillis()
      );
    } catch (IOException exception) {
      warn("Redis storage write failed: " + exception.getMessage());
    }
    if (announce) {
      transport.send(new NetworkFrame(FrameType.STORE, UUID.randomUUID(), id, settings.serverId(), "", now, payload));
    }
  }

  @Override
  public void handle(NetworkFrame frame) {
    switch (frame.type()) {
      case RESPONSE: {
        Pending request = pending.remove(frame.requestId());
        if (request != null) {
          deliver(request.playerId, request.consumer, frame.payload());
        }
        break;
      }
      case STORE:
        fulfill(frame.playerId(), frame.payload());
        break;
      case REQUEST:
        answer(frame);
        break;
      default:
        break;
    }
  }

  /** A STORE answers every request still waiting for that player. */
  private void fulfill(UUID playerId, byte[] payload) {
    Iterator<Map.Entry<UUID, Pending>> iterator = pending.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<UUID, Pending> entry = iterator.next();
      Pending request = entry.getValue();
      if (request.playerId.equals(playerId) && pending.remove(entry.getKey(), request)) {
        deliver(playerId, request.consumer, payload);
      }
    }
  }

  /** Another server asks for a player that is (still) here: answer with the live storage and refresh the key. */
  private void answer(NetworkFrame frame) {
    Player player = Bukkit.getPlayer(frame.playerId());
    if (player == null || !player.isOnline()) {
      return;
    }
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    Synchronizer.synchronize(user, () -> {
      try {
        ByteBuffer buffer = StorageIOProcessor.outputFrom(user.mainStorage());
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        if (payload.length == 0 || payload.length > NetworkLimits.MAX_PAYLOAD_BYTES) {
          return;
        }
        transport.send(new NetworkFrame(
          FrameType.RESPONSE, frame.requestId(), frame.playerId(), settings.serverId(), frame.source(),
          System.currentTimeMillis(), payload
        ));
        executor.accept(() -> store(frame.playerId(), payload, false));
      } catch (RuntimeException exception) {
        IntaveLogger.logger().warn(
          "Unable to serve cross-server storage for " + player.getName() + ": " + exception.getMessage()
        );
      }
    });
  }

  @Override
  public void tick(long now) {
    for (Map.Entry<UUID, Pending> entry : pending.entrySet()) {
      Pending request = entry.getValue();
      if (now >= request.deadline && pending.remove(entry.getKey(), request)) {
        deliver(request.playerId, request.consumer, request.fallback);
      }
    }
  }

  @Override
  public void failAll() {
    for (Map.Entry<UUID, Pending> entry : pending.entrySet()) {
      Pending request = pending.remove(entry.getKey());
      if (request != null) {
        deliver(request.playerId, request.consumer, request.fallback);
      }
    }
  }

  @Override
  public int pendingCount() {
    return pending.size();
  }

  private RedisCommands commands() {
    return transport.commands();
  }

  private void deliver(UUID playerId, Consumer<ByteBuffer> consumer, byte[] payload) {
    ByteBuffer buffer = payload == null || payload.length == 0 ? ByteBuffer.allocate(0) : ByteBuffer.wrap(payload);
    executor.accept(() -> {
      try {
        consumer.accept(buffer);
      } catch (RuntimeException exception) {
        IntaveLogger.logger().warn("Cross-server storage consumer failed for " + playerId + ": " + exception.getMessage());
      }
    });
  }

  private void warn(String message) {
    long now = System.currentTimeMillis();
    if (now - lastWarning < 60_000L) {
      return;
    }
    lastWarning = now;
    IntaveLogger.logger().warn("[network] " + message);
  }

  private static final class Pending {
    final UUID playerId;
    final Consumer<ByteBuffer> consumer;
    final long deadline;
    final byte[] fallback;

    Pending(UUID playerId, Consumer<ByteBuffer> consumer, long deadline, byte[] fallback) {
      this.playerId = playerId;
      this.consumer = consumer;
      this.deadline = deadline;
      this.fallback = fallback;
    }
  }
}
