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
 * Detects auto-attack that ignores the cooldown of the modern heavy weapons — the mace (1.21) and the
 * trident — which {@link SpearAttackSpeedHeuristic} (spears only) does not cover.
 *
 * <p>Like the spear, the mace and trident are slow, heavy weapons: a full-power hit requires waiting
 * out the attack cooldown, so a legitimate player paces hits to it while an aura fires on every
 * opportunity. While the held item is a mace or trident, two hits closer than
 * {@link #MIN_HEAVY_INTERVAL_MILLIS} apart are faster than deliberate manual input on such a weapon
 * produces; each feeds a decaying {@link ConfidenceBuffer}, so isolated bursts (e.g. lag-flushed
 * packets) fade and only a sustained, too-fast cadence flags, at a
 * {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double) graded confidence}
 * scaling with how fast the hits came.
 *
 * <p>The interval is intentionally conservative (it sits well above any deliberate heavy-weapon pace),
 * so it stays false-positive-free; raw click-rate limits are handled separately by the click-speed and
 * click-pattern checks. It ships at violation level {@code 0} (see
 * {@code heuristics.classic.heavy-attack-speed}) so out of the box it only contributes to
 * cross-heuristic corroboration and verbose output; raise it after confirming no false positives.
 */
public final class HeavyHitterAttackSpeedHeuristic extends ClassicHeuristic<HeavyHitterAttackSpeedHeuristic.HeavyMeta> {
  /** Mace/trident hits closer than this are too fast to be deliberate manual attacks on a heavy weapon. */
  private static final long MIN_HEAVY_INTERVAL_MILLIS = 150L;
  private static final double BUFFER_HALF_LIFE_MILLIS = 6_000d;
  /** Accumulated too-fast-hit evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public HeavyHitterAttackSpeedHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.HEAVY_ATTACK_SPEED, HeavyMeta.class);
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
    // Mace and trident only; spears keep their own (spear-attack-speed) heuristic.
    if (!CombatItems.isHeavyHitter(held) || CombatItems.isSpear(held)) {
      return;
    }

    long now = System.currentTimeMillis();
    HeavyMeta meta = metaOf(user);
    long delta = now - meta.lastAttackMillis;
    meta.lastAttackMillis = now;
    if (delta <= 0 || delta >= MIN_HEAVY_INTERVAL_MILLIS) {
      return;
    }

    double closeness = MathHelper.minmax(0.0d,
      (MIN_HEAVY_INTERVAL_MILLIS - delta) / (double) MIN_HEAVY_INTERVAL_MILLIS, 1.0d);
    meta.evidence.add(1.0d + closeness, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.3d, closeness, 1.0d);
      flag(user.player(), "heavy weapon (mace/trident) attacks faster than its cooldown allows ("
        + delta + "ms)", confidence);
    }
  }

  public static final class HeavyMeta extends CheckCustomMetadata {
    private long lastAttackMillis;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
