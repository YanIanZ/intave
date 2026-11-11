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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChunkCodec {
  private ChunkCodec() {
  }

  public static List<byte[]> chunk(byte[] frame, int maxChunkBytes) {
    if (frame == null || frame.length == 0) {
      throw new ProtocolException("Cannot chunk an empty frame");
    }
    if (maxChunkBytes < 1 || maxChunkBytes > NetworkLimits.MAX_CHUNK_BYTES) {
      throw new ProtocolException("Invalid chunk size " + maxChunkBytes);
    }
    int total = (frame.length + maxChunkBytes - 1) / maxChunkBytes;
    if (total < 1 || total > NetworkLimits.MAX_CHUNKS) {
      throw new ProtocolException("Frame requires " + total + " chunks");
    }

    UUID messageId = UUID.randomUUID();
    List<byte[]> chunks = new ArrayList<>(total);
    for (int index = 0; index < total; index++) {
      int offset = index * maxChunkBytes;
      int length = Math.min(maxChunkBytes, frame.length - offset);
      chunks.add(chunk(messageId, index, total, frame, offset, length));
    }
    return chunks;
  }

  private static byte[] chunk(
    UUID messageId, int index, int total, byte[] frame, int offset, int length
  ) {
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(length + 32);
      DataOutputStream output = new DataOutputStream(buffer);
      output.writeInt(NetworkLimits.CHUNK_MAGIC);
      output.writeByte(NetworkLimits.VERSION);
      output.writeByte(0);
      output.writeLong(messageId.getMostSignificantBits());
      output.writeLong(messageId.getLeastSignificantBits());
      output.writeShort(index);
      output.writeShort(total);
      output.writeInt(length);
      output.write(frame, offset, length);
      output.flush();
      return buffer.toByteArray();
    } catch (IOException exception) {
      throw new ProtocolException("Unable to encode chunk", exception);
    }
  }

  static final class ChunkHeader {
    final UUID messageId;
    final int index;
    final int total;
    final byte[] data;

    ChunkHeader(UUID messageId, int index, int total, byte[] data) {
      this.messageId = messageId;
      this.index = index;
      this.total = total;
      this.data = data;
    }
  }

  static ChunkHeader decode(byte[] chunk) {
    if (chunk == null || chunk.length < 4 + 1 + 1 + 16 + 2 + 2 + 4) {
      throw new ProtocolException("Chunk is too short");
    }
    try {
      DataInputStream input = new DataInputStream(new ByteArrayInputStream(chunk));
      int magic = input.readInt();
      if (magic != NetworkLimits.CHUNK_MAGIC) {
        throw new ProtocolException("Invalid chunk magic");
      }
      int version = input.readUnsignedByte();
      if (version != NetworkLimits.VERSION) {
        throw new ProtocolException("Unsupported chunk version " + version);
      }
      input.readUnsignedByte();
      UUID messageId = new UUID(input.readLong(), input.readLong());
      int index = input.readUnsignedShort();
      int total = input.readUnsignedShort();
      if (total < 1 || total > NetworkLimits.MAX_CHUNKS) {
        throw new ProtocolException("Invalid chunk count " + total);
      }
      if (index >= total) {
        throw new ProtocolException("Chunk index " + index + " is outside " + total);
      }
      int length = input.readInt();
      if (length < 0 || length > NetworkLimits.MAX_CHUNK_BYTES) {
        throw new ProtocolException("Invalid chunk data length " + length);
      }
      byte[] data = new byte[length];
      input.readFully(data);
      return new ChunkHeader(messageId, index, total, data);
    } catch (EOFException exception) {
      throw new ProtocolException("Truncated chunk", exception);
    } catch (IOException exception) {
      throw new ProtocolException("Unable to decode chunk", exception);
    }
  }
}
