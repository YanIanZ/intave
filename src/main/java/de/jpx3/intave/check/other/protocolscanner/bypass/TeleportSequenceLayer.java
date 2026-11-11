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

final class TeleportSequenceLayer implements AntiBypassLayer<TeleportBypassContext> {
  @Override
  public String id() {
    return "confirm_revert_sequence";
  }

  @Override
  public AntiBypassResult evaluate(TeleportBypassContext context) {
    if (!context.valid()) {
      return AntiBypassResult.unknown(id(), "no teleport packet sequence");
    }
    if (!context.confirmHadServerRotation()) {
      return AntiBypassResult.unknown(id(), "confirm packet did not carry the server view");
    }
    if (context.nextReverted()) {
      return AntiBypassResult.violation(
        id(), 0.90D, "confirm carried the server view, next packet reverted"
      );
    }
    return AntiBypassResult.clean(id(), "confirm sequence stayed on the server view");
  }
}
