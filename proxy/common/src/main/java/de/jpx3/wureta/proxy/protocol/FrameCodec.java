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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public final class FrameCodec {
  private static final int FLAG_HMAC = 1;

  private FrameCodec() {
  }

  public static byte[] encode(NetworkFrame frame, byte[] secret) {
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(256 + frame.payload().length);
      DataOutputStream output = new DataOutputStream(buffer);
      boolean signed = secret != null && secret.length > 0;

      output.writeInt(NetworkLimits.FRAME_MAGIC);
      output.writeByte(NetworkLimits.VERSION);
      output.writeByte(frame.type().id());
      output.writeByte(signed ? FLAG_HMAC : 0);
      writeUuid(output, frame.requestId());
      writeUuid(output, frame.playerId());
      output.writeLong(frame.timestamp());
      writeString(output, frame.source(), NetworkLimits.MAX_SERVER_NAME_BYTES);
      writeString(output, frame.target(), NetworkLimits.MAX_SERVER_NAME_BYTES);
      output.writeInt(frame.payload().length);
      output.write(frame.payload());
      output.flush();

      byte[] unsigned = buffer.toByteArray();
      if (!signed) {
        return unsigned;
      }
      byte[] signature = hmac(unsigned, secret);
      byte[] result = new byte[unsigned.length + signature.length];
      System.arraycopy(unsigned, 0, result, 0, unsigned.length);
      System.arraycopy(signature, 0, result, unsigned.length, signature.length);
      return result;
    } catch (IOException exception) {
      throw new ProtocolException("Unable to encode network frame", exception);
    }
  }

  public static NetworkFrame decode(byte[] data, byte[] secret) {
    if (data == null || data.length < 4 + 1 + 1 + 1 + 16 + 16 + 8) {
      throw new ProtocolException("Network frame is too short");
    }

    boolean signed = secret != null && secret.length > 0;
    int contentLength = data.length;
    int flags = data[6] & 0xFF;
    boolean frameSigned = (flags & FLAG_HMAC) != 0;
    if (frameSigned != signed) {
      throw new ProtocolException("Network frame signature mode does not match the configured secret");
    }
    if (frameSigned) {
      if (data.length <= NetworkLimits.HMAC_BYTES) {
        throw new ProtocolException("Network frame is missing its signature");
      }
      contentLength = data.length - NetworkLimits.HMAC_BYTES;
      byte[] expected = hmac(data, contentLength, secret);
      byte[] received = new byte[NetworkLimits.HMAC_BYTES];
      System.arraycopy(data, contentLength, received, 0, received.length);
      if (!MessageDigest.isEqual(expected, received)) {
        throw new ProtocolException("Network frame signature mismatch");
      }
    }

    try {
      DataInputStream input = new DataInputStream(new ByteArrayInputStream(data, 0, contentLength));
      int magic = input.readInt();
      if (magic != NetworkLimits.FRAME_MAGIC) {
        throw new ProtocolException("Invalid network frame magic");
      }
      int version = input.readUnsignedByte();
      if (version != NetworkLimits.VERSION) {
        throw new ProtocolException("Unsupported network frame version " + version);
      }
      FrameType type = FrameType.fromId(input.readUnsignedByte());
      input.readUnsignedByte(); // flags, already validated

      UUID requestId = readUuid(input);
      UUID playerId = readUuid(input);
      long timestamp = input.readLong();
      long now = System.currentTimeMillis();
      if (Math.abs(now - timestamp) > NetworkLimits.MAX_CLOCK_SKEW_MILLIS) {
        throw new ProtocolException("Network frame timestamp is outside the accepted clock window");
      }
      String source = readString(input, NetworkLimits.MAX_SERVER_NAME_BYTES);
      String target = readString(input, NetworkLimits.MAX_SERVER_NAME_BYTES);
      int payloadLength = input.readInt();
      if (payloadLength < 0 || payloadLength > NetworkLimits.MAX_PAYLOAD_BYTES) {
        throw new ProtocolException("Invalid network frame payload length " + payloadLength);
      }
      byte[] payload = new byte[payloadLength];
      input.readFully(payload);
      if (input.available() != 0) {
        throw new ProtocolException("Network frame contains trailing bytes");
      }
      return new NetworkFrame(type, requestId, playerId, source, target, timestamp, payload);
    } catch (EOFException exception) {
      throw new ProtocolException("Truncated network frame", exception);
    } catch (IOException exception) {
      throw new ProtocolException("Unable to decode network frame", exception);
    }
  }

  private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
    output.writeLong(id.getMostSignificantBits());
    output.writeLong(id.getLeastSignificantBits());
  }

  private static UUID readUuid(DataInputStream input) throws IOException {
    return new UUID(input.readLong(), input.readLong());
  }

  private static void writeString(DataOutputStream output, String value, int maxBytes) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > maxBytes) {
      throw new ProtocolException("String exceeds " + maxBytes + " bytes");
    }
    output.writeShort(bytes.length);
    output.write(bytes);
  }

  private static String readString(DataInputStream input, int maxBytes) throws IOException {
    int length = input.readUnsignedShort();
    if (length > maxBytes) {
      throw new ProtocolException("String exceeds " + maxBytes + " bytes");
    }
    byte[] bytes = new byte[length];
    input.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static byte[] hmac(byte[] data, byte[] secret) {
    return hmac(data, data.length, secret);
  }

  private static byte[] hmac(byte[] data, int length, byte[] secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      mac.update(data, 0, length);
      return mac.doFinal();
    } catch (Exception exception) {
      throw new ProtocolException("Unable to sign network frame", exception);
    }
  }
}
