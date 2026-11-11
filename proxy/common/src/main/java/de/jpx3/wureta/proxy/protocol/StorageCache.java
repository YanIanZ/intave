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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class StorageCache {
  private final int maxEntries;
  private final long maxBytes;
  private final long ttlMillis;
  private final Path directory;
  private final long diskTtlMillis;
  private final LinkedHashMap<UUID, Entry> entries =
    new LinkedHashMap<>(16, 0.75F, true);
  private long totalBytes;
  private long lastDiskCleanup;

  public StorageCache(int maxEntries, long maxBytes, long ttlMillis) {
    this(maxEntries, maxBytes, ttlMillis, null, 0L);
  }

  public StorageCache(
    int maxEntries, long maxBytes, long ttlMillis, Path directory, long diskTtlMillis
  ) {
    this.maxEntries = maxEntries;
    this.maxBytes = maxBytes;
    this.ttlMillis = ttlMillis;
    this.directory = directory;
    this.diskTtlMillis = diskTtlMillis;
    if (directory != null) {
      try {
        Files.createDirectories(directory);
      } catch (IOException exception) {
        throw new ProtocolException("Unable to create storage directory " + directory, exception);
      }
    }
  }

  public synchronized byte[] get(UUID playerId, long now) {
    Entry entry = entries.get(playerId);
    if (entry != null) {
      if (now - entry.storedAt <= ttlMillis) {
        return entry.payload;
      }
      remove(playerId);
    }
    Entry disk = readDisk(playerId, now);
    if (disk != null) {
      putMemory(playerId, disk.payload, disk.storedAt);
      return disk.payload;
    }
    return null;
  }

  /**
   * Returns a cached payload only when it was stored within {@code maxAgeMillis}. Used by the
   * proxy to avoid serving an old 20-minute autosave to a player who just switched servers;
   * a stale payload is kept as a fallback while the live backend is asked for a fresh copy.
   */
  public synchronized byte[] getFresh(UUID playerId, long now, long maxAgeMillis) {
    byte[] payload = get(playerId, now);
    if (payload == null) {
      return null;
    }
    Entry entry = entries.get(playerId);
    if (entry == null || now - entry.storedAt > maxAgeMillis) {
      return null;
    }
    return payload;
  }

  public synchronized void put(UUID playerId, byte[] payload, long now) {
    if (payload == null || payload.length == 0) {
      remove(playerId);
      return;
    }
    if (payload.length > NetworkLimits.MAX_PAYLOAD_BYTES) {
      return;
    }
    writeDisk(playerId, payload);
    putMemory(playerId, payload, now);
  }

  private void putMemory(UUID playerId, byte[] payload, long now) {
    Entry previous = entries.remove(playerId);
    if (previous != null) {
      totalBytes -= previous.payload.length;
    }
    entries.put(playerId, new Entry(payload, now));
    totalBytes += payload.length;
    evict();
  }

  public synchronized void remove(UUID playerId) {
    Entry entry = entries.remove(playerId);
    if (entry != null) {
      totalBytes -= entry.payload.length;
    }
  }

  public synchronized void cleanup(long now) {
    Iterator<Map.Entry<UUID, Entry>> iterator = entries.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry entry = iterator.next().getValue();
      if (now - entry.storedAt > ttlMillis) {
        totalBytes -= entry.payload.length;
        iterator.remove();
      }
    }
    if (now - lastDiskCleanup >= 60_000L) {
      lastDiskCleanup = now;
      cleanupDisk(now);
    }
  }

  private Entry readDisk(UUID playerId, long now) {
    if (directory == null) {
      return null;
    }
    Path file = directory.resolve(playerId + ".bin");
    try {
      if (!Files.isRegularFile(file)) {
        return null;
      }
      long modified = Files.getLastModifiedTime(file).toMillis();
      if (now - modified > diskTtlMillis) {
        Files.deleteIfExists(file);
        return null;
      }
      byte[] payload = Files.readAllBytes(file);
      if (payload.length == 0 || payload.length > NetworkLimits.MAX_PAYLOAD_BYTES) {
        Files.deleteIfExists(file);
        return null;
      }
      return new Entry(payload, modified);
    } catch (IOException exception) {
      return null;
    }
  }

  private void writeDisk(UUID playerId, byte[] payload) {
    if (directory == null) {
      return;
    }
    Path file = directory.resolve(playerId + ".bin");
    Path temporary = directory.resolve(playerId + ".tmp");
    try {
      Files.write(temporary, payload);
      try {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException atomicMoveFailure) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException exception) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
      }
    }
  }

  private void cleanupDisk(long now) {
    if (directory == null) {
      return;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.bin")) {
      for (Path file : stream) {
        if (now - Files.getLastModifiedTime(file).toMillis() > diskTtlMillis) {
          Files.deleteIfExists(file);
        }
      }
    } catch (IOException ignored) {
    }
  }

  private void evict() {
    Iterator<Map.Entry<UUID, Entry>> iterator = entries.entrySet().iterator();
    while ((entries.size() > maxEntries || totalBytes > maxBytes) && iterator.hasNext()) {
      Entry entry = iterator.next().getValue();
      totalBytes -= entry.payload.length;
      iterator.remove();
    }
  }

  public synchronized int size() {
    return entries.size();
  }

  public synchronized long bytes() {
    return totalBytes;
  }

  private static final class Entry {
    final byte[] payload;
    final long storedAt;

    Entry(byte[] payload, long storedAt) {
      this.payload = payload;
      this.storedAt = storedAt;
    }
  }
}
