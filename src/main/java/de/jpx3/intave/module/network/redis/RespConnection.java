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

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One blocking RESP2 connection (the Redis wire protocol), binary-safe. Not thread-safe by itself: the
 * owner serialises access ({@link RedisCommands} synchronises, {@link RedisSubscriber} uses one thread).
 *
 * <p>Replies: simple string → {@link String}, error → {@link RedisErrorException}, integer → {@link Long},
 * bulk string → {@code byte[]} (or {@code null}), array → {@link List} (or {@code null}).</p>
 */
final class RespConnection implements Closeable {
  private static final byte[] CRLF = {'\r', '\n'};
  /** Largest bulk reply accepted; storage blobs are far smaller (frames are capped at 256 KiB). */
  private static final int MAX_BULK_BYTES = 8 * 1024 * 1024;
  private static final int MAX_ARRAY_LENGTH = 1_000_000;

  private final Socket socket;
  private final InputStream in;
  private final OutputStream out;

  private RespConnection(Socket socket) throws IOException {
    this.socket = socket;
    this.in = new BufferedInputStream(socket.getInputStream(), 16 * 1024);
    this.out = new BufferedOutputStream(socket.getOutputStream(), 16 * 1024);
  }

  static RespConnection open(RedisEndpoint endpoint) throws IOException {
    Socket socket = endpoint.tls() ? SSLSocketFactory.getDefault().createSocket() : new Socket();
    try {
      socket.setTcpNoDelay(true);
      socket.setKeepAlive(true);
      socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), endpoint.timeoutMillis());
      socket.setSoTimeout(endpoint.timeoutMillis());
      RespConnection connection = new RespConnection(socket);
      if (endpoint.password() != null) {
        if (endpoint.username() != null) {
          connection.call(bytes("AUTH"), bytes(endpoint.username()), bytes(endpoint.password()));
        } else {
          connection.call(bytes("AUTH"), bytes(endpoint.password()));
        }
      }
      if (endpoint.database() != 0) {
        connection.call(bytes("SELECT"), bytes(Integer.toString(endpoint.database())));
      }
      return connection;
    } catch (IOException | RuntimeException exception) {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      throw exception;
    }
  }

  static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  Object call(byte[]... arguments) throws IOException {
    write(arguments);
    return read();
  }

  void write(byte[]... arguments) throws IOException {
    writeAscii("*" + arguments.length);
    for (byte[] argument : arguments) {
      writeAscii("$" + argument.length);
      out.write(argument);
      out.write(CRLF);
    }
    out.flush();
  }

  void setReadTimeout(int millis) throws IOException {
    socket.setSoTimeout(millis);
  }

  Object read() throws IOException {
    int type = in.read();
    if (type < 0) {
      throw new EOFException("Redis closed the connection");
    }
    switch (type) {
      case '+':
        return readLine();
      case '-':
        throw new RedisErrorException(readLine());
      case ':':
        return Long.parseLong(readLine());
      case '$': {
        int length = Integer.parseInt(readLine());
        if (length < 0) {
          return null;
        }
        if (length > MAX_BULK_BYTES) {
          throw new IOException("Redis bulk reply too large: " + length + " bytes");
        }
        byte[] data = new byte[length];
        readFully(data);
        expectCrlf();
        return data;
      }
      case '*': {
        int length = Integer.parseInt(readLine());
        if (length < 0) {
          return null;
        }
        if (length > MAX_ARRAY_LENGTH) {
          throw new IOException("Redis array reply too large: " + length);
        }
        List<Object> items = new ArrayList<>(Math.min(length, 64));
        for (int i = 0; i < length; i++) {
          items.add(read());
        }
        return items;
      }
      default:
        throw new IOException("Unexpected Redis reply type " + (char) type);
    }
  }

  private void writeAscii(String line) throws IOException {
    out.write(line.getBytes(StandardCharsets.US_ASCII));
    out.write(CRLF);
  }

  private String readLine() throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream(32);
    while (true) {
      int next = in.read();
      if (next < 0) {
        throw new EOFException("Redis closed the connection");
      }
      if (next == '\r') {
        int lf = in.read();
        if (lf != '\n') {
          throw new IOException("Malformed Redis reply line");
        }
        return new String(line.toByteArray(), StandardCharsets.UTF_8);
      }
      if (line.size() > 64 * 1024) {
        throw new IOException("Redis reply line too long");
      }
      line.write(next);
    }
  }

  private void readFully(byte[] target) throws IOException {
    int offset = 0;
    while (offset < target.length) {
      int read = in.read(target, offset, target.length - offset);
      if (read < 0) {
        throw new EOFException("Redis closed the connection");
      }
      offset += read;
    }
  }

  private void expectCrlf() throws IOException {
    if (in.read() != '\r' || in.read() != '\n') {
      throw new IOException("Malformed Redis bulk reply");
    }
  }

  @Override
  public void close() {
    try {
      socket.close();
    } catch (IOException ignored) {
    }
  }

  /** An error reply from Redis (for example {@code NOPERM ...} or {@code WRONGPASS ...}). Never carries secrets. */
  static final class RedisErrorException extends IOException {
    RedisErrorException(String message) {
      super(message);
    }
  }
}
