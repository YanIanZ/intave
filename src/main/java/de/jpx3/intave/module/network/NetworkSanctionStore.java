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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Line codec for network sanctions persisted by {@code PlayerListService}.
 * Format: {@code v1|name|uuid|address|base64url(reason)|expiresAt}.
 * Unknown versions and malformed lines decode to {@code null} so old files
 * can never break startup after a format change.
 */
public final class NetworkSanctionStore {
  private NetworkSanctionStore() {
  }

  public static final class Sanction {
    public final String name;
    public final UUID id;
    public final String address;
    public final String reason;
    public final long expiresAt;

    public Sanction(String name, UUID id, String address, String reason, long expiresAt) {
      this.name = name;
      this.id = id;
      this.address = address;
      this.reason = reason;
      this.expiresAt = expiresAt;
    }

    public boolean expired(long now) {
      return expiresAt > 0L && now > expiresAt;
    }
  }

  public static String encode(String name, UUID id, String address, String reason, long expiresAt) {
    if (name == null || id == null) {
      throw new IllegalArgumentException("Sanction name and id are required");
    }
    if (containsSeparator(name) || containsSeparator(address)) {
      throw new IllegalArgumentException("Sanction name/address contains a field separator");
    }
    String safeAddress = address == null ? "" : address;
    String safeReason = reason == null ? "" : reason;
    String encodedReason = Base64.getUrlEncoder().withoutPadding()
      .encodeToString(safeReason.getBytes(StandardCharsets.UTF_8));
    return "v1|" + name + "|" + id + "|" + safeAddress + "|" + encodedReason + "|" + expiresAt;
  }

  public static Sanction decode(String line) {
    if (line == null) {
      return null;
    }
    String[] parts = line.split("\\|", -1);
    if (parts.length != 6 || !"v1".equals(parts[0])) {
      return null;
    }
    try {
      String name = parts[1];
      UUID id = UUID.fromString(parts[2]);
      String address = parts[3];
      String reason = new String(
        Base64.getUrlDecoder().decode(parts[4]), StandardCharsets.UTF_8
      );
      long expiresAt = Long.parseLong(parts[5]);
      if (name.isEmpty()) {
        return null;
      }
      return new Sanction(name, id, address, reason, expiresAt);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static boolean containsSeparator(String value) {
    if (value == null) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '|' || c == '\n' || c == '\r') {
        return true;
      }
    }
    return false;
  }
}
