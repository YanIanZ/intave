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

/**
 * Logical message types carried by the Wureta cross-server protocol.
 *
 * <p>Alert, trust and sanction frames are broadcast to every other backend. Storage frames are
 * handled by the proxy cache. The proxy never interprets alert/trust/sanction payloads, so new
 * payload fields can be added without upgrading the proxy.
 */
public enum FrameType {
  ALERT(10),
  TRUST(11),
  SANCTION(12),
  DIRECT(2),
  STORE(3),
  REQUEST(4),
  RESPONSE(5),
  PING(6),
  PONG(7);

  private final int id;

  FrameType(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  public boolean broadcast() {
    return this == ALERT || this == TRUST || this == SANCTION;
  }

  public boolean storage() {
    return this == STORE || this == REQUEST || this == RESPONSE;
  }

  public static FrameType fromId(int id) {
    for (FrameType type : values()) {
      if (type.id == id) {
        return type;
      }
    }
    throw new ProtocolException("Unknown frame type " + id);
  }
}
