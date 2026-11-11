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

public final class AntiBypassPolicy {
  private final double violationConfidence;
  private final double suspiciousConfidence;
  private final int corroboratingLayers;

  public AntiBypassPolicy(
    double violationConfidence, double suspiciousConfidence, int corroboratingLayers
  ) {
    this.violationConfidence = violationConfidence;
    this.suspiciousConfidence = suspiciousConfidence;
    this.corroboratingLayers = Math.max(1, corroboratingLayers);
  }

  static AntiBypassPolicy defaultPolicy() {
    return new AntiBypassPolicy(0.85D, 0.60D, 2);
  }

  double violationConfidence() {
    return violationConfidence;
  }

  double suspiciousConfidence() {
    return suspiciousConfidence;
  }

  int corroboratingLayers() {
    return corroboratingLayers;
  }
}
