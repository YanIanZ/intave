package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.CombatItems;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Material;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Detects auto-attack that ignores the spear's cooldown.
 *
 * <p>The spear is a heavy weapon, and heavy weapons charge slowly in modern (1.9+) combat: a
 * full-power hit requires waiting out the attack cooldown. A legitimate player therefore paces spear
 * hits to that cooldown, whereas an auto-attack / aura fires on every opportunity and lands hits far
 * closer together than the weapon allows. While the held item is a spear, two attacks closer than
 * {@link #MIN_HEAVY_INTERVAL_MILLIS} apart are faster than full-power manual input produces; each
 * such hit feeds a decaying {@link ConfidenceBuffer}, so isolated bursts (e.g. lag-flushed packets)
 * fade and only a sustained, too-fast cadence flags, at a
 * {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double) graded confidence}
 * scaling with how fast the attacks came.
 *
 * <p>Because attack-pace expectations vary by server/weapon configuration, it ships at violation
 * level {@code 0} (see {@code heuristics.classic.spear-attack-speed}): out of the box it only
 * contributes to cross-heuristic corroboration and verbose output rather than escalating on its own.
 * Hard click-rate limits are handled separately by the click-speed and click-pattern checks. Raise it
 * after confirming the interval matches your spear's cooldown.
 */
public final class SpearAttackSpeedHeuristic extends ClassicHeuristic<SpearAttackSpeedHeuristic.SpearMeta> {
  /** Spear hits closer than this are too fast to be full-power manual attacks on a heavy weapon. */
  private static final long MIN_HEAVY_INTERVAL_MILLIS = 150L;
  private static final double BUFFER_HALF_LIFE_MILLIS = 6_000d;
  /** Accumulated too-fast-hit evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public SpearAttackSpeedHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.SPEAR_ATTACK_SPEED, SpearMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void onAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    Material held = user.meta().inventory().heldItemType();
    if (!CombatItems.isSpear(held)) {
      return;
    }

    long now = System.currentTimeMillis();
    SpearMeta meta = metaOf(user);
    long delta = now - meta.lastSpearAttackMillis;
    meta.lastSpearAttackMillis = now;
    if (delta <= 0 || delta >= MIN_HEAVY_INTERVAL_MILLIS) {
      return;
    }

    double closeness = MathHelper.minmax(0.0d,
      (MIN_HEAVY_INTERVAL_MILLIS - delta) / (double) MIN_HEAVY_INTERVAL_MILLIS, 1.0d);
    meta.evidence.add(1.0d + closeness, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.3d, closeness, 1.0d);
      flag(user.player(), "spear attacks faster than its cooldown allows (" + delta + "ms)", confidence);
    }
  }

  public static final class SpearMeta extends CheckCustomMetadata {
    private long lastSpearAttackMillis;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
