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

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NetworkPayloadsTest {
  @Test
  void roundTripsAlerts() {
    UUID playerId = UUID.randomUUID();
    NetworkPayloads.Alert alert = new NetworkPayloads.Alert(
      playerId, "Player", "physics", "moved incorrectly", "dy=0.42",
      12.5D, 14.25D, "GREEN"
    );

    NetworkPayloads.Alert decoded = NetworkPayloads.Alert.decode(alert.encode());

    assertEquals(playerId, decoded.playerId());
    assertEquals("Player", decoded.playerName());
    assertEquals("physics", decoded.checkName());
    assertEquals("moved incorrectly", decoded.message());
    assertEquals("dy=0.42", decoded.details());
    assertEquals(12.5D, decoded.violationLevelBefore());
    assertEquals(14.25D, decoded.violationLevelAfter());
    assertEquals("GREEN", decoded.trustFactor());
  }

  @Test
  void roundTripsTrustUpdates() {
    UUID playerId = UUID.randomUUID();
    NetworkPayloads.Trust trust = new NetworkPayloads.Trust(playerId, "Player", "RED", "cloud");

    NetworkPayloads.Trust decoded = NetworkPayloads.Trust.decode(trust.encode());

    assertEquals(playerId, decoded.playerId());
    assertEquals("RED", decoded.trustFactor());
    assertEquals("cloud", decoded.reason());
  }

  @Test
  void roundTripsSanctions() {
    UUID playerId = UUID.randomUUID();
    NetworkPayloads.Sanction sanction = new NetworkPayloads.Sanction(
      playerId, "Player", "127.0.0.1", "blacklist", NetworkPayloads.Sanction.BLOCK, 0L
    );

    NetworkPayloads.Sanction decoded = NetworkPayloads.Sanction.decode(sanction.encode());

    assertEquals(playerId, decoded.playerId());
    assertEquals("127.0.0.1", decoded.address());
    assertEquals(NetworkPayloads.Sanction.BLOCK, decoded.type());
  }

  @Test
  void rejectsTruncatedAlertPayloads() {
    UUID playerId = UUID.randomUUID();
    NetworkPayloads.Alert alert = new NetworkPayloads.Alert(
      playerId, "Player", "physics", "message", "", 1D, 2D, "GREEN"
    );
    byte[] encoded = alert.encode();

    byte[] truncated = new byte[encoded.length - 3];
    System.arraycopy(encoded, 0, truncated, 0, truncated.length);

    assertThrows(RuntimeException.class, () -> NetworkPayloads.Alert.decode(truncated));
  }
}
