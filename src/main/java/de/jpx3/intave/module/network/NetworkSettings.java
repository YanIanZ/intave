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

import de.jpx3.intave.IntaveBuildConfig;
import de.jpx3.intave.IntavePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.charset.StandardCharsets;

public final class NetworkSettings {
  private final boolean enabled;
  private final String serverId;
  private final String channel;
  private final String secret;
  private final boolean syncAlerts;
  private final boolean syncTrustFactor;
  private final boolean syncSanctions;
  private final boolean syncStorage;
  private final long requestTimeoutMillis;
  private final long alertRateLimitMillis;
  private final boolean debug;

  NetworkSettings(
    boolean enabled,
    String serverId,
    String channel,
    String secret,
    boolean syncAlerts,
    boolean syncTrustFactor,
    boolean syncSanctions,
    boolean syncStorage,
    long requestTimeoutMillis,
    long alertRateLimitMillis,
    boolean debug
  ) {
    this.enabled = enabled;
    this.serverId = serverId;
    this.channel = channel;
    this.secret = secret;
    this.syncAlerts = syncAlerts;
    this.syncTrustFactor = syncTrustFactor;
    this.syncSanctions = syncSanctions;
    this.syncStorage = syncStorage;
    this.requestTimeoutMillis = requestTimeoutMillis;
    this.alertRateLimitMillis = alertRateLimitMillis;
    this.debug = debug;
  }

  public static NetworkSettings from(IntavePlugin plugin) {
    YamlConfiguration settings = plugin.settings();
    String defaultChannel = IntaveBuildConfig.BRAND_CHANNEL_NAMESPACE + ":sync";
    String serverId = settings.getString("network.server-id", "server-1").trim();
    if (serverId.isEmpty()) {
      serverId = "server-1";
    }
    String channel = settings.getString("network.channel", defaultChannel).trim();
    if (channel.isEmpty() || !channel.contains(":")) {
      channel = defaultChannel;
    }
    long requestTimeout = settings.getLong("network.storage.request-timeout-ms", 2500L);
    requestTimeout = Math.max(250L, Math.min(30_000L, requestTimeout));
    long alertRateLimit = settings.getLong("network.alert-rate-limit-ms", 1500L);
    alertRateLimit = Math.max(250L, Math.min(60_000L, alertRateLimit));

    return new NetworkSettings(
      settings.getBoolean("network.enabled", false),
      serverId,
      channel,
      settings.getString("network.secret", "").trim(),
      settings.getBoolean("network.sync.alerts", true),
      settings.getBoolean("network.sync.trust-factor", true),
      settings.getBoolean("network.sync.sanctions", true),
      settings.getBoolean("network.sync.storage", true),
      requestTimeout,
      alertRateLimit,
      settings.getBoolean("network.debug", false)
    );
  }

  public boolean enabled() {
    return enabled;
  }

  public String serverId() {
    return serverId;
  }

  public String channel() {
    return channel;
  }

  public String secret() {
    return secret;
  }

  public boolean hasSecret() {
    return secret != null && !secret.trim().isEmpty();
  }

  public byte[] secretBytes() {
    return hasSecret() ? secret.getBytes(StandardCharsets.UTF_8) : new byte[0];
  }

  public boolean syncAlerts() {
    return syncAlerts;
  }

  public boolean syncTrustFactor() {
    return syncTrustFactor;
  }

  public boolean syncSanctions() {
    return syncSanctions;
  }

  public boolean syncStorage() {
    return syncStorage;
  }

  public long requestTimeoutMillis() {
    return requestTimeoutMillis;
  }

  public long alertRateLimitMillis() {
    return alertRateLimitMillis;
  }

  public boolean debug() {
    return debug;
  }
}
