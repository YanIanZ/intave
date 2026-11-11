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

package de.jpx3.wureta.proxy.protocol;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkAssembler {
  private final Map<UUID, PendingMessage> pending = new ConcurrentHashMap<>();
  private final long timeoutMillis;

  public ChunkAssembler() {
    this(NetworkLimits.ASSEMBLY_TIMEOUT_MILLIS);
  }

  public ChunkAssembler(long timeoutMillis) {
    this.timeoutMillis = timeoutMillis;
  }

  public byte[] accept(byte[] chunk) {
    ChunkCodec.ChunkHeader header = ChunkCodec.decode(chunk);
    PendingMessage message = pending.get(header.messageId);
    if (message == null) {
      PendingMessage created = new PendingMessage(header.total);
      PendingMessage existing = pending.putIfAbsent(header.messageId, created);
      message = existing == null ? created : existing;
    }
    if (message.total != header.total) {
      pending.remove(header.messageId);
      throw new ProtocolException("Chunk count changed during assembly");
    }
    synchronized (message) {
      if (message.received[header.index]) {
        return null;
      }
      message.received[header.index] = true;
      message.chunks[header.index] = header.data;
      message.receivedCount++;
      message.totalBytes += header.data.length;
      if (message.totalBytes > NetworkLimits.MAX_PAYLOAD_BYTES) {
        pending.remove(header.messageId);
        throw new ProtocolException("Assembled payload exceeds the maximum size");
      }
      if (message.receivedCount != message.total) {
        return null;
      }
    }
    pending.remove(header.messageId);
    return join(message.chunks);
  }

  public void cleanup(long now) {
    Iterator<Map.Entry<UUID, PendingMessage>> iterator = pending.entrySet().iterator();
    while (iterator.hasNext()) {
      PendingMessage message = iterator.next().getValue();
      if (now - message.createdAt > timeoutMillis) {
        iterator.remove();
      }
    }
  }

  public int pendingCount() {
    return pending.size();
  }

  private static byte[] join(byte[][] chunks) {
    int length = 0;
    for (byte[] chunk : chunks) {
      length += chunk.length;
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] chunk : chunks) {
      System.arraycopy(chunk, 0, result, offset, chunk.length);
      offset += chunk.length;
    }
    return result;
  }

  private static final class PendingMessage {
    final int total;
    final byte[][] chunks;
    final boolean[] received;
    final long createdAt = System.currentTimeMillis();
    int receivedCount;
    int totalBytes;

    PendingMessage(int total) {
      this.total = total;
      this.chunks = new byte[total][];
      this.received = new boolean[total];
    }
  }
}
