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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static de.jpx3.intave.module.network.redis.RespConnection.bytes;

/**
 * Subscribes to one Redis channel on its own daemon thread and hands every message to a consumer. Reconnects
 * with backoff (1 s doubling to 30 s). A silent connection is detected by a PING after {@code 30 s} without
 * traffic; no answer within the next {@code 30 s} reopens the connection.
 */
public final class RedisSubscriber implements Closeable {
  private static final int IDLE_MILLIS = 30_000;
  private static final long MAX_BACKOFF_MILLIS = 30_000L;

  private final RedisEndpoint endpoint;
  private final String channel;
  private final Consumer<byte[]> messages;
  private final Consumer<String> log;
  private volatile boolean running;
  private volatile boolean connected;
  private volatile RespConnection connection;
  private Thread thread;

  public RedisSubscriber(RedisEndpoint endpoint, String channel, Consumer<byte[]> messages, Consumer<String> log) {
    this.endpoint = endpoint;
    this.channel = channel;
    this.messages = messages;
    this.log = log;
  }

  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    thread = new Thread(this::run, "WuretaCore-redis-subscriber");
    thread.setDaemon(true);
    thread.start();
  }

  public boolean connected() {
    return connected;
  }

  private void run() {
    long backoff = 1000L;
    int failures = 0;
    while (running) {
      RespConnection current = null;
      try {
        current = RespConnection.open(endpoint);
        connection = current;
        current.write(bytes("SUBSCRIBE"), bytes(channel));
        current.read(); // ["subscribe", channel, 1]
        current.setReadTimeout(IDLE_MILLIS);
        connected = true;
        if (failures > 0) {
          log.accept("Redis subscriber reconnected after " + failures + " failed attempt(s)");
        }
        failures = 0;
        backoff = 1000L;
        boolean awaitingPong = false;
        while (running) {
          Object reply;
          try {
            reply = current.read();
          } catch (SocketTimeoutException idle) {
            if (awaitingPong) {
              throw new IOException("Redis subscriber connection is silent");
            }
            current.write(bytes("PING"));
            awaitingPong = true;
            continue;
          }
          awaitingPong = false;
          dispatch(reply);
        }
      } catch (IOException | RuntimeException exception) {
        connected = false;
        if (!running) {
          break;
        }
        failures++;
        if (failures == 1 || failures % 10 == 0) {
          log.accept("Redis subscriber disconnected (" + exception.getMessage() + "), attempt " + failures
            + "; retrying in " + (backoff / 1000L) + " s");
        }
        sleep(backoff);
        backoff = Math.min(MAX_BACKOFF_MILLIS, backoff * 2L);
      } finally {
        connected = false;
        if (current != null) {
          current.close();
        }
        connection = null;
      }
    }
  }

  private void dispatch(Object reply) {
    if (!(reply instanceof List)) {
      return;
    }
    List<?> parts = (List<?>) reply;
    if (parts.size() < 3 || !(parts.get(0) instanceof byte[])) {
      return; // ["pong", ""] and subscription confirmations
    }
    String kind = new String((byte[]) parts.get(0), StandardCharsets.UTF_8);
    if (!kind.equals("message") || !(parts.get(2) instanceof byte[])) {
      return;
    }
    try {
      messages.accept((byte[]) parts.get(2));
    } catch (RuntimeException exception) {
      log.accept("Redis message handler failed: " + exception.getMessage());
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public synchronized void close() {
    running = false;
    RespConnection current = connection;
    if (current != null) {
      current.close(); // unblocks the reader
    }
    if (thread != null) {
      thread.interrupt();
      try {
        thread.join(2000L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      thread = null;
    }
  }
}
