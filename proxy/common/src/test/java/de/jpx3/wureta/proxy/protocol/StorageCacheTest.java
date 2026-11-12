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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StorageCacheTest {
  @Test
  void persistsStorageToDiskAcrossCacheInstances(@TempDir Path directory) throws InterruptedException {
    UUID playerId = UUID.randomUUID();
    byte[] payload = "durable-storage".getBytes(StandardCharsets.UTF_8);
    StorageCache cache = new StorageCache(10, 1_000_000, 1, directory, 60_000);
    cache.put(playerId, payload, System.currentTimeMillis());
    cache.flush();
    Thread.sleep(10);

    StorageCache reloaded = new StorageCache(10, 1_000_000, 1, directory, 60_000);
    try {
      assertArrayEquals(payload, reloaded.get(playerId, System.currentTimeMillis()));
    } finally {
      cache.close();
      reloaded.close();
    }
  }

  @Test
  void evictsLeastRecentlyUsedEntries(@TempDir Path directory) {
    StorageCache cache = new StorageCache(2, 1_000_000, 60_000, directory, 60_000);
    try {
      UUID first = UUID.randomUUID();
      UUID second = UUID.randomUUID();
      UUID third = UUID.randomUUID();
      cache.put(first, new byte[]{1}, System.currentTimeMillis());
      cache.put(second, new byte[]{2}, System.currentTimeMillis());
      cache.get(first, System.currentTimeMillis());
      cache.put(third, new byte[]{3}, System.currentTimeMillis());

      assertEquals(2, cache.size());
    } finally {
      cache.close();
    }
  }

  @Test
  void reportsFreshOnlyWithinMaxAge(@TempDir Path directory) {
    StorageCache cache = new StorageCache(10, 1_000_000, 60_000, directory, 60_000);
    try {
      UUID playerId = UUID.randomUUID();
      long now = System.currentTimeMillis();
      cache.put(playerId, new byte[]{1}, now);

      assertArrayEquals(new byte[]{1}, cache.getFresh(playerId, now + 1_000, 5_000));
      assertNull(cache.getFresh(playerId, now + 6_000, 5_000));
    } finally {
      cache.close();
    }
  }

  @Test
  void expiresDiskEntries(@TempDir Path directory) throws InterruptedException {
    UUID playerId = UUID.randomUUID();
    StorageCache cache = new StorageCache(10, 1_000_000, 60_000, directory, 1);
    cache.put(playerId, new byte[]{1}, System.currentTimeMillis());
    cache.flush();
    Thread.sleep(10);

    StorageCache reloaded = new StorageCache(10, 1_000_000, 60_000, directory, 1);
    try {
      assertNull(reloaded.get(playerId, System.currentTimeMillis()));
    } finally {
      cache.close();
      reloaded.close();
    }
  }

  @Test
  void enforcesDiskByteBudget(@TempDir Path directory) throws Exception {
    StorageCache cache = new StorageCache(100, 1_000_000, 600_000, directory, 600_000, 250L);
    try {
      long now = System.currentTimeMillis();
      for (int i = 0; i < 5; i++) {
        cache.put(UUID.randomUUID(), new byte[100], now);
      }
      cache.flush();
      // Disk cleanup is throttled to once per minute; advance past the throttle.
      // TTL is 10 minutes so nothing expires by age here, only by budget.
      cache.cleanup(now + 61_000L);
      cache.flush();

      long total = 0L;
      int files = 0;
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.bin")) {
        for (Path file : stream) {
          files++;
          total += Files.size(file);
        }
      }
      assertTrue(total <= 250L, "disk usage " + total + " exceeds budget");
      assertTrue(files <= 2, "expected at most 2 survivors, got " + files);
    } finally {
      cache.close();
    }
  }

  @Test
  void removesDiskEntry(@TempDir Path directory) throws Exception {
    UUID playerId = UUID.randomUUID();
    StorageCache cache = new StorageCache(10, 1_000_000, 60_000, directory, 60_000);
    try {
      cache.put(playerId, new byte[]{1, 2, 3}, System.currentTimeMillis());
      cache.flush();
      assertTrue(Files.isRegularFile(directory.resolve(playerId + ".bin")));

      cache.remove(playerId);
      cache.flush();
      assertTrue(!Files.exists(directory.resolve(playerId + ".bin")));
    } finally {
      cache.close();
    }
  }
}
