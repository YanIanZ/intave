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

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AntiBypassEngineTest {
  @Test
  void singleHighConfidenceLayerFlags() {
    AntiBypassEngine<String> engine = new AntiBypassEngine<>(
      Collections.singletonList(layer("a", AntiBypassResult.violation("a", 0.90D, "evidence"))),
      AntiBypassPolicy.defaultPolicy()
    );

    AntiBypassDecision decision = engine.evaluate("context");

    assertEquals(AntiBypassDecision.Verdict.VIOLATION, decision.verdict());
    assertTrue(decision.shouldFlag());
    assertTrue(decision.evidenceSummary().contains("a=VIOLATION"));
  }

  @Test
  void twoCorroboratingSuspiciousLayersFlag() {
    AntiBypassEngine<String> engine = new AntiBypassEngine<>(
      Arrays.asList(
        layer("a", AntiBypassResult.suspicious("a", 0.65D, "a")),
        layer("b", AntiBypassResult.suspicious("b", 0.60D, "b"))
      ),
      AntiBypassPolicy.defaultPolicy()
    );

    AntiBypassDecision decision = engine.evaluate("context");

    assertEquals(AntiBypassDecision.Verdict.VIOLATION, decision.verdict());
    assertTrue(decision.shouldFlag());
  }

  @Test
  void oneSuspiciousLayerIsOnlySuspicious() {
    AntiBypassEngine<String> engine = new AntiBypassEngine<>(
      Collections.singletonList(layer("a", AntiBypassResult.suspicious("a", 0.65D, "a"))),
      AntiBypassPolicy.defaultPolicy()
    );

    AntiBypassDecision decision = engine.evaluate("context");

    assertEquals(AntiBypassDecision.Verdict.SUSPICIOUS, decision.verdict());
    assertFalse(decision.shouldFlag());
  }

  @Test
  void unknownLayerIsCoverageGapNotViolation() {
    AntiBypassEngine<String> engine = new AntiBypassEngine<>(
      Arrays.asList(
        layer("a", AntiBypassResult.unknown("a", "missing")),
        layer("b", AntiBypassResult.clean("b", "clean"))
      ),
      AntiBypassPolicy.defaultPolicy()
    );

    AntiBypassDecision decision = engine.evaluate("context");

    assertEquals(AntiBypassDecision.Verdict.OBSERVE, decision.verdict());
    assertEquals(Collections.singletonList("a"), decision.coverageGaps());
  }

  @Test
  void allCleanIsClean() {
    AntiBypassEngine<String> engine = new AntiBypassEngine<>(
      Collections.singletonList(layer("a", AntiBypassResult.clean("a", "clean"))),
      AntiBypassPolicy.defaultPolicy()
    );

    assertEquals(AntiBypassDecision.Verdict.CLEAN, engine.evaluate("context").verdict());
  }

  private static AntiBypassLayer<String> layer(
    String id, AntiBypassResult result
  ) {
    return new AntiBypassLayer<String>() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public AntiBypassResult evaluate(String context) {
        return result;
      }
    };
  }
}
