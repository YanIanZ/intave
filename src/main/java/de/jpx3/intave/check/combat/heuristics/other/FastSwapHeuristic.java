package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.CombatItems;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.HELD_ITEM_SLOT_IN;

/**
 * Detects auto-swap / weapon-combo macros that change the held weapon faster than a human can.
 *
 * <p>The modern (1.21+, and the 26.x PvP meta) combat revolves around hit-and-swap combos — e.g.
 * landing a mace/trident/spear burst and instantly swapping back to a primary weapon. The vanilla
 * client only changes the selected hot-bar slot once per client tick (one key-press / scroll step
 * is processed per tick), so two held-slot changes arriving inside a single tick during combat is a
 * cadence no legitimate input produces — the fingerprint of a swap macro firing packets directly.
 *
 * <p>Each in-combat held-slot change closer than {@link #ONE_TICK_MILLIS} to the previous one feeds
 * a decaying {@link ConfidenceBuffer} (weighted higher when a {@linkplain CombatItems#isHeavyHitter
 * heavy-hitter} weapon is involved), so isolated coincidences from lag fade and only a sustained,
 * super-human swap cadence flags — at a {@linkplain ClassicHeuristic#flag(Player, String, double)
 * graded confidence} that scales with how fast the swap was.
 *
 * <p>Because top-tier players legitimately swap very quickly, this ships at violation level {@code 0}
 * (see {@code heuristics.classic.fast-swap}): out of the box it only contributes to cross-heuristic
 * {@linkplain de.jpx3.intave.check.combat.heuristics.ConfidenceLedger corroboration} and verbose
 * output rather than escalating on its own. Raise it after confirming no false positives.
 */
public final class FastSwapHeuristic extends ClassicHeuristic<FastSwapHeuristic.FastSwapMeta> {
  /** Only consider swaps while the player is actively fighting. */
  private static final long IN_COMBAT_MILLIS = 1_500L;
  /** Two held-slot changes closer than one tick apart are faster than vanilla input allows. */
  private static final long ONE_TICK_MILLIS = 50L;
  private static final double BUFFER_HALF_LIFE_MILLIS = 6_000d;
  /** Accumulated evidence required before a flag is released (i.e. a sustained macro cadence). */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public FastSwapHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.FAST_SWAP, FastSwapMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {HELD_ITEM_SLOT_IN}
  )
  public void receiveHeldSlot(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    if (!attackData.recentlyAttacked(IN_COMBAT_MILLIS)) {
      return;
    }
    long now = System.currentTimeMillis();
    FastSwapMeta meta = metaOf(user);
    long delta = now - meta.lastSwapMillis;
    meta.lastSwapMillis = now;
    if (delta <= 0 || delta >= ONE_TICK_MILLIS) {
      return;
    }

    Material held = user.meta().inventory().heldItemType();
    double weight = CombatItems.isHeavyHitter(held) ? 1.6d
      : CombatItems.isCombatItem(held) ? 1.2d
      : 0.8d;
    meta.evidence.add(weight, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.3d, (ONE_TICK_MILLIS - delta) / (double) ONE_TICK_MILLIS, 1.0d);
      flag(player, "weapon swaps faster than one per tick (" + delta + "ms)", confidence);
    }
  }

  public static final class FastSwapMeta extends CheckCustomMetadata {
    private long lastSwapMillis;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
