package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Detects anchor-/bed-aura — automated respawn-anchor / bed explosive combat (the Nether and End
 * equivalent of crystal-aura), which detonates its explosive faster than a human can place and re-aim.
 *
 * <p>Unlike a crystal (an entity), a respawn anchor or bed is a <i>block</i>: you place it, then
 * right-click it to set it off — in the End or Nether for a bed, in the Overworld or End for a charged
 * anchor (the only dimensions where they explode). An aura runs that place-then-detonate cycle in a
 * tick or two; a human needs reaction time to place the block, re-aim and click. The time between a
 * bed/anchor placement and a subsequent detonating interaction is measured; when it is below
 * {@link #MAX_CYCLE_MILLIS} the cycle is faster than manual input produces.
 *
 * <p>Interactions are only counted in the dimension where the block actually explodes, so setting a
 * spawn-point (anchor in the Nether, sleeping in the Overworld) is never considered. As with
 * crystal-aura, skilled players legitimately place-and-pop fast, so a single fast cycle is not
 * conclusive: evidence accumulates in a decaying {@link ConfidenceBuffer} and only a <i>sustained</i>
 * super-human cadence flags, at a {@linkplain ClassicHeuristic#flag(Player, String, double) graded
 * confidence}. It ships at violation level {@code 0} (see {@code heuristics.classic.anchor-bed-aura})
 * so out of the box it only feeds cross-heuristic corroboration and verbose output; raise it after
 * confirming no false positives on your Nether-PvP players.
 */
public final class AnchorBedAuraHeuristic extends ClassicHeuristic<AnchorBedAuraHeuristic.AuraMeta> {
  /** A place-then-detonate cycle quicker than this is faster than a human can place and re-aim. */
  private static final long MAX_CYCLE_MILLIS = 160L;
  private static final double BUFFER_HALF_LIFE_MILLIS = 8_000d;
  /** Accumulated super-human cycles required before a flag is released (a sustained cadence). */
  private static final double RELEASE_THRESHOLD = 4.0d;

  public AnchorBedAuraHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ANCHOR_BED_AURA, AuraMeta.class);
  }

  @BukkitEventSubscription
  public void onPlace(BlockPlaceEvent event) {
    if (!isExplosiveBlock(event.getBlockPlaced().getType())) {
      return;
    }
    metaOf(userOf(event.getPlayer())).lastPlaceMillis = System.currentTimeMillis();
  }

  @BukkitEventSubscription
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    Block clicked = event.getClickedBlock();
    Player player = event.getPlayer();
    if (clicked == null || !detonatesIn(clicked.getType(), player.getWorld().getEnvironment())) {
      return;
    }

    AuraMeta meta = metaOf(userOf(player));
    long now = System.currentTimeMillis();
    long delta = now - meta.lastPlaceMillis;
    if (delta <= 0 || delta > MAX_CYCLE_MILLIS) {
      return;
    }

    double closeness = MathHelper.minmax(0.0d, (MAX_CYCLE_MILLIS - delta) / (double) MAX_CYCLE_MILLIS, 1.0d);
    meta.evidence.add(1.0d + closeness, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.3d, closeness, 1.0d);
      flag(player, "explosive (bed/anchor) cycle " + delta + "ms after placing (super-human aura)", confidence);
    }
  }

  private static boolean isExplosiveBlock(Material material) {
    String name = material.name();
    return name.equals("RESPAWN_ANCHOR") || isBed(name);
  }

  private static boolean isBed(String name) {
    return name.endsWith("_BED") || name.equals("BED") || name.equals("BED_BLOCK");
  }

  /** Whether right-clicking this block detonates it in the given dimension (vs. setting a spawn-point). */
  private static boolean detonatesIn(Material material, World.Environment environment) {
    String name = material.name();
    if (name.equals("RESPAWN_ANCHOR")) {
      return environment == World.Environment.NORMAL || environment == World.Environment.THE_END;
    }
    if (isBed(name)) {
      return environment == World.Environment.NETHER || environment == World.Environment.THE_END;
    }
    return false;
  }

  public static final class AuraMeta extends CheckCustomMetadata {
    private long lastPlaceMillis;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
