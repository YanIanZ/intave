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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AntiBypassEngine<C> {
  private final List<AntiBypassLayer<C>> layers;
  private final AntiBypassPolicy policy;

  AntiBypassEngine(List<AntiBypassLayer<C>> layers, AntiBypassPolicy policy) {
    this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
    this.policy = policy;
  }

  public AntiBypassDecision evaluate(C context) {
    List<AntiBypassResult> results = new ArrayList<>(layers.size());
    List<String> coverageGaps = new ArrayList<>();
    double highestViolation = 0.0D;
    double highestSuspicious = 0.0D;
    int corroborating = 0;
    boolean hasObservation = false;

    for (AntiBypassLayer<C> layer : layers) {
      AntiBypassResult result = layer.evaluate(context);
      results.add(result);
      switch (result.status()) {
        case VIOLATION:
          highestViolation = Math.max(highestViolation, result.confidence());
          if (result.confidence() >= policy.suspiciousConfidence()) {
            corroborating++;
          }
          break;
        case SUSPICIOUS:
          highestSuspicious = Math.max(highestSuspicious, result.confidence());
          if (result.confidence() >= policy.suspiciousConfidence()) {
            corroborating++;
          }
          break;
        case OBSERVE:
          hasObservation = true;
          break;
        case UNKNOWN:
          coverageGaps.add(result.layerId());
          break;
        default:
          break;
      }
    }

    highestSuspicious = Math.max(highestSuspicious, highestViolation);
    if (highestViolation >= policy.violationConfidence()) {
      return decision(
        AntiBypassDecision.Verdict.VIOLATION,
        highestViolation,
        results,
        coverageGaps
      );
    }
    if (corroborating >= policy.corroboratingLayers()
      && Math.max(highestViolation, highestSuspicious) >= policy.suspiciousConfidence()) {
      return decision(
        AntiBypassDecision.Verdict.VIOLATION,
        Math.max(highestViolation, highestSuspicious),
        results,
        coverageGaps
      );
    }
    if (highestSuspicious >= policy.suspiciousConfidence()) {
      return decision(
        AntiBypassDecision.Verdict.SUSPICIOUS,
        highestSuspicious,
        results,
        coverageGaps
      );
    }
    if (hasObservation || !coverageGaps.isEmpty()) {
      return decision(
        AntiBypassDecision.Verdict.OBSERVE,
        Math.max(highestViolation, highestSuspicious),
        results,
        coverageGaps
      );
    }
    return decision(AntiBypassDecision.Verdict.CLEAN, 0.0D, results, coverageGaps);
  }

  private AntiBypassDecision decision(
    AntiBypassDecision.Verdict verdict, double confidence,
    List<AntiBypassResult> results, List<String> coverageGaps
  ) {
    return new AntiBypassDecision(verdict, confidence, results, coverageGaps);
  }
}
