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

import java.io.Closeable;
import java.io.IOException;

import static de.jpx3.intave.module.network.redis.RespConnection.bytes;

/**
 * Blocking Redis commands over one lazily (re)opened connection. Calls are serialised; a broken connection is
 * reopened once per call. Never call from a server or region thread — the network module uses its own threads.
 */
public final class RedisCommands implements Closeable {
  private final RedisEndpoint endpoint;
  private RespConnection connection;
  private boolean closed;

  public RedisCommands(RedisEndpoint endpoint) {
    this.endpoint = endpoint;
  }

  public synchronized Object call(byte[]... arguments) throws IOException {
    if (closed) {
      throw new IOException("Redis client closed");
    }
    IOException failure = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        if (connection == null) {
          connection = RespConnection.open(endpoint);
        }
        return connection.call(arguments);
      } catch (RespConnection.RedisErrorException error) {
        // A server-side error (NOPERM, WRONGTYPE, ...) leaves the connection usable; do not retry.
        throw error;
      } catch (IOException exception) {
        failure = exception;
        dropConnection();
      }
    }
    throw failure;
  }

  public String ping() throws IOException {
    Object reply = call(bytes("PING"));
    return reply instanceof byte[] ? new String((byte[]) reply, java.nio.charset.StandardCharsets.UTF_8) : String.valueOf(reply);
  }

  /** {@code SET key value PX ttl}. */
  public void set(String key, byte[] value, long ttlMillis) throws IOException {
    Object reply = call(bytes("SET"), bytes(key), value, bytes("PX"), bytes(Long.toString(Math.max(1L, ttlMillis))));
    if (!"OK".equals(reply)) {
      throw new IOException("Unexpected SET reply");
    }
  }

  public byte[] get(String key) throws IOException {
    Object reply = call(bytes("GET"), bytes(key));
    return reply instanceof byte[] ? (byte[]) reply : null;
  }

  public void delete(String key) throws IOException {
    call(bytes("DEL"), bytes(key));
  }

  /** Returns the number of subscribers that received the message. */
  public long publish(String channel, byte[] message) throws IOException {
    Object reply = call(bytes("PUBLISH"), bytes(channel), message);
    return reply instanceof Long ? (Long) reply : 0L;
  }

  public RedisEndpoint endpoint() {
    return endpoint;
  }

  private void dropConnection() {
    if (connection != null) {
      connection.close();
      connection = null;
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    dropConnection();
  }
}
