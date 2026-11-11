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

final class TeleportCombatLayer implements AntiBypassLayer<TeleportBypassContext> {
  @Override
  public String id() {
    return "combat_before_adoption";
  }

  @Override
  public AntiBypassResult evaluate(TeleportBypassContext context) {
    if (!context.valid()) {
      return AntiBypassResult.unknown(id(), "no teleport combat context");
    }
    if (context.attacksBeforeAdoption() >= 3) {
      return AntiBypassResult.suspicious(
        id(), 0.80D, "multiple attacks before adopting the server view"
      );
    }
    if (context.attacksBeforeAdoption() >= 1) {
      return AntiBypassResult.suspicious(
        id(), 0.65D, "attack before adopting the server view"
      );
    }
    return AntiBypassResult.clean(id(), "no attack before adoption");
  }
}
