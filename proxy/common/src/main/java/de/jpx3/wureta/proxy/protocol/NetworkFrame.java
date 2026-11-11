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

import java.util.Arrays;
import java.util.UUID;

public final class NetworkFrame {
  private final FrameType type;
  private final UUID requestId;
  private final UUID playerId;
  private final String source;
  private final String target;
  private final long timestamp;
  private final byte[] payload;

  public NetworkFrame(
    FrameType type,
    UUID requestId,
    UUID playerId,
    String source,
    String target,
    long timestamp,
    byte[] payload
  ) {
    this.type = type;
    this.requestId = requestId;
    this.playerId = playerId == null ? new UUID(0L, 0L) : playerId;
    this.source = source == null ? "" : source;
    this.target = target == null ? "" : target;
    this.timestamp = timestamp;
    this.payload = payload == null ? new byte[0] : payload;
  }

  public FrameType type() {
    return type;
  }

  public UUID requestId() {
    return requestId;
  }

  public UUID playerId() {
    return playerId;
  }

  public String source() {
    return source;
  }

  public String target() {
    return target;
  }

  public long timestamp() {
    return timestamp;
  }

  public byte[] payload() {
    return payload;
  }

  public NetworkFrame withSource(String source) {
    return new NetworkFrame(type, requestId, playerId, source, target, timestamp, payload);
  }

  public NetworkFrame withTarget(String target) {
    return new NetworkFrame(type, requestId, playerId, source, target, timestamp, payload);
  }

  public NetworkFrame withTimestamp(long timestamp) {
    return new NetworkFrame(type, requestId, playerId, source, target, timestamp, payload);
  }

  public NetworkFrame withPayload(byte[] payload) {
    return new NetworkFrame(type, requestId, playerId, source, target, timestamp, payload);
  }

  @Override
  public String toString() {
    return "NetworkFrame{" +
      "type=" + type +
      ", requestId=" + requestId +
      ", playerId=" + playerId +
      ", source='" + source + '\'' +
      ", target='" + target + '\'' +
      ", timestamp=" + timestamp +
      ", payload=" + (payload == null ? 0 : payload.length) + " bytes" +
      '}';
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof NetworkFrame)) {
      return false;
    }
    NetworkFrame frame = (NetworkFrame) other;
    return timestamp == frame.timestamp
      && type == frame.type
      && requestId.equals(frame.requestId)
      && playerId.equals(frame.playerId)
      && source.equals(frame.source)
      && target.equals(frame.target)
      && Arrays.equals(payload, frame.payload);
  }

  @Override
  public int hashCode() {
    int result = type.hashCode();
    result = 31 * result + requestId.hashCode();
    result = 31 * result + playerId.hashCode();
    result = 31 * result + source.hashCode();
    result = 31 * result + target.hashCode();
    result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
    result = 31 * result + Arrays.hashCode(payload);
    return result;
  }
}
