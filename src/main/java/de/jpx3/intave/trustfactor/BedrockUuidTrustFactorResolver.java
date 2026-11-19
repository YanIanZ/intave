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

package de.jpx3.intave.trustfactor;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import org.bukkit.entity.Player;
import java.util.function.Consumer;

/**
 * Bedrock players on a network whose Geyser and Floodgate run on the proxy only: the backend has no Floodgate
 * API, but the player still carries Floodgate's UUID (the most significant half is zero), which a Java account
 * cannot have. Their movement is translated from another game, so the Java checks do not judge them
 * (same decision as {@link GeyserTrustFactorResolver}).
 */
public final class BedrockUuidTrustFactorResolver implements TrustFactorResolver {
  private final TrustFactorResolver parent;

  public BedrockUuidTrustFactorResolver(TrustFactorResolver parent) {
    this.parent = parent;
  }

  @Override
  public void resolve(Player player, Consumer<TrustFactor> callback) {
    if (player.getUniqueId().getMostSignificantBits() == 0L) {
      callback.accept(TrustFactor.BYPASS);
    } else {
      parent.resolve(player, callback);
    }
  }
}
