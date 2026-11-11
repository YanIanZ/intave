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
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.wureta.proxy.protocol.ChunkAssembler;
import de.jpx3.wureta.proxy.protocol.ChunkCodec;
import de.jpx3.wureta.proxy.protocol.FrameCodec;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import de.jpx3.wureta.proxy.protocol.NetworkLimits;
import de.jpx3.wureta.proxy.protocol.ProtocolException;
import de.jpx3.wureta.proxy.protocol.ReplayGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ProxyMessageTransport implements PluginMessageListener, FrameSender {
  private static final int MAX_QUEUED_FRAMES = 256;

  private final IntavePlugin plugin;
  private final NetworkSettings settings;
  private final Consumer<NetworkFrame> receiver;
  private final ChunkAssembler assembler = new ChunkAssembler();
  private final ReplayGuard replayGuard = new ReplayGuard();
  private final Queue<byte[][]> queue = new ConcurrentLinkedQueue<>();
  private final AtomicInteger playerIndex = new AtomicInteger();
  private final AtomicLong sentFrames = new AtomicLong();
  private final AtomicLong receivedFrames = new AtomicLong();
  private final AtomicLong droppedFrames = new AtomicLong();
  private long lastWarning;
  private boolean registered;

  public ProxyMessageTransport(
    IntavePlugin plugin, NetworkSettings settings, Consumer<NetworkFrame> receiver
  ) {
    this.plugin = plugin;
    this.settings = settings;
    this.receiver = receiver;
  }

  public boolean register() {
    if (registered) {
      return true;
    }
    try {
      Messenger messenger = Bukkit.getMessenger();
      messenger.registerOutgoingPluginChannel(plugin, settings.channel());
      messenger.registerIncomingPluginChannel(plugin, settings.channel(), this);
      registered = true;
      return true;
    } catch (RuntimeException exception) {
      try {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, settings.channel());
      } catch (RuntimeException ignored) {
      }
      IntaveLogger.logger().error(
        "Unable to register cross-server channel " + settings.channel() + ": " + exception.getMessage()
      );
      return false;
    }
  }

  public void close() {
    if (registered) {
      try {
        Messenger messenger = Bukkit.getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, settings.channel(), this);
        messenger.unregisterOutgoingPluginChannel(plugin, settings.channel());
      } catch (RuntimeException exception) {
        IntaveLogger.logger().warn("Unable to unregister cross-server channel: " + exception.getMessage());
      }
      registered = false;
    }
    queue.clear();
  }

  @Override
  public boolean send(NetworkFrame frame) {
    byte[] encoded;
    List<byte[]> chunkList;
    try {
      encoded = FrameCodec.encode(frame, settings.secretBytes());
      chunkList = ChunkCodec.chunk(encoded, NetworkLimits.MAX_CHUNK_BYTES);
    } catch (ProtocolException exception) {
      droppedFrames.incrementAndGet();
      debug("Dropped outgoing frame: " + exception.getMessage());
      return false;
    }
    byte[][] chunks = chunkList.toArray(new byte[0][]);
    Synchronizer.synchronize(() -> sendNow(chunks));
    sentFrames.incrementAndGet();
    return true;
  }

  @Override
  public void flush() {
    Synchronizer.synchronize(this::flushNow);
  }

  public void cleanup() {
    long now = System.currentTimeMillis();
    assembler.cleanup(now);
    replayGuard.cleanup(now);
  }

  private void sendNow(byte[][] chunks) {
    Player player = nextPlayer();
    if (player == null) {
      if (queue.size() >= MAX_QUEUED_FRAMES) {
        queue.poll();
        droppedFrames.incrementAndGet();
      }
      queue.add(chunks);
      return;
    }
    sendChunks(player, chunks);
  }

  private void flushNow() {
    if (queue.isEmpty()) {
      return;
    }
    Player player = nextPlayer();
    if (player == null) {
      return;
    }
    byte[][] chunks;
    while ((chunks = queue.poll()) != null) {
      sendChunks(player, chunks);
      if (!player.isOnline()) {
        break;
      }
    }
  }

  private void sendChunks(Player player, byte[][] chunks) {
    for (int index = 0; index < chunks.length; index++) {
      if (!player.isOnline()) {
        queue.add(Arrays.copyOfRange(chunks, index, chunks.length));
        return;
      }
      try {
        player.sendPluginMessage(plugin, settings.channel(), chunks[index]);
      } catch (RuntimeException exception) {
        queue.add(Arrays.copyOfRange(chunks, index, chunks.length));
        debug("Unable to send cross-server chunk: " + exception.getMessage());
        return;
      }
    }
  }

  private Player nextPlayer() {
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    if (players.isEmpty()) {
      return null;
    }
    int index = (playerIndex.getAndIncrement() & Integer.MAX_VALUE) % players.size();
    Player player = players.get(index);
    return player.isOnline() ? player : null;
  }

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!settings.channel().equals(channel)) {
      return;
    }
    try {
      byte[] frame = assembler.accept(message);
      if (frame == null) {
        return;
      }
      NetworkFrame decoded = FrameCodec.decode(frame, settings.secretBytes());
      if (!replayGuard.accept(decoded.requestId(), decoded.timestamp())) {
        return;
      }
      receivedFrames.incrementAndGet();
      receiver.accept(decoded);
    } catch (ProtocolException exception) {
      droppedFrames.incrementAndGet();
      warnOnce("Dropped incoming cross-server frame: " + exception.getMessage());
    } catch (RuntimeException exception) {
      droppedFrames.incrementAndGet();
      logWarning("Failed to handle cross-server frame: " + exception.getMessage());
    }
  }

  public long sentFrames() {
    return sentFrames.get();
  }

  public long receivedFrames() {
    return receivedFrames.get();
  }

  public long droppedFrames() {
    return droppedFrames.get();
  }

  public int queuedFrames() {
    return queue.size();
  }

  private void warnOnce(String message) {
    long now = System.currentTimeMillis();
    if (now - lastWarning < 60_000L) {
      return;
    }
    lastWarning = now;
    logWarning(message);
  }

  private static void logWarning(String message) {
    IntaveLogger logger = IntaveLogger.logger();
    if (logger != null) {
      logger.warn("[network] " + message);
    }
  }

  private void debug(String message) {
    if (settings.debug()) {
      IntaveLogger logger = IntaveLogger.logger();
      if (logger != null) {
        logger.info("[network] " + message);
      }
    }
  }
}
