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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class StorageCache {
  private static final long DISK_CLEANUP_INTERVAL_MILLIS = 60_000L;
  private static final long STALE_TEMP_FILE_MILLIS = 3_600_000L;

  private final int maxEntries;
  private final long maxBytes;
  private final long ttlMillis;
  private final Path directory;
  private final long diskTtlMillis;
  private final long maxDiskBytes;
  private final LinkedHashMap<UUID, Entry> entries =
    new LinkedHashMap<UUID, Entry>(16, 0.75F, true);
  private final ExecutorService diskWriter;
  private long totalBytes;
  private volatile long lastDiskCleanup;

  public StorageCache(int maxEntries, long maxBytes, long ttlMillis) {
    this(maxEntries, maxBytes, ttlMillis, null, 0L, Long.MAX_VALUE);
  }

  public StorageCache(
    int maxEntries, long maxBytes, long ttlMillis, Path directory, long diskTtlMillis
  ) {
    this(maxEntries, maxBytes, ttlMillis, directory, diskTtlMillis, Long.MAX_VALUE);
  }

  public StorageCache(
    int maxEntries,
    long maxBytes,
    long ttlMillis,
    Path directory,
    long diskTtlMillis,
    long maxDiskBytes
  ) {
    this.maxEntries = maxEntries;
    this.maxBytes = maxBytes;
    this.ttlMillis = ttlMillis;
    this.directory = directory;
    this.diskTtlMillis = diskTtlMillis;
    this.maxDiskBytes = maxDiskBytes;
    if (directory != null) {
      try {
        Files.createDirectories(directory);
      } catch (IOException exception) {
        throw new ProtocolException("Unable to create storage directory " + directory, exception);
      }
      this.diskWriter = Executors.newSingleThreadExecutor(new DiskThreadFactory());
    } else {
      this.diskWriter = null;
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
    writeDiskAsync(playerId, payload);
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
    deleteDiskAsync(playerId);
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
    cleanupDiskAsync(now);
  }

  /**
   * Blocks until all queued disk writes complete. Used by tests and by {@link #close()}.
   */
  public void flush() {
    ExecutorService writer = diskWriter;
    if (writer == null || writer.isShutdown()) {
      return;
    }
    try {
      writer.submit(new Runnable() {
        @Override
        public void run() {
        }
      }).get(30L, TimeUnit.SECONDS);
    } catch (Exception ignored) {
      Thread.currentThread().interrupt();
    }
  }

  public void close() {
    ExecutorService writer = diskWriter;
    if (writer == null) {
      return;
    }
    writer.shutdown();
    try {
      if (!writer.awaitTermination(10L, TimeUnit.SECONDS)) {
        writer.shutdownNow();
      }
    } catch (InterruptedException exception) {
      writer.shutdownNow();
      Thread.currentThread().interrupt();
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
        deleteDiskAsync(playerId);
        return null;
      }
      byte[] payload = Files.readAllBytes(file);
      if (payload.length == 0 || payload.length > NetworkLimits.MAX_PAYLOAD_BYTES) {
        deleteDiskAsync(playerId);
        return null;
      }
      return new Entry(payload, modified);
    } catch (IOException exception) {
      return null;
    }
  }

  private void writeDiskAsync(final UUID playerId, final byte[] payload) {
    if (diskWriter == null) {
      return;
    }
    try {
      diskWriter.submit(new Runnable() {
        @Override
        public void run() {
          writeDiskNow(playerId, payload);
          enforceDiskBudgetThrottled(System.currentTimeMillis());
        }
      });
    } catch (RejectedExecutionException shuttingDown) {
      writeDiskNow(playerId, payload);
    }
  }

  private void deleteDiskAsync(final UUID playerId) {
    if (diskWriter == null || directory == null) {
      return;
    }
    try {
      diskWriter.submit(new Runnable() {
        @Override
        public void run() {
          try {
            Files.deleteIfExists(directory.resolve(playerId + ".bin"));
          } catch (IOException ignored) {
          }
        }
      });
    } catch (RejectedExecutionException ignored) {
    }
  }

  private void cleanupDiskAsync(final long now) {
    if (diskWriter == null) {
      return;
    }
    try {
      diskWriter.submit(new Runnable() {
        @Override
        public void run() {
          cleanupDiskThrottled(now);
        }
      });
    } catch (RejectedExecutionException ignored) {
    }
  }

  private void writeDiskNow(UUID playerId, byte[] payload) {
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

  private void cleanupDiskThrottled(long now) {
    if (now - lastDiskCleanup < DISK_CLEANUP_INTERVAL_MILLIS) {
      return;
    }
    lastDiskCleanup = now;
    cleanupDisk(now);
  }

  private void enforceDiskBudgetThrottled(long now) {
    if (now - lastDiskCleanup < DISK_CLEANUP_INTERVAL_MILLIS) {
      return;
    }
    lastDiskCleanup = now;
    cleanupDisk(now);
  }

  private void cleanupDisk(long now) {
    if (directory == null) {
      return;
    }
    List<DiskEntry> survivors = new ArrayList<DiskEntry>();
    long total = 0L;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      for (Path file : stream) {
        String name = file.getFileName().toString();
        try {
          if (name.endsWith(".tmp")) {
            if (now - Files.getLastModifiedTime(file).toMillis() > STALE_TEMP_FILE_MILLIS) {
              Files.deleteIfExists(file);
            }
            continue;
          }
          if (!name.endsWith(".bin")) {
            continue;
          }
          long modified = Files.getLastModifiedTime(file).toMillis();
          if (now - modified > diskTtlMillis) {
            Files.deleteIfExists(file);
            continue;
          }
          long size = Files.size(file);
          survivors.add(new DiskEntry(file, size, modified));
          total += size;
        } catch (IOException ignored) {
        }
      }
    } catch (IOException ignored) {
      return;
    }
    if (total <= maxDiskBytes) {
      return;
    }
    Collections.sort(survivors, new Comparator<DiskEntry>() {
      @Override
      public int compare(DiskEntry first, DiskEntry second) {
        return Long.compare(first.modified, second.modified);
      }
    });
    for (DiskEntry victim : survivors) {
      if (total <= maxDiskBytes) {
        break;
      }
      try {
        if (Files.deleteIfExists(victim.path)) {
          total -= victim.size;
        }
      } catch (IOException ignored) {
      }
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

  public long diskBytes() {
    if (directory == null) {
      return 0L;
    }
    long total = 0L;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.bin")) {
      for (Path file : stream) {
        try {
          total += Files.size(file);
        } catch (IOException ignored) {
        }
      }
    } catch (IOException ignored) {
    }
    return total;
  }

  private static final class Entry {
    final byte[] payload;
    final long storedAt;

    Entry(byte[] payload, long storedAt) {
      this.payload = payload;
      this.storedAt = storedAt;
    }
  }

  private static final class DiskEntry {
    final Path path;
    final long size;
    final long modified;

    DiskEntry(Path path, long size, long modified) {
      this.path = path;
      this.size = size;
      this.modified = modified;
    }
  }

  private static final class DiskThreadFactory implements ThreadFactory {
    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    public Thread newThread(Runnable task) {
      Thread thread = new Thread(task, "WuretaProxy-Disk-" + sequence.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    }
  }

}
