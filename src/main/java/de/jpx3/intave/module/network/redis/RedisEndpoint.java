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

package de.jpx3.intave.module.network.redis;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;

/**
 * Where and how to reach Redis, parsed from {@code redis://[user[:password]@]host[:port][/db]} or
 * {@code rediss://...} (TLS). Credentials are never part of {@link #describe()} or {@link #toString()}.
 */
public final class RedisEndpoint {
  private final String host;
  private final int port;
  private final String username;
  private final String password;
  private final int database;
  private final boolean tls;
  private final int timeoutMillis;

  RedisEndpoint(String host, int port, String username, String password, int database, boolean tls, int timeoutMillis) {
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
    this.database = database;
    this.tls = tls;
    this.timeoutMillis = timeoutMillis;
  }

  public static RedisEndpoint parse(String uri, int timeoutMillis) {
    if (uri == null || uri.trim().isEmpty()) {
      throw new IllegalArgumentException("network.redis.uri is empty");
    }
    URI parsed;
    try {
      parsed = new URI(uri.trim());
    } catch (URISyntaxException exception) {
      // The message would echo the URI (and its password); keep it generic.
      throw new IllegalArgumentException("network.redis.uri is not a valid URI");
    }
    String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(java.util.Locale.ROOT);
    if (!scheme.equals("redis") && !scheme.equals("rediss")) {
      throw new IllegalArgumentException("network.redis.uri must start with redis:// or rediss://");
    }
    String host = parsed.getHost();
    if (host == null || host.isEmpty()) {
      throw new IllegalArgumentException("network.redis.uri has no host");
    }
    int port = parsed.getPort() > 0 ? parsed.getPort() : 6379;
    String username = null;
    String password = null;
    String userInfo = parsed.getRawUserInfo();
    if (userInfo != null && !userInfo.isEmpty()) {
      int colon = userInfo.indexOf(':');
      if (colon < 0) {
        password = decode(userInfo);
      } else {
        String user = decode(userInfo.substring(0, colon));
        username = user.isEmpty() ? null : user;
        password = decode(userInfo.substring(colon + 1));
      }
    }
    int database = 0;
    String path = parsed.getPath();
    if (path != null && path.length() > 1) {
      try {
        database = Integer.parseInt(path.substring(1));
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("network.redis.uri database must be a number");
      }
    }
    return new RedisEndpoint(
      host, port, username, password, database, scheme.equals("rediss"), Math.max(250, timeoutMillis)
    );
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (UnsupportedEncodingException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public String host() {
    return host;
  }

  public int port() {
    return port;
  }

  String username() {
    return username;
  }

  String password() {
    return password;
  }

  public int database() {
    return database;
  }

  public boolean tls() {
    return tls;
  }

  public int timeoutMillis() {
    return timeoutMillis;
  }

  /** {@code host:port/db}, never credentials. */
  public String describe() {
    return host + ":" + port + "/" + database + (tls ? " (tls)" : "");
  }

  @Override
  public String toString() {
    return describe();
  }
}
