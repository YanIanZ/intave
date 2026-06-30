package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Detects kill-aura that attacks while the player is still consuming an item (eating / drinking).
 *
 * <p>Consuming an item occupies the hand. In vanilla the very first attack interrupts the consume, so
 * a player physically cannot land attack after attack while continuously eating or drinking — the
 * consume would have ended after the first hit. A kill-aura that fires at nearby targets without
 * releasing the consume leaks exactly that impossible state: the hand stays active across several
 * entity attacks.
 *
 * <p>Each attack that lands while the hand is actively consuming food/drink feeds a decaying
 * {@link ConfidenceBuffer}. A single such hit — a legitimate attack that interrupts the eat on the
 * same tick — fades; a sustained run of attacks during one continuous consume, which manual play
 * cannot produce, crosses {@link #RELEASE_THRESHOLD} and flags, at a
 * {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double) graded confidence} that
 * scales with how long the consume has been held. Like the other behavioural tells it ships at
 * violation level {@code 0} (see {@code heuristics.classic.attack-while-consuming}) so out of the box
 * it only feeds cross-heuristic corroboration and verbose output; raise it after confirming no false
 * positives.
 */
public final class AttackWhileConsumingHeuristic extends ClassicHeuristic<AttackWhileConsumingHeuristic.ConsumeMeta> {
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated attack-while-consuming evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;
  /** Hand-active ticks at which the consume is clearly sustained (a full eat is ~32 ticks). */
  private static final double SUSTAINED_USE_TICKS = 16.0d;

  public AttackWhileConsumingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_WHILE_CONSUMING, ConsumeMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void onAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    InventoryMetadata inventory = user.meta().inventory();
    // Only an attack that landed while the hand is actively consuming food/drink is of interest;
    // bow draws / shields (hand active but not food) are deliberately left to their own concerns.
    if (!inventory.handActive() || !inventory.foodItem()) {
      return;
    }

    long now = System.currentTimeMillis();
    ConsumeMeta meta = metaOf(user);
    meta.evidence.add(1.0d, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.4d, inventory.handActiveTicks / SUSTAINED_USE_TICKS, 1.0d);
      flag(user.player(), "attacked while consuming an item (hand active "
        + inventory.handActiveTicks + "t)", confidence);
    }
  }

  public static final class ConsumeMeta extends CheckCustomMetadata {
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
