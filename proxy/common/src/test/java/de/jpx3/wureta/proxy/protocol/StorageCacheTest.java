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
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class StorageCacheTest {
  @Test
  void persistsStorageToDiskAcrossCacheInstances(@TempDir Path directory) throws InterruptedException {
    UUID playerId = UUID.randomUUID();
    byte[] payload = "durable-storage".getBytes(StandardCharsets.UTF_8);
    StorageCache cache = new StorageCache(10, 1_000_000, 1, directory, 60_000);
    cache.put(playerId, payload, System.currentTimeMillis());
    Thread.sleep(10);

    StorageCache reloaded = new StorageCache(10, 1_000_000, 1, directory, 60_000);
    assertArrayEquals(payload, reloaded.get(playerId, System.currentTimeMillis()));
  }

  @Test
  void evictsLeastRecentlyUsedEntries(@TempDir Path directory) {
    StorageCache cache = new StorageCache(2, 1_000_000, 60_000, directory, 60_000);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID third = UUID.randomUUID();
    cache.put(first, new byte[]{1}, System.currentTimeMillis());
    cache.put(second, new byte[]{2}, System.currentTimeMillis());
    cache.get(first, System.currentTimeMillis());
    cache.put(third, new byte[]{3}, System.currentTimeMillis());

    assertEquals(2, cache.size());
  }

  @Test
  void reportsFreshOnlyWithinMaxAge(@TempDir Path directory) {
    UUID playerId = UUID.randomUUID();
    StorageCache cache = new StorageCache(10, 1_000_000, 60_000, directory, 60_000);
    long now = System.currentTimeMillis();
    cache.put(playerId, new byte[]{1}, now);

    assertArrayEquals(new byte[]{1}, cache.getFresh(playerId, now + 1_000, 5_000));
    assertNull(cache.getFresh(playerId, now + 6_000, 5_000));
  }

  @Test
  void expiresDiskEntries(@TempDir Path directory) throws InterruptedException {
    UUID playerId = UUID.randomUUID();
    StorageCache cache = new StorageCache(10, 1_000_000, 60_000, directory, 1);
    cache.put(playerId, new byte[]{1}, System.currentTimeMillis());
    Thread.sleep(10);

    StorageCache reloaded = new StorageCache(10, 1_000_000, 60_000, directory, 1);
    assertNull(reloaded.get(playerId, System.currentTimeMillis()));
  }
}
