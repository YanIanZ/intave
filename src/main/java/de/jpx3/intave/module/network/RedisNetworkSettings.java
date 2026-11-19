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

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Locale;

/**
 * {@code network.transport} and {@code network.redis.*}. With {@code transport: redis} the backends exchange frames
 * through Redis pub/sub and keep player storage in Redis keys, so no online player is needed as a carrier and the
 * WuretaProxy companion is not used. {@code proxy} (default) keeps the plugin-message transport.
 */
public final class RedisNetworkSettings {
  private final boolean redis;
  private final String uri;
  private final String keyPrefix;
  private final int timeoutMillis;
  private final long storageTtlMillis;
  private final long freshMillis;
  private final long pendingTimeoutMillis;

  private RedisNetworkSettings(
    boolean redis, String uri, String keyPrefix, int timeoutMillis,
    long storageTtlMillis, long freshMillis, long pendingTimeoutMillis
  ) {
    this.redis = redis;
    this.uri = uri;
    this.keyPrefix = keyPrefix;
    this.timeoutMillis = timeoutMillis;
    this.storageTtlMillis = storageTtlMillis;
    this.freshMillis = freshMillis;
    this.pendingTimeoutMillis = pendingTimeoutMillis;
  }

  public static RedisNetworkSettings from(YamlConfiguration settings) {
    String transport = settings.getString("network.transport", "proxy").trim().toLowerCase(Locale.ROOT);
    String prefix = settings.getString("network.redis.key-prefix", "wureta:core").trim();
    while (prefix.endsWith(":")) {
      prefix = prefix.substring(0, prefix.length() - 1);
    }
    return new RedisNetworkSettings(
      transport.equals("redis"),
      settings.getString("network.redis.uri", "").trim(),
      prefix.isEmpty() ? "wureta:core" : prefix,
      clamp(settings.getInt("network.redis.timeout-ms", 2000), 250, 30_000),
      clamp(settings.getLong("network.redis.storage-ttl-seconds", 604_800L), 60L, 31_536_000L) * 1000L,
      clamp(settings.getLong("network.redis.fresh-ms", 5000L), 0L, 60_000L),
      clamp(settings.getLong("network.redis.pending-timeout-ms", 1500L), 100L, 30_000L)
    );
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static long clamp(long value, long min, long max) {
    return Math.max(min, Math.min(max, value));
  }

  public boolean redis() {
    return redis;
  }

  String uri() {
    return uri;
  }

  public String keyPrefix() {
    return keyPrefix;
  }

  public int timeoutMillis() {
    return timeoutMillis;
  }

  public long storageTtlMillis() {
    return storageTtlMillis;
  }

  /** A stored blob younger than this answers a request at once, without asking the other servers. */
  public long freshMillis() {
    return freshMillis;
  }

  /** How long a request waits for a live answer before the stored blob (or nothing) is delivered. */
  public long pendingTimeoutMillis() {
    return pendingTimeoutMillis;
  }

  public String framesChannel() {
    return keyPrefix + ":frames";
  }

  public String storageKey(java.util.UUID playerId) {
    return keyPrefix + ":storage:" + playerId;
  }
}
