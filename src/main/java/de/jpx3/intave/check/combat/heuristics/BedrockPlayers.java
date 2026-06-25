package de.jpx3.intave.check.combat.heuristics;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects players who joined from Bedrock Edition through Geyser/Floodgate.
 *
 * <p>Bedrock clients use a fundamentally different input model — touch and controller schemes with
 * built-in aim assist — and their actions reach a Java server only after Geyser translates them.
 * Java-style aim/rotation/packet-cadence analysis therefore does not apply to them, and running the
 * classic heuristics against that translated input produces false positives. The heuristic engine
 * consults this helper to exempt Bedrock players from those checks (see {@code ClassicHeuristic}),
 * complementing the trust-factor bypass that Floodgate already grants them.
 *
 * <p>Detection is done <b>reflectively</b> against the Floodgate API so this class carries no
 * compile- or load-time dependency on Floodgate: when Floodgate is not installed (or the lookup
 * fails for any reason) it simply reports {@code false}. Bedrock cannot be reliably identified
 * without Floodgate, so a Geyser-only setup without Floodgate will treat such players as Java.
 */
public final class BedrockPlayers {
  private static final Object FLOODGATE_API;
  private static final Method IS_FLOODGATE_PLAYER;

  static {
    Object api = null;
    Method isFloodgatePlayer = null;
    try {
      Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
      api = apiClass.getMethod("getInstance").invoke(null);
      isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
    } catch (Throwable ignored) {
      api = null;
      isFloodgatePlayer = null;
    }
    FLOODGATE_API = api;
    IS_FLOODGATE_PLAYER = isFloodgatePlayer;
  }

  private BedrockPlayers() {
  }

  /** @return whether Floodgate is installed and exposing its API. */
  public static boolean available() {
    return FLOODGATE_API != null && IS_FLOODGATE_PLAYER != null;
  }

  /**
   * @return {@code true} if the player connected from Bedrock via Geyser/Floodgate. Always
   * {@code false} when Floodgate is unavailable or the lookup fails.
   */
  public static boolean isBedrock(Player player) {
    if (player == null || !available()) {
      return false;
    }
    try {
      Object result = IS_FLOODGATE_PLAYER.invoke(FLOODGATE_API, player.getUniqueId());
      return result instanceof Boolean && (Boolean) result;
    } catch (Throwable ignored) {
      return false;
    }
  }
}
