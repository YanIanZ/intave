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

package de.jpx3.intave.module.network;

import de.jpx3.intave.module.Modules;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;

public final class NetworkSyncTests extends IntegrationTests {
  public NetworkSyncTests() {
    super("network-sync");
  }

  @Test(testCode = "network-sync-module-loaded", severity = Severity.ERROR)
  public void testModuleIsLoaded() {
    NetworkSync network = Modules.networkOrNull();
    assertNotNull(network);
    if (network.active()) {
      assertTrue(network.status().contains("server="));
    } else {
      assertEquals("disabled", network.status());
    }
  }
}
