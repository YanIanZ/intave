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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NetworkSanctionStoreTest {
  @Test
  void roundTripsSanction() {
    UUID id = UUID.randomUUID();
    String line = NetworkSanctionStore.encode(
      "Cheater", id, "203.0.113.7", "&cBlocked | appeal at example.com", 0L
    );

    NetworkSanctionStore.Sanction decoded = NetworkSanctionStore.decode(line);

    assertEquals("Cheater", decoded.name);
    assertEquals(id, decoded.id);
    assertEquals("203.0.113.7", decoded.address);
    assertEquals("&cBlocked | appeal at example.com", decoded.reason);
    assertEquals(0L, decoded.expiresAt);
    assertFalse(decoded.expired(System.currentTimeMillis()));
  }

  @Test
  void reportsExpiry() {
    long now = System.currentTimeMillis();
    NetworkSanctionStore.Sanction expired = new NetworkSanctionStore.Sanction(
      "A", UUID.randomUUID(), "", "", now - 1L
    );
    NetworkSanctionStore.Sanction live = new NetworkSanctionStore.Sanction(
      "B", UUID.randomUUID(), "", "", now + 60_000L
    );

    assertTrue(expired.expired(now));
    assertFalse(live.expired(now));
  }

  @Test
  void rejectsMalformedLines() {
    assertNull(NetworkSanctionStore.decode(null));
    assertNull(NetworkSanctionStore.decode(""));
    assertNull(NetworkSanctionStore.decode("v2|a|b|c|d|0"));
    assertNull(NetworkSanctionStore.decode("v1|only-two|parts"));
    assertNull(NetworkSanctionStore.decode("v1|Name|not-a-uuid||e30|0"));
    assertNull(NetworkSanctionStore.decode("v1|Name|" + UUID.randomUUID() + "||!!!|0"));
    assertNull(NetworkSanctionStore.decode("v1||" + UUID.randomUUID() + "||e30|0"));
  }

  @Test
  void rejectsSeparatorInName() {
    assertThrows(IllegalArgumentException.class, () ->
      NetworkSanctionStore.encode("a|b", UUID.randomUUID(), "", "", 0L)
    );
  }
}
