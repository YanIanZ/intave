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
import de.jpx3.intave.access.player.storage.StorageGateway;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.player.StorageIOProcessor;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.wureta.proxy.protocol.FrameType;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import de.jpx3.wureta.proxy.protocol.NetworkLimits;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ProxyStorageGateway implements StorageGateway {
  private final FrameSender sender;
  private final NetworkSettings settings;
  private final Consumer<Runnable> executor;
  private final Map<UUID, PendingRequest> pending = new ConcurrentHashMap<>();

  public ProxyStorageGateway(FrameSender sender, NetworkSettings settings) {
    this(sender, settings, BackgroundExecutors::execute);
  }

  ProxyStorageGateway(FrameSender sender, NetworkSettings settings, Consumer<Runnable> executor) {
    this.sender = sender;
    this.settings = settings;
    this.executor = executor;
  }

  @Override
  public void requestStorage(UUID id, Consumer<ByteBuffer> lazyReturn) {
    UUID requestId = UUID.randomUUID();
    PendingRequest request = new PendingRequest(
      id, lazyReturn, System.currentTimeMillis() + settings.requestTimeoutMillis()
    );
    pending.put(requestId, request);
    boolean sent = sender.send(new NetworkFrame(
      FrameType.REQUEST,
      requestId,
      id,
      settings.serverId(),
      "",
      System.currentTimeMillis(),
      new byte[0]
    ));
    if (!sent) {
      PendingRequest removed = pending.remove(requestId);
      if (removed != null) {
        deliver(removed, new byte[0]);
      }
    }
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
    sender.send(new NetworkFrame(
      FrameType.STORE,
      UUID.randomUUID(),
      id,
      settings.serverId(),
      "",
      System.currentTimeMillis(),
      payload
    ));
  }

  public void handle(NetworkFrame frame) {
    if (frame.type() == FrameType.RESPONSE) {
      PendingRequest request = pending.remove(frame.requestId());
      if (request != null) {
        deliver(request, frame.payload());
      }
      return;
    }
    if (frame.type() == FrameType.REQUEST) {
      handleRequest(frame);
    }
  }

  private void handleRequest(NetworkFrame frame) {
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
        sender.send(new NetworkFrame(
          FrameType.RESPONSE,
          frame.requestId(),
          frame.playerId(),
          settings.serverId(),
          frame.source(),
          System.currentTimeMillis(),
          payload
        ));
      } catch (RuntimeException exception) {
        IntaveLogger.logger().warn(
          "Unable to serve cross-server storage for " + player.getName() + ": " + exception.getMessage()
        );
      }
    });
  }

  public void tick(long now) {
    for (Map.Entry<UUID, PendingRequest> entry : pending.entrySet()) {
      PendingRequest request = entry.getValue();
      if (now < request.deadline) {
        continue;
      }
      if (pending.remove(entry.getKey(), request)) {
        deliver(request, new byte[0]);
      }
    }
  }

  public void failAll() {
    for (Map.Entry<UUID, PendingRequest> entry : pending.entrySet()) {
      PendingRequest request = pending.remove(entry.getKey());
      if (request != null) {
        deliver(request, new byte[0]);
      }
    }
  }

  public int pendingCount() {
    return pending.size();
  }

  private void deliver(PendingRequest request, byte[] payload) {
    ByteBuffer buffer = payload == null || payload.length == 0
      ? ByteBuffer.allocate(0)
      : ByteBuffer.wrap(payload);
    executor.accept(() -> {
      try {
        request.consumer.accept(buffer);
      } catch (RuntimeException exception) {
        IntaveLogger.logger().warn(
          "Cross-server storage consumer failed for " + request.playerId + ": " + exception.getMessage()
        );
      }
    });
  }

  private static final class PendingRequest {
    final UUID playerId;
    final Consumer<ByteBuffer> consumer;
    final long deadline;

    PendingRequest(UUID playerId, Consumer<ByteBuffer> consumer, long deadline) {
      this.playerId = playerId;
      this.consumer = consumer;
      this.deadline = deadline;
    }
  }
}
