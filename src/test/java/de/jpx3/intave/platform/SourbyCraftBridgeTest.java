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

package de.jpx3.intave.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SourbyCraftBridgeTest {
  @Test
  void bridgeFailsClosedWithoutSourbyApi() {
    assertFalse(SourbyCraftBridge.isSourbyCraft());
    assertNull(SourbyCraftBridge.findScheduler());
    assertEquals("unknown", SourbyCraftBridge.buildNumber());
  }

  @Test
  void auroraIsRequestedByDefault() {
    String previous = System.getProperty("intave.sourbycraft.scheduler");
    try {
      System.clearProperty("intave.sourbycraft.scheduler");
      assertTrue(SourbyCraftBridge.schedulerRequested());
    } finally {
      restore(previous);
    }
  }

  @Test
  void foliaPropertyOptsOut() {
    String previous = System.getProperty("intave.sourbycraft.scheduler");
    try {
      System.setProperty("intave.sourbycraft.scheduler", "folia");
      assertFalse(SourbyCraftBridge.schedulerRequested());
      System.setProperty("intave.sourbycraft.scheduler", "false");
      assertFalse(SourbyCraftBridge.schedulerRequested());
    } finally {
      restore(previous);
    }
  }

  private void restore(String previous) {
    if (previous == null) {
      System.clearProperty("intave.sourbycraft.scheduler");
    } else {
      System.setProperty("intave.sourbycraft.scheduler", previous);
    }
  }
}
