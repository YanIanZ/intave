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

import de.jpx3.wureta.proxy.protocol.FrameType;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProxyStorageGatewayTest {
  @Test
  void sendsStorageRequests() {
    FakeSender sender = new FakeSender(true);
    ProxyStorageGateway gateway = new ProxyStorageGateway(sender, settings(2500L), Runnable::run);
    UUID playerId = UUID.randomUUID();
    AtomicReference<ByteBuffer> received = new AtomicReference<>();

    gateway.requestStorage(playerId, received::set);

    NetworkFrame request = sender.single();
    assertEquals(FrameType.REQUEST, request.type());
    assertEquals(playerId, request.playerId());
    assertEquals("server-1", request.source());
  }

  @Test
  void completesRequestOnResponse() {
    FakeSender sender = new FakeSender(true);
    ProxyStorageGateway gateway = new ProxyStorageGateway(sender, settings(2500L), Runnable::run);
    UUID playerId = UUID.randomUUID();
    byte[] payload = "storage".getBytes(StandardCharsets.UTF_8);
    AtomicReference<ByteBuffer> received = new AtomicReference<>();

    gateway.requestStorage(playerId, received::set);
    NetworkFrame request = sender.single();
    gateway.handle(new NetworkFrame(
      FrameType.RESPONSE, request.requestId(), playerId, "proxy", "server-1",
      System.currentTimeMillis(), payload
    ));

    assertNotNull(received.get());
    byte[] actual = new byte[received.get().remaining()];
    received.get().get(actual);
    assertArrayEquals(payload, actual);
  }

  @Test
  void timesOutRequestsWithAnEmptyBuffer() throws InterruptedException {
    FakeSender sender = new FakeSender(true);
    ProxyStorageGateway gateway = new ProxyStorageGateway(sender, settings(1L), Runnable::run);
    AtomicReference<ByteBuffer> received = new AtomicReference<>();

    gateway.requestStorage(UUID.randomUUID(), received::set);
    Thread.sleep(10);
    gateway.tick(System.currentTimeMillis());

    assertNotNull(received.get());
    assertEquals(0, received.get().remaining());
  }

  @Test
  void sendsStorageBlobs() {
    FakeSender sender = new FakeSender(true);
    ProxyStorageGateway gateway = new ProxyStorageGateway(sender, settings(2500L), Runnable::run);
    UUID playerId = UUID.randomUUID();
    byte[] payload = "storage".getBytes(StandardCharsets.UTF_8);

    gateway.saveStorage(playerId, ByteBuffer.wrap(payload));

    NetworkFrame store = sender.single();
    assertEquals(FrameType.STORE, store.type());
    assertEquals(playerId, store.playerId());
    assertArrayEquals(payload, store.payload());
  }

  @Test
  void failsFastWhenNoTransportIsAvailable() {
    FakeSender sender = new FakeSender(false);
    ProxyStorageGateway gateway = new ProxyStorageGateway(sender, settings(2500L), Runnable::run);
    AtomicReference<ByteBuffer> received = new AtomicReference<>();

    gateway.requestStorage(UUID.randomUUID(), received::set);

    assertNotNull(received.get());
    assertEquals(0, received.get().remaining());
    assertTrue(sender.frames.isEmpty());
  }

  private static NetworkSettings settings(long timeoutMillis) {
    return new NetworkSettings(
      true, "server-1", "wureta:sync", "test-secret",
      true, true, true, true, timeoutMillis, 1500L, false
    );
  }

  private static final class FakeSender implements FrameSender {
    private final boolean available;
    private final List<NetworkFrame> frames = new ArrayList<>();

    FakeSender(boolean available) {
      this.available = available;
    }

    @Override
    public boolean send(NetworkFrame frame) {
      if (!available) {
        return false;
      }
      frames.add(frame);
      return true;
    }

    @Override
    public void flush() {
    }

    NetworkFrame single() {
      assertEquals(1, frames.size());
      return frames.get(0);
    }
  }
}
