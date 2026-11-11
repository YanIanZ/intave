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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProxyRouterTest {
  private static final byte[] SECRET = "test-secret-value".getBytes(StandardCharsets.UTF_8);

  @Test
  void broadcastsAlertsAndRewritesSource() {
    Harness harness = new Harness();
    UUID playerId = UUID.randomUUID();

    harness.send("survival-1", new NetworkFrame(
      FrameType.ALERT, UUID.randomUUID(), playerId, "spoofed", "",
      System.currentTimeMillis(), "alert".getBytes(StandardCharsets.UTF_8)
    ));

    NetworkFrame toLobby = harness.next("lobby-1");
    NetworkFrame toCreative = harness.next("creative-1");
    assertNotNull(toLobby);
    assertNotNull(toCreative);
    assertEquals("survival-1", toLobby.source());
    assertEquals(playerId, toLobby.playerId());
    assertNull(harness.next("survival-1"));
    assertEquals(0, harness.router.cacheSize());
  }

  @Test
  void servesCachedStorage() {
    Harness harness = new Harness();
    UUID playerId = UUID.randomUUID();
    byte[] storage = "storage".getBytes(StandardCharsets.UTF_8);

    harness.send("survival-1", new NetworkFrame(
      FrameType.STORE, UUID.randomUUID(), playerId, "survival-1", "",
      System.currentTimeMillis(), storage
    ));
    assertEquals(1, harness.router.cacheSize());

    harness.send("lobby-1", new NetworkFrame(
      FrameType.REQUEST, UUID.randomUUID(), playerId, "lobby-1", "",
      System.currentTimeMillis(), new byte[0]
    ));

    NetworkFrame response = harness.next("lobby-1");
    assertNotNull(response);
    assertEquals(FrameType.RESPONSE, response.type());
    assertEquals(playerId, response.playerId());
    assertArrayEquals(storage, response.payload());
  }

  @Test
  void holdsRequestUntilStoreArrives() throws InterruptedException {
    Harness harness = new Harness();
    UUID playerId = UUID.randomUUID();
    byte[] storage = "late-storage".getBytes(StandardCharsets.UTF_8);

    harness.send("lobby-1", new NetworkFrame(
      FrameType.REQUEST, UUID.randomUUID(), playerId, "lobby-1", "",
      System.currentTimeMillis(), new byte[0]
    ));
    assertNotNull(harness.next("survival-1"));
    assertNull(harness.next("lobby-1"));

    harness.send("survival-1", new NetworkFrame(
      FrameType.STORE, UUID.randomUUID(), playerId, "survival-1", "",
      System.currentTimeMillis(), storage
    ));

    NetworkFrame response = harness.next("lobby-1");
    assertNotNull(response);
    assertEquals(FrameType.RESPONSE, response.type());
    assertArrayEquals(storage, response.payload());
  }

  @Test
  void timesOutPendingRequests() throws InterruptedException {
    Harness harness = new Harness("pending-timeout-ms=1");
    UUID playerId = UUID.randomUUID();

    harness.send("lobby-1", new NetworkFrame(
      FrameType.REQUEST, UUID.randomUUID(), playerId, "lobby-1", "",
      System.currentTimeMillis(), new byte[0]
    ));
    Thread.sleep(150);
    harness.router.tick();

    NetworkFrame response = harness.next("lobby-1");
    assertNotNull(response);
    assertEquals(FrameType.RESPONSE, response.type());
    assertEquals(0, response.payload().length);
  }

  @Test
  void routesDirectFrames() {
    Harness harness = new Harness();

    harness.send("survival-1", new NetworkFrame(
      FrameType.DIRECT, UUID.randomUUID(), UUID.randomUUID(), "survival-1", "lobby-1",
      System.currentTimeMillis(), "direct".getBytes(StandardCharsets.UTF_8)
    ));

    NetworkFrame direct = harness.next("lobby-1");
    assertNotNull(direct);
    assertEquals(FrameType.DIRECT, direct.type());
    assertNull(harness.next("creative-1"));
  }

  @Test
  void answersPingWithCacheStatus() {
    Harness harness = new Harness();

    harness.send("survival-1", new NetworkFrame(
      FrameType.PING, UUID.randomUUID(), new UUID(0L, 0L), "survival-1", "",
      System.currentTimeMillis(), new byte[0]
    ));

    NetworkFrame pong = harness.next("survival-1");
    assertNotNull(pong);
    assertEquals(FrameType.PONG, pong.type());
    assertTrue(new String(pong.payload(), StandardCharsets.UTF_8).contains("servers=3"));
  }

  private static final class Harness {
    final FakePlatform platform = new FakePlatform();
    final ProxyRouter router;

    Harness() {
      this("");
    }

    Harness(String extra) {
      Properties properties = new Properties();
      properties.setProperty("channel", "wureta:sync");
      properties.setProperty("secret", new String(SECRET, StandardCharsets.UTF_8));
      properties.setProperty("pending-timeout-ms", "2500");
      for (String line : extra.split("&")) {
        if (line.isEmpty()) {
          continue;
        }
        int split = line.indexOf('=');
        if (split > 0) {
          properties.setProperty(line.substring(0, split), line.substring(split + 1));
        }
      }
      router = new ProxyRouter(ProxyConfig.from(properties), platform);
    }

    void send(String source, NetworkFrame frame) {
      byte[] encoded = FrameCodec.encode(frame, SECRET);
      for (byte[] chunk : ChunkCodec.chunk(encoded, NetworkLimits.MAX_CHUNK_BYTES)) {
        router.handle(source, chunk);
      }
    }

    NetworkFrame next(String server) {
      byte[] frame = platform.next(server);
      if (frame == null) {
        return null;
      }
      return FrameCodec.decode(frame, SECRET);
    }
  }

  private static final class FakePlatform implements ProxyPlatform {
    private final Set<String> servers = new HashSet<>(Arrays.asList("survival-1", "lobby-1", "creative-1"));
    private final Map<String, ChunkAssembler> assemblers = new HashMap<>();
    private final Map<String, List<byte[]>> frames = new HashMap<>();

    @Override
    public void sendToServer(String serverName, byte[] data) {
      if (!servers.contains(serverName)) {
        return;
      }
      ChunkAssembler assembler = assemblers.computeIfAbsent(serverName, ignored -> new ChunkAssembler());
      byte[] frame = assembler.accept(data);
      if (frame != null) {
        frames.computeIfAbsent(serverName, ignored -> new ArrayList<>()).add(frame);
      }
    }

    @Override
    public void broadcastExcept(String sourceServer, byte[] data) {
      for (String server : servers) {
        if (!server.equals(sourceServer)) {
          sendToServer(server, data);
        }
      }
    }

    @Override
    public Set<String> serverNames() {
      return servers;
    }

    @Override
    public void log(String message) {
    }

    @Override
    public void warn(String message) {
    }

    byte[] next(String server) {
      List<byte[]> queue = frames.get(server);
      if (queue == null || queue.isEmpty()) {
        return null;
      }
      return queue.remove(0);
    }
  }
}
