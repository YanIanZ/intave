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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PendingRequestsTest {
  @Test
  void removesOnlyTheSelectedRequest() {
    PendingRequests pending = new PendingRequests();
    UUID playerId = UUID.randomUUID();
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    pending.add(playerId, new PendingRequests.Pending("lobby", firstId, playerId, 1L));
    pending.add(playerId, new PendingRequests.Pending("survival", secondId, playerId, 2L));

    assertTrue(pending.remove(playerId, firstId));
    assertFalse(pending.remove(playerId, firstId));
    assertEquals(1, pending.size());

    assertEquals("survival", pending.remove(playerId).get(0).server());
    assertEquals(0, pending.size());
  }

  @Test
  void expiresWithTheStoredFallback() {
    PendingRequests pending = new PendingRequests();
    UUID playerId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    byte[] fallback = new byte[]{1, 2, 3};
    pending.add(playerId, new PendingRequests.Pending("lobby", requestId, playerId, 0L, fallback));

    pending.expire(System.currentTimeMillis(), request ->
      assertEquals(3, request.fallback.length)
    );

    assertEquals(0, pending.size());
  }
}
