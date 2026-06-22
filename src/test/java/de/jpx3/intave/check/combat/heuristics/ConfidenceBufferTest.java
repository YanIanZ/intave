package de.jpx3.intave.check.combat.heuristics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the heuristic engine's time-decaying confidence primitives.
 */
final class ConfidenceBufferTest {

  @Test
  void decaysByHalfEveryHalfLife() {
    ConfidenceBuffer buffer = new ConfidenceBuffer(1_000d);
    buffer.add(10d, 0L);

    assertEquals(10d, buffer.value(0L), 1.0e-6, "value is intact immediately after adding");
    assertEquals(5d, buffer.value(1_000L), 1.0e-6, "one half-life halves the value");
    assertEquals(2.5d, buffer.value(2_000L), 1.0e-6, "two half-lives quarter the value");
  }

  @Test
  void addReinforcesOnTopOfDecayedValue() {
    ConfidenceBuffer buffer = new ConfidenceBuffer(1_000d);
    buffer.add(10d, 0L);
    // after one half-life the 10 has decayed to 5; adding 10 more yields 15
    assertEquals(15d, buffer.add(10d, 1_000L), 1.0e-6);
  }

  @Test
  void consumeIfAtLeastSubtractsThresholdWhenReached() {
    ConfidenceBuffer buffer = new ConfidenceBuffer(1_000d);
    buffer.add(10d, 0L);

    assertTrue(buffer.consumeIfAtLeast(8d, 0L), "10 >= 8 so the flag is released");
    assertEquals(2d, buffer.value(0L), 1.0e-6, "the threshold is carried off, remainder stays");
    assertFalse(buffer.consumeIfAtLeast(8d, 0L), "2 < 8 so no further release");
  }

  @Test
  void resetClearsAccumulatedConfidence() {
    ConfidenceBuffer buffer = new ConfidenceBuffer(1_000d);
    buffer.add(10d, 0L);
    buffer.reset();
    assertEquals(0d, buffer.value(5_000L), 1.0e-6);
  }

  @Test
  void rejectsNonPositiveHalfLife() {
    assertThrows(IllegalArgumentException.class, () -> new ConfidenceBuffer(0d));
    assertThrows(IllegalArgumentException.class, () -> new ConfidenceBuffer(-1d));
  }

  @Test
  void ledgerCountsDistinctCorroboratingHeuristics() {
    ConfidenceLedger ledger = new ConfidenceLedger();
    ledger.note(HeuristicsClassicType.ROTATION_SNAP, 1.0d);
    ledger.note(HeuristicsClassicType.ROTATION_EXACT, 1.0d);
    ledger.note(HeuristicsClassicType.ROTATION_SNAP, 1.0d); // same type again, not a new corroborator

    assertEquals(2, ledger.corroboratingHeuristics(ConfidenceLedger.DEFAULT_CORROBORATION_WINDOW_MILLIS),
      "two distinct heuristics flagged within the window");
    assertTrue(ledger.confidenceOf(HeuristicsClassicType.ROTATION_SNAP) > 0d);
    assertTrue(ledger.aggregateConfidence() > 0d);
  }
}
