package de.jpx3.intave.check.combat.heuristics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SustainedStreakDetector} — the shared "single edge is noise, a sustained run
 * is a cheat" core of {@code InventoryCloseAttackHeuristic}, {@code BaritoneHeuristic} and
 * {@code MultiActionHeuristic}. Timestamps are passed in, so the decay and streak logic are tested
 * deterministically without touching the wall clock.
 */
final class SustainedStreakDetectorTest {
  private static final double EPS = 1.0e-9d;

  /** release=3, gap=1000ms, sustained=6, minConfidence=0.4, large half-life (no decay in-window). */
  private static SustainedStreakDetector detector() {
    return new SustainedStreakDetector(1_000_000d, 3.0d, 1_000L, 6.0d, 0.4d);
  }

  @Test
  void doesNotFlagBelowReleaseThreshold() {
    SustainedStreakDetector d = detector();
    assertEquals(SustainedStreakDetector.NO_FLAG, d.note(0L), EPS);    // evidence 1
    assertEquals(SustainedStreakDetector.NO_FLAG, d.note(100L), EPS);  // evidence 2
  }

  @Test
  void flagsOnceSustainedRunCrossesRelease() {
    SustainedStreakDetector d = detector();
    // same-tick events so no decay erodes the buffer before it reaches the release threshold
    assertEquals(SustainedStreakDetector.NO_FLAG, d.note(0L), EPS); // evidence 1
    assertEquals(SustainedStreakDetector.NO_FLAG, d.note(0L), EPS); // evidence 2
    double confidence = d.note(0L);                                 // evidence 3 >= release 3 -> flag
    assertTrue(confidence > 0.0d, "third consecutive event should release a flag");
    assertEquals(3, d.streak());
    // streak 3 of sustained 6 -> 0.5 confidence
    assertEquals(0.5d, confidence, EPS);
  }

  @Test
  void confidenceScalesWithStreakAndClampsToOne() {
    // sustained=2 so a streak of 3 would map to 1.5 -> clamped to 1.0
    SustainedStreakDetector d = new SustainedStreakDetector(1_000_000d, 1.0d, 1_000L, 2.0d, 0.4d);
    d.note(0L);              // streak 1 -> 1/2 = 0.5
    d.note(100L);            // streak 2 -> 1.0
    double c = d.note(200L); // streak 3 -> 1.5 clamped
    assertEquals(1.0d, c, EPS);
  }

  @Test
  void confidenceFlooredAtMinConfidence() {
    // release 1 so the very first event flags at streak 1 -> 1/6 < 0.4 -> floored to 0.4
    SustainedStreakDetector d = new SustainedStreakDetector(1_000_000d, 1.0d, 1_000L, 6.0d, 0.4d);
    assertEquals(0.4d, d.note(0L), EPS);
  }

  @Test
  void gapResetsTheStreak() {
    SustainedStreakDetector d = detector();
    d.note(0L);
    d.note(100L);
    assertEquals(2, d.streak());
    d.note(5_000L); // 4900ms > 1000ms gap -> streak restarts
    assertEquals(1, d.streak());
  }

  @Test
  void decayPreventsFlaggingOnSparseEvents() {
    // half-life 1000ms; events 10s apart decay to ~0 before the next, never reaching release 3
    SustainedStreakDetector d = new SustainedStreakDetector(1_000d, 3.0d, 1_000_000L, 6.0d, 0.4d);
    for (long t = 0; t <= 100_000L; t += 10_000L) {
      assertEquals(SustainedStreakDetector.NO_FLAG, d.note(t), EPS,
        "sparse, decayed events must not accumulate to a flag at t=" + t);
    }
  }
}
