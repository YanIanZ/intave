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

package de.jpx3.intave.check.other.protocolscanner.bypass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TeleportBypassLayersTest {
  @Test
  void exactRevertWithConfirmSequenceFlags() {
    AntiBypassDecision decision = TeleportBypassLayers.engine().evaluate(
      context(90.0f, 0.0f, true, true, true, 0, 1)
    );

    assertEquals(AntiBypassDecision.Verdict.VIOLATION, decision.verdict());
    assertTrue(decision.shouldFlag());
  }

  @Test
  void adoptionThenRevertFlags() {
    AntiBypassDecision decision = TeleportBypassLayers.engine().evaluate(
      context(90.0f, 0.0f, true, true, false, 0, 1)
    );

    assertEquals(AntiBypassDecision.Verdict.VIOLATION, decision.verdict());
    assertTrue(decision.shouldFlag());
  }

  @Test
  void singleExactRevertWithoutSequenceIsSuspicious() {
    AntiBypassDecision decision = TeleportBypassLayers.engine().evaluate(
      context(90.0f, 0.0f, false, true, false, 0, 1)
    );

    assertEquals(AntiBypassDecision.Verdict.SUSPICIOUS, decision.verdict());
    assertFalse(decision.shouldFlag());
  }

  @Test
  void combatAndHistoryCorroborate() {
    AntiBypassDecision decision = TeleportBypassLayers.engine().evaluate(
      context(90.0f, 20.0f, false, false, false, 3, 2)
    );

    assertEquals(AntiBypassDecision.Verdict.VIOLATION, decision.verdict());
    assertTrue(decision.shouldFlag());
  }

  @Test
  void singleCombatSignalIsSuspicious() {
    AntiBypassDecision decision = TeleportBypassLayers.engine().evaluate(
      context(90.0f, 20.0f, false, false, false, 1, 1)
    );

    assertEquals(AntiBypassDecision.Verdict.SUSPICIOUS, decision.verdict());
    assertFalse(decision.shouldFlag());
  }

  @Test
  void cleanContextIsClean() {
    AntiBypassDecision decision = TeleportBypassLayers.engine().evaluate(
      context(10.0f, 10.0f, false, false, false, 0, 0)
    );

    assertEquals(AntiBypassDecision.Verdict.OBSERVE, decision.verdict());
    assertFalse(decision.shouldFlag());
  }

  private static TeleportBypassContext context(
    float serverDelta, float clientDelta,
    boolean adopted, boolean nextReverted, boolean confirmHadServerRotation,
    int attacksBeforeAdoption, int refusalsInWindow
  ) {
    return new TeleportBypassContext(
      true, serverDelta, clientDelta, adopted, nextReverted,
      confirmHadServerRotation, attacksBeforeAdoption, refusalsInWindow
    );
  }
}
