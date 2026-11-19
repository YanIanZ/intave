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

package de.jpx3.intave.check.bedrock;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Who is a Bedrock player. Geyser and Floodgate may run on the proxy only, so the Floodgate API is not asked:
 * Floodgate gives every Bedrock account a UUID whose most significant half is zero, and a Java account cannot have
 * one (online UUIDs are version 4, offline ones version 3).
 */
public final class BedrockPlayers {
  private BedrockPlayers() {
  }

  public static boolean is(Player player) {
    return player != null && is(player.getUniqueId());
  }

  public static boolean is(UUID id) {
    return id != null && id.getMostSignificantBits() == 0L;
  }
}
