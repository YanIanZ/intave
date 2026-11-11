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

import de.jpx3.wureta.proxy.protocol.ChunkCodec;
import de.jpx3.wureta.proxy.protocol.FrameCodec;
import de.jpx3.wureta.proxy.protocol.FrameType;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import de.jpx3.wureta.proxy.protocol.NetworkLimits;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ProxyMessageTransportTest {
  @Test
  void reassemblesAndDeliversFrames() {
    NetworkSettings settings = settings();
    AtomicReference<NetworkFrame> received = new AtomicReference<>();
    ProxyMessageTransport transport = new ProxyMessageTransport(null, settings, received::set);
    NetworkFrame frame = frame(FrameType.ALERT, "payload".getBytes(StandardCharsets.UTF_8));

    deliver(transport, settings, frame);

    assertNotNull(received.get());
    assertEquals(FrameType.ALERT, received.get().type());
    assertEquals("survival-1", received.get().source());
  }

  @Test
  void dropsReplayedFrames() {
    NetworkSettings settings = settings();
    AtomicInteger count = new AtomicInteger();
    ProxyMessageTransport transport = new ProxyMessageTransport(null, settings, frame -> count.incrementAndGet());
    NetworkFrame frame = frame(FrameType.TRUST, new byte[]{1});

    deliver(transport, settings, frame);
    deliver(transport, settings, frame);

    assertEquals(1, count.get());
  }

  @Test
  void dropsTamperedFrames() {
    NetworkSettings settings = settings();
    AtomicInteger count = new AtomicInteger();
    ProxyMessageTransport transport = new ProxyMessageTransport(null, settings, frame -> count.incrementAndGet());
    NetworkFrame frame = frame(FrameType.SANCTION, new byte[]{1});
    byte[] encoded = FrameCodec.encode(frame, settings.secretBytes());
    encoded[encoded.length - 40] ^= 1;

    for (byte[] chunk : ChunkCodec.chunk(encoded, NetworkLimits.MAX_CHUNK_BYTES)) {
      transport.onPluginMessageReceived(settings.channel(), null, chunk);
    }

    assertEquals(0, count.get());
  }

  private static void deliver(
    ProxyMessageTransport transport, NetworkSettings settings, NetworkFrame frame
  ) {
    byte[] encoded = FrameCodec.encode(frame, settings.secretBytes());
    for (byte[] chunk : ChunkCodec.chunk(encoded, NetworkLimits.MAX_CHUNK_BYTES)) {
      transport.onPluginMessageReceived(settings.channel(), null, chunk);
    }
  }

  private static NetworkFrame frame(FrameType type, byte[] payload) {
    return new NetworkFrame(
      type,
      UUID.randomUUID(),
      UUID.randomUUID(),
      "survival-1",
      "",
      System.currentTimeMillis(),
      payload
    );
  }

  private static NetworkSettings settings() {
    return new NetworkSettings(
      true, "server-1", "wureta:sync", "test-secret",
      true, true, true, true, 2500L, 1500L, false
    );
  }
}
