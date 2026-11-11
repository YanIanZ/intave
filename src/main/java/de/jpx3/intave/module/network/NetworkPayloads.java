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

import de.jpx3.wureta.proxy.protocol.ProtocolException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class NetworkPayloads {
  private static final int MAX_STRING_BYTES = 1024;

  private NetworkPayloads() {
  }

  public static final class Alert {
    private final UUID playerId;
    private final String playerName;
    private final String checkName;
    private final String message;
    private final String details;
    private final double violationLevelBefore;
    private final double violationLevelAfter;
    private final String trustFactor;

    public Alert(
      UUID playerId,
      String playerName,
      String checkName,
      String message,
      String details,
      double violationLevelBefore,
      double violationLevelAfter,
      String trustFactor
    ) {
      this.playerId = playerId;
      this.playerName = playerName;
      this.checkName = checkName;
      this.message = message;
      this.details = details;
      this.violationLevelBefore = violationLevelBefore;
      this.violationLevelAfter = violationLevelAfter;
      this.trustFactor = trustFactor;
    }

    public byte[] encode() {
      try {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
        DataOutputStream output = new DataOutputStream(buffer);
        writeUuid(output, playerId);
        writeString(output, playerName);
        writeString(output, checkName);
        writeString(output, message);
        writeString(output, details);
        output.writeDouble(violationLevelBefore);
        output.writeDouble(violationLevelAfter);
        writeString(output, trustFactor);
        output.flush();
        return buffer.toByteArray();
      } catch (IOException exception) {
        throw new ProtocolException("Unable to encode alert", exception);
      }
    }

    public static Alert decode(byte[] payload) {
      try {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        return new Alert(
          readUuid(input),
          readString(input),
          readString(input),
          readString(input),
          readString(input),
          input.readDouble(),
          input.readDouble(),
          readString(input)
        );
      } catch (EOFException exception) {
        throw new ProtocolException("Truncated alert payload", exception);
      } catch (IOException exception) {
        throw new ProtocolException("Unable to decode alert", exception);
      }
    }

    public UUID playerId() {
      return playerId;
    }

    public String playerName() {
      return playerName;
    }

    public String checkName() {
      return checkName;
    }

    public String message() {
      return message;
    }

    public String details() {
      return details;
    }

    public double violationLevelBefore() {
      return violationLevelBefore;
    }

    public double violationLevelAfter() {
      return violationLevelAfter;
    }

    public String trustFactor() {
      return trustFactor;
    }
  }

  public static final class Trust {
    private final UUID playerId;
    private final String playerName;
    private final String trustFactor;
    private final String reason;

    public Trust(UUID playerId, String playerName, String trustFactor, String reason) {
      this.playerId = playerId;
      this.playerName = playerName;
      this.trustFactor = trustFactor;
      this.reason = reason;
    }

    public byte[] encode() {
      try {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        DataOutputStream output = new DataOutputStream(buffer);
        writeUuid(output, playerId);
        writeString(output, playerName);
        writeString(output, trustFactor);
        writeString(output, reason);
        output.flush();
        return buffer.toByteArray();
      } catch (IOException exception) {
        throw new ProtocolException("Unable to encode trust update", exception);
      }
    }

    public static Trust decode(byte[] payload) {
      try {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        return new Trust(
          readUuid(input),
          readString(input),
          readString(input),
          readString(input)
        );
      } catch (EOFException exception) {
        throw new ProtocolException("Truncated trust payload", exception);
      } catch (IOException exception) {
        throw new ProtocolException("Unable to decode trust update", exception);
      }
    }

    public UUID playerId() {
      return playerId;
    }

    public String playerName() {
      return playerName;
    }

    public String trustFactor() {
      return trustFactor;
    }

    public String reason() {
      return reason;
    }
  }

  public static final class Sanction {
    public static final byte BLOCK = 1;
    public static final byte KICK = 2;

    private final UUID playerId;
    private final String playerName;
    private final String address;
    private final String reason;
    private final byte type;
    private final long expiresAt;

    public Sanction(
      UUID playerId, String playerName, String address, String reason, byte type, long expiresAt
    ) {
      this.playerId = playerId;
      this.playerName = playerName;
      this.address = address;
      this.reason = reason;
      this.type = type;
      this.expiresAt = expiresAt;
    }

    public byte[] encode() {
      try {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(192);
        DataOutputStream output = new DataOutputStream(buffer);
        writeUuid(output, playerId);
        writeString(output, playerName);
        writeString(output, address);
        writeString(output, reason);
        output.writeByte(type);
        output.writeLong(expiresAt);
        output.flush();
        return buffer.toByteArray();
      } catch (IOException exception) {
        throw new ProtocolException("Unable to encode sanction", exception);
      }
    }

    public static Sanction decode(byte[] payload) {
      try {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        return new Sanction(
          readUuid(input),
          readString(input),
          readString(input),
          readString(input),
          input.readByte(),
          input.readLong()
        );
      } catch (EOFException exception) {
        throw new ProtocolException("Truncated sanction payload", exception);
      } catch (IOException exception) {
        throw new ProtocolException("Unable to decode sanction", exception);
      }
    }

    public UUID playerId() {
      return playerId;
    }

    public String playerName() {
      return playerName;
    }

    public String address() {
      return address;
    }

    public String reason() {
      return reason;
    }

    public byte type() {
      return type;
    }

    public long expiresAt() {
      return expiresAt;
    }
  }

  static void writeUuid(DataOutputStream output, UUID id) throws IOException {
    output.writeLong(id.getMostSignificantBits());
    output.writeLong(id.getLeastSignificantBits());
  }

  static UUID readUuid(DataInputStream input) throws IOException {
    return new UUID(input.readLong(), input.readLong());
  }

  static void writeString(DataOutputStream output, String value) throws IOException {
    String safe = value == null ? "" : value;
    byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_STRING_BYTES) {
      int end = safe.length();
      while (end > 0 && safe.substring(0, end).getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES) {
        end--;
      }
      safe = safe.substring(0, end);
      bytes = safe.getBytes(StandardCharsets.UTF_8);
    }
    output.writeShort(bytes.length);
    output.write(bytes);
  }

  static String readString(DataInputStream input) throws IOException {
    int length = input.readUnsignedShort();
    if (length > MAX_STRING_BYTES) {
      throw new ProtocolException("String exceeds " + MAX_STRING_BYTES + " bytes");
    }
    byte[] bytes = new byte[length];
    input.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
