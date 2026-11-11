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

import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.List;

public final class CommandFilterTests extends IntegrationTests {
  public CommandFilterTests() {
    super("command-filter");
  }

  @Test(testCode = "command-filter-hides-plugin-list", severity = Severity.ERROR)
  public void testPluginListIsFilteredForPlayers() {
    List<String> messages = new ArrayList<>();
    Player player = playerWithPermission(false, messages);

    PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/plugins");
    Bukkit.getPluginManager().callEvent(event);

    assertTrue(event.isCancelled());
    assertFalse(messages.isEmpty());
    String response = messages.get(messages.size() - 1);
    assertTrue(response.contains("Plugins:"));
    assertFalse(response.contains("WuretaCore"));
  }

  @Test(testCode = "command-filter-keeps-plugin-list-for-operators", severity = Severity.ERROR)
  public void testPluginListIsKeptForOperators() {
    List<String> messages = new ArrayList<>();
    Player player = playerWithPermission(true, messages);

    PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/plugins");
    Bukkit.getPluginManager().callEvent(event);

    assertFalse(event.isCancelled());
    assertTrue(messages.isEmpty());
  }

  @Test(testCode = "command-filter-ignores-unrelated-commands", severity = Severity.ERROR)
  public void testUnrelatedCommandsAreNotFiltered() {
    List<String> messages = new ArrayList<>();
    Player player = playerWithPermission(false, messages);

    PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/help");
    Bukkit.getPluginManager().callEvent(event);

    assertFalse(event.isCancelled());
    assertTrue(messages.isEmpty());
  }

  private static Player playerWithPermission(boolean permitted, List<String> messages) {
    return FakePlayerFactory.createPlayer((methodName, arguments) -> {
      if (methodName.equals("hasPermission")) {
        return permitted;
      }
      if (methodName.equals("sendMessage") && arguments.length == 1 && arguments[0] instanceof String) {
        messages.add((String) arguments[0]);
        return new Object();
      }
      return null;
    });
  }
}
