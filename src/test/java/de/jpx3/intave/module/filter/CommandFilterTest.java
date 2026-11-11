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

package de.jpx3.intave.module.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandFilterTest {
  @Test
  void recognisesPluginListCommandsWithArgumentsAndNamespaces() {
    assertTrue(CommandFilter.isPluginListCommand("/plugins"));
    assertTrue(CommandFilter.isPluginListCommand("/plugins all"));
    assertTrue(CommandFilter.isPluginListCommand("/pl"));
    assertTrue(CommandFilter.isPluginListCommand("/pl Wureta"));
    assertTrue(CommandFilter.isPluginListCommand("/bukkit:plugins"));
    assertTrue(CommandFilter.isPluginListCommand("/paper:plugins"));
  }

  @Test
  void ignoresCommandsThatOnlyStartWithAPluginListPrefix() {
    assertFalse(CommandFilter.isPluginListCommand("/pluginsomething"));
    assertFalse(CommandFilter.isPluginListCommand("/plsomething"));
    assertFalse(CommandFilter.isPluginListCommand("/plugin"));
    assertFalse(CommandFilter.isPluginListCommand("/help"));
    assertFalse(CommandFilter.isPluginListCommand("/wureta diagnostics environment"));
  }

  @Test
  void recognisesManagedCommandTokens() {
    assertTrue(CommandFilter.isManagedCommand("/wureta"));
    assertTrue(CommandFilter.isManagedCommand("/wureta verbose Player"));
    assertTrue(CommandFilter.isManagedCommand("/wcore diagnostics"));
    assertTrue(CommandFilter.isManagedCommand("/intave"));
    assertTrue(CommandFilter.isManagedCommand("/iac"));
  }

  @Test
  void leavesSimilarCommandsAlone() {
    assertFalse(CommandFilter.isManagedCommand("/wuretacore"));
    assertFalse(CommandFilter.isManagedCommand("/intavex"));
    assertFalse(CommandFilter.isManagedCommand("/iacx"));
    assertFalse(CommandFilter.isManagedCommand("/wuretacommandforward"));
  }
}
