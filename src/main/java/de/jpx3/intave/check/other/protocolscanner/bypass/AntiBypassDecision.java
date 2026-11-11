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

public final class AntiBypassDecision {
  enum Verdict {
    CLEAN,
    OBSERVE,
    SUSPICIOUS,
    VIOLATION
  }

  private final Verdict verdict;
  private final double confidence;
  private final List<AntiBypassResult> results;
  private final List<String> coverageGaps;

  AntiBypassDecision(
    Verdict verdict, double confidence,
    List<AntiBypassResult> results, List<String> coverageGaps
  ) {
    this.verdict = verdict;
    this.confidence = confidence;
    this.results = Collections.unmodifiableList(new ArrayList<>(results));
    this.coverageGaps = Collections.unmodifiableList(new ArrayList<>(coverageGaps));
  }

  Verdict verdict() {
    return verdict;
  }

  double confidence() {
    return confidence;
  }

  List<AntiBypassResult> results() {
    return results;
  }

  List<String> coverageGaps() {
    return coverageGaps;
  }

  public boolean shouldFlag() {
    return verdict == Verdict.VIOLATION;
  }

  public String evidenceSummary() {
    StringBuilder builder = new StringBuilder();
    for (AntiBypassResult result : results) {
      if (result.signalsMisbehaviour()) {
        if (builder.length() > 0) {
          builder.append("; ");
        }
        builder.append(result.layerId())
          .append('=')
          .append(result.status())
          .append('(')
          .append(Math.round(result.confidence() * 100.0D))
          .append("%)");
      }
    }
    return builder.toString();
  }
}
