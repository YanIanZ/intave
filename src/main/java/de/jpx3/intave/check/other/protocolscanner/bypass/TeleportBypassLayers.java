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

import java.util.Arrays;

public final class TeleportBypassLayers {
  private static final AntiBypassEngine<TeleportBypassContext> ENGINE = engine(
    AntiBypassPolicy.defaultPolicy()
  );

  private TeleportBypassLayers() {
  }

  public static AntiBypassEngine<TeleportBypassContext> engine() {
    return ENGINE;
  }

  public static AntiBypassEngine<TeleportBypassContext> engine(AntiBypassPolicy policy) {
    return new AntiBypassEngine<>(
      Arrays.asList(
        new TeleportAdoptionLayer(),
        new TeleportSequenceLayer(),
        new TeleportCombatLayer(),
        new TeleportHistoryLayer()
      ),
      policy
    );
  }
}
