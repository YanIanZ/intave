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

final class TeleportAdoptionLayer implements AntiBypassLayer<TeleportBypassContext> {
  private static final float MIN_SERVER_DELTA = 30.0f;
  private static final float MAX_CLIENT_DELTA = 0.05f;

  @Override
  public String id() {
    return "rotation_adoption";
  }

  @Override
  public AntiBypassResult evaluate(TeleportBypassContext context) {
    if (!context.valid()) {
      return AntiBypassResult.unknown(id(), "no teleport rotation evidence");
    }
    if (context.adopted() && context.nextReverted()) {
      return AntiBypassResult.violation(
        id(), 0.95D, "server rotation was adopted and then reverted"
      );
    }
    if (context.serverDelta() < MIN_SERVER_DELTA) {
      return AntiBypassResult.clean(id(), "teleport did not demand a large turn");
    }
    if (context.clientDelta() <= MAX_CLIENT_DELTA) {
      return AntiBypassResult.suspicious(
        id(), 0.80D, "client kept its pre-teleport view"
      );
    }
    if (context.adopted()) {
      return AntiBypassResult.clean(id(), "client adopted the server rotation");
    }
    return AntiBypassResult.unknown(id(), "no adoption or exact revert observed");
  }
}
