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

final class TeleportHistoryLayer implements AntiBypassLayer<TeleportBypassContext> {
  @Override
  public String id() {
    return "refusal_history";
  }

  @Override
  public AntiBypassResult evaluate(TeleportBypassContext context) {
    if (!context.valid()) {
      return AntiBypassResult.unknown(id(), "no refusal history");
    }
    if (context.refusalsInWindow() >= 3) {
      return AntiBypassResult.violation(
        id(), 0.75D, "repeated teleport-view refusals in the window"
      );
    }
    if (context.refusalsInWindow() >= 2) {
      return AntiBypassResult.suspicious(
        id(), 0.60D, "second teleport-view refusal in the window"
      );
    }
    return AntiBypassResult.clean(id(), "first refusal in the window");
  }
}
