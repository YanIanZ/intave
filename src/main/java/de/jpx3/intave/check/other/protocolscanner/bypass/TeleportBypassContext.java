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

public final class TeleportBypassContext {
  private final boolean valid;
  private final float serverDelta;
  private final float clientDelta;
  private final boolean adopted;
  private final boolean nextReverted;
  private final boolean confirmHadServerRotation;
  private final int attacksBeforeAdoption;
  private final int refusalsInWindow;

  public TeleportBypassContext(
    boolean valid, float serverDelta, float clientDelta,
    boolean adopted, boolean nextReverted, boolean confirmHadServerRotation,
    int attacksBeforeAdoption, int refusalsInWindow
  ) {
    this.valid = valid;
    this.serverDelta = serverDelta;
    this.clientDelta = clientDelta;
    this.adopted = adopted;
    this.nextReverted = nextReverted;
    this.confirmHadServerRotation = confirmHadServerRotation;
    this.attacksBeforeAdoption = attacksBeforeAdoption;
    this.refusalsInWindow = refusalsInWindow;
  }

  boolean valid() {
    return valid;
  }

  float serverDelta() {
    return serverDelta;
  }

  float clientDelta() {
    return clientDelta;
  }

  boolean adopted() {
    return adopted;
  }

  boolean nextReverted() {
    return nextReverted;
  }

  boolean confirmHadServerRotation() {
    return confirmHadServerRotation;
  }

  int attacksBeforeAdoption() {
    return attacksBeforeAdoption;
  }

  int refusalsInWindow() {
    return refusalsInWindow;
  }
}
