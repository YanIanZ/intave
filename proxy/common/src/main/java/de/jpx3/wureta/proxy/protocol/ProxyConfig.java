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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ProxyConfig {
  private final String channel;
  private final String secret;
  private final long cacheTtlMillis;
  private final long diskTtlMillis;
  private final String storageDirectory;
  private final int maxCacheEntries;
  private final long maxCacheBytes;
  private final long pendingTimeoutMillis;
  private final long replayTtlMillis;
  private final int maxMessagesPerSecond;
  private final boolean debug;

  private ProxyConfig(
    String channel,
    String secret,
    long cacheTtlMillis,
    long diskTtlMillis,
    String storageDirectory,
    int maxCacheEntries,
    long maxCacheBytes,
    long pendingTimeoutMillis,
    long replayTtlMillis,
    int maxMessagesPerSecond,
    boolean debug
  ) {
    this.channel = channel;
    this.secret = secret;
    this.cacheTtlMillis = cacheTtlMillis;
    this.diskTtlMillis = diskTtlMillis;
    this.storageDirectory = storageDirectory;
    this.maxCacheEntries = maxCacheEntries;
    this.maxCacheBytes = maxCacheBytes;
    this.pendingTimeoutMillis = pendingTimeoutMillis;
    this.replayTtlMillis = replayTtlMillis;
    this.maxMessagesPerSecond = maxMessagesPerSecond;
    this.debug = debug;
  }

  public static ProxyConfig load(Path file) {
    Properties properties = new Properties();
    if (Files.isRegularFile(file)) {
      try (InputStream input = Files.newInputStream(file)) {
        properties.load(input);
      } catch (IOException exception) {
        throw new ProtocolException("Unable to read " + file, exception);
      }
    } else {
      writeDefault(file);
    }
    return from(properties);
  }

  public static ProxyConfig from(Properties properties) {
    String channel = properties.getProperty("channel", "wureta:sync").trim();
    if (channel.isEmpty() || !channel.contains(":")) {
      throw new ProtocolException("channel must be a namespaced plugin channel such as wureta:sync");
    }
    String secret = properties.getProperty("secret", "").trim();
    long cacheTtlSeconds = parseLong(properties, "cache-ttl-seconds", 600L, 1L, 86_400L);
    long diskTtlSeconds = parseLong(properties, "disk-ttl-seconds", 604_800L, 60L, 31_536_000L);
    String storageDirectory = properties.getProperty("storage-directory", "storage").trim();
    if (storageDirectory.isEmpty()) {
      storageDirectory = "storage";
    }
    int maxCacheEntries = (int) parseLong(properties, "max-cache-entries", 5000L, 1L, 100_000L);
    long maxCacheBytes = parseLong(properties, "max-cache-bytes", 64L * 1024L * 1024L, 1L, 1024L * 1024L * 1024L);
    long pendingTimeout = parseLong(properties, "pending-timeout-ms", 1500L, 100L, 30_000L);
    long replayTtl = parseLong(properties, "replay-ttl-seconds", 300L, 30L, 3600L);
    int maxMessages = (int) parseLong(properties, "max-messages-per-second", 500L, 10L, 100_000L);
    boolean debug = Boolean.parseBoolean(properties.getProperty("debug", "false").trim());

    return new ProxyConfig(
      channel,
      secret,
      cacheTtlSeconds * 1000L,
      diskTtlSeconds * 1000L,
      storageDirectory,
      maxCacheEntries,
      maxCacheBytes,
      pendingTimeout,
      replayTtl * 1000L,
      maxMessages,
      debug
    );
  }

  public static void writeDefault(Path file) {
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      try (OutputStream output = Files.newOutputStream(file)) {
        String template = "# WuretaProxy configuration\n" +
          "# The channel must match network.channel in the backend WuretaCore config.\n" +
          "channel=wureta:sync\n" +
          "# Shared HMAC secret. Set the same value in every backend network.secret.\n" +
          "# Leave empty only on a fully trusted private network.\n" +
          "secret=\n" +
          "cache-ttl-seconds=600\n" +
          "disk-ttl-seconds=604800\n" +
          "storage-directory=storage\n" +
          "max-cache-entries=5000\n" +
          "max-cache-bytes=67108864\n" +
          "pending-timeout-ms=1500\n" +
          "replay-ttl-seconds=300\n" +
          "max-messages-per-second=500\n" +
          "debug=false\n";
        output.write(template.getBytes(StandardCharsets.UTF_8));
      }
    } catch (IOException exception) {
      throw new ProtocolException("Unable to write " + file, exception);
    }
  }

  private static long parseLong(Properties properties, String key, long fallback, long min, long max) {
    String raw = properties.getProperty(key);
    if (raw == null || raw.trim().isEmpty()) {
      return fallback;
    }
    try {
      long value = Long.parseLong(raw.trim());
      return Math.max(min, Math.min(max, value));
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  public String channel() {
    return channel;
  }

  public String secret() {
    return secret;
  }

  public byte[] secretBytes() {
    return secret.isEmpty() ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
  }

  public long cacheTtlMillis() {
    return cacheTtlMillis;
  }

  public long diskTtlMillis() {
    return diskTtlMillis;
  }

  public String storageDirectory() {
    return storageDirectory;
  }

  public int maxCacheEntries() {
    return maxCacheEntries;
  }

  public long maxCacheBytes() {
    return maxCacheBytes;
  }

  public long pendingTimeoutMillis() {
    return pendingTimeoutMillis;
  }

  public long replayTtlMillis() {
    return replayTtlMillis;
  }

  public int maxMessagesPerSecond() {
    return maxMessagesPerSecond;
  }

  public boolean debug() {
    return debug;
  }
}
