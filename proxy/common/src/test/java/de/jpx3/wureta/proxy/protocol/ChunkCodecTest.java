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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ChunkCodecTest {
  @Test
  void reassemblesChunksInAnyOrder() {
    byte[] frame = payload(100_000);
    List<byte[]> chunks = ChunkCodec.chunk(frame, 30_000);
    ChunkAssembler assembler = new ChunkAssembler();

    byte[] assembled = null;
    for (int i = chunks.size() - 1; i >= 0; i--) {
      assembled = assembler.accept(chunks.get(i));
    }

    assertArrayEquals(frame, assembled);
    assertEquals(0, assembler.pendingCount());
  }

  @Test
  void ignoresDuplicateChunks() {
    byte[] frame = payload(50_000);
    List<byte[]> chunks = ChunkCodec.chunk(frame, 30_000);
    ChunkAssembler assembler = new ChunkAssembler();

    assertNull(assembler.accept(chunks.get(0)));
    assertNull(assembler.accept(chunks.get(0)));
    assertArrayEquals(frame, assembler.accept(chunks.get(1)));
  }

  @Test
  void expiresIncompleteAssemblies() {
    List<byte[]> chunks = ChunkCodec.chunk(payload(50_000), 30_000);
    ChunkAssembler assembler = new ChunkAssembler(1);
    assembler.accept(chunks.get(0));

    assembler.cleanup(System.currentTimeMillis() + 10);

    assertEquals(0, assembler.pendingCount());
  }

  @Test
  void rejectsTooManyChunks() {
    assertThrows(ProtocolException.class, () -> ChunkCodec.chunk(new byte[1_000_000], 30_000));
  }

  @Test
  void rejectsInvalidChunkSize() {
    assertThrows(ProtocolException.class, () -> ChunkCodec.chunk(new byte[10], 0));
    assertThrows(ProtocolException.class, () -> ChunkCodec.chunk(new byte[10], NetworkLimits.MAX_CHUNK_BYTES + 1));
  }

  private static byte[] payload(int size) {
    byte[] payload = new byte[size];
    for (int i = 0; i < size; i++) {
      payload[i] = (byte) (i * 31);
    }
    return payload;
  }
}
