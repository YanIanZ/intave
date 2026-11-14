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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * Player storage as kept in Redis: {@code "WSB1" | savedAt (8) | length (4) | payload | HMAC-SHA256 (32)}, the MAC
 * over everything before it with the network secret. A blob that is malformed or not signed with this network's
 * secret reads as absent, so nothing written by another party can be loaded as player storage.
 */
final class StoredStorageBlob {
  private static final int MAGIC = 0x57534231; // WSB1
  private static final int HEADER_BYTES = 4 + 8 + 4;
  private static final int MAC_BYTES = 32;

  final long savedAt;
  final byte[] payload;

  private StoredStorageBlob(long savedAt, byte[] payload) {
    this.savedAt = savedAt;
    this.payload = payload;
  }

  static byte[] encode(byte[] payload, long savedAt, byte[] secret) {
    ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + payload.length + MAC_BYTES);
    buffer.putInt(MAGIC);
    buffer.putLong(savedAt);
    buffer.putInt(payload.length);
    buffer.put(payload);
    byte[] mac = mac(buffer.array(), HEADER_BYTES + payload.length, secret);
    buffer.put(mac);
    return buffer.array();
  }

  /** The verified blob, or null when absent, malformed or signed with another secret. */
  static StoredStorageBlob decode(byte[] data, byte[] secret) {
    if (data == null || data.length < HEADER_BYTES + MAC_BYTES) {
      return null;
    }
    ByteBuffer buffer = ByteBuffer.wrap(data);
    if (buffer.getInt() != MAGIC) {
      return null;
    }
    long savedAt = buffer.getLong();
    int length = buffer.getInt();
    if (length < 0 || length != data.length - HEADER_BYTES - MAC_BYTES) {
      return null;
    }
    byte[] expected = mac(data, HEADER_BYTES + length, secret);
    byte[] given = new byte[MAC_BYTES];
    System.arraycopy(data, HEADER_BYTES + length, given, 0, MAC_BYTES);
    if (!MessageDigest.isEqual(expected, given)) {
      return null;
    }
    byte[] payload = new byte[length];
    System.arraycopy(data, HEADER_BYTES, payload, 0, length);
    return new StoredStorageBlob(savedAt, payload);
  }

  private static byte[] mac(byte[] data, int length, byte[] secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      mac.update(data, 0, length);
      return mac.doFinal();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HmacSHA256 unavailable", exception);
    }
  }
}
