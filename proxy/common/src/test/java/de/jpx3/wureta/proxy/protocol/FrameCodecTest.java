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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FrameCodecTest {
  private static final byte[] SECRET = "test-secret-value".getBytes(StandardCharsets.UTF_8);

  @Test
  void roundTripsSignedFrames() {
    NetworkFrame frame = frame(FrameType.ALERT, "survival-1", "lobby-1", "payload".getBytes(StandardCharsets.UTF_8));

    NetworkFrame decoded = FrameCodec.decode(FrameCodec.encode(frame, SECRET), SECRET);

    assertEquals(frame, decoded);
  }

  @Test
  void roundTripsUnsignedFrames() {
    NetworkFrame frame = frame(FrameType.TRUST, "survival-1", "", new byte[]{1, 2, 3});

    NetworkFrame decoded = FrameCodec.decode(FrameCodec.encode(frame, new byte[0]), new byte[0]);

    assertEquals(frame, decoded);
  }

  @Test
  void rejectsTamperedPayload() {
    byte[] encoded = FrameCodec.encode(frame(FrameType.SANCTION, "survival-1", "", new byte[]{7}), SECRET);
    encoded[encoded.length - NetworkLimits.HMAC_BYTES - 1] ^= 1;

    assertThrows(ProtocolException.class, () -> FrameCodec.decode(encoded, SECRET));
  }

  @Test
  void rejectsWrongSecret() {
    byte[] encoded = FrameCodec.encode(frame(FrameType.ALERT, "survival-1", "", new byte[]{1}), SECRET);

    assertThrows(ProtocolException.class, () ->
      FrameCodec.decode(encoded, "other-secret".getBytes(StandardCharsets.UTF_8))
    );
  }

  @Test
  void rejectsSignatureModeMismatch() {
    byte[] signed = FrameCodec.encode(frame(FrameType.ALERT, "survival-1", "", new byte[]{1}), SECRET);
    byte[] unsigned = FrameCodec.encode(frame(FrameType.ALERT, "survival-1", "", new byte[]{1}), new byte[0]);

    assertThrows(ProtocolException.class, () -> FrameCodec.decode(signed, new byte[0]));
    assertThrows(ProtocolException.class, () -> FrameCodec.decode(unsigned, SECRET));
  }

  @Test
  void rejectsStaleFrames() {
    NetworkFrame frame = new NetworkFrame(
      FrameType.ALERT,
      UUID.randomUUID(),
      UUID.randomUUID(),
      "survival-1",
      "",
      System.currentTimeMillis() - NetworkLimits.MAX_CLOCK_SKEW_MILLIS - 1,
      new byte[0]
    );

    assertThrows(ProtocolException.class, () -> FrameCodec.decode(FrameCodec.encode(frame, SECRET), SECRET));
  }

  @Test
  void rejectsUnknownFrameType() {
    NetworkFrame frame = frame(FrameType.ALERT, "survival-1", "", new byte[0]);
    byte[] encoded = FrameCodec.encode(frame, new byte[0]);
    encoded[5] = 99;

    assertThrows(ProtocolException.class, () -> FrameCodec.decode(encoded, new byte[0]));
  }

  @Test
  void preservesPayloadBytes() {
    byte[] payload = new byte[512];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) i;
    }
    NetworkFrame frame = frame(FrameType.STORE, "survival-1", "", payload);

    NetworkFrame decoded = FrameCodec.decode(FrameCodec.encode(frame, SECRET), SECRET);

    assertArrayEquals(payload, decoded.payload());
  }

  private static NetworkFrame frame(FrameType type, String source, String target, byte[] payload) {
    return new NetworkFrame(
      type,
      UUID.randomUUID(),
      UUID.randomUUID(),
      source,
      target,
      System.currentTimeMillis(),
      payload
    );
  }
}
