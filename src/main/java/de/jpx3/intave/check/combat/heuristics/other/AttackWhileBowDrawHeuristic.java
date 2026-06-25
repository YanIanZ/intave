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
import org.bukkit.Material;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Detects kill-aura / bow-aura that lands melee attacks while a bow or crossbow is being drawn.
 *
 * <p>Drawing a ranged weapon occupies the hand: a left-click while a bow is drawn releases the shot
 * rather than meleeing, so a player physically cannot land melee hit after melee hit while a single
 * continuous draw is held — the draw would have ended at the first click. A kill-aura that swings at
 * nearby targets while the player is charging a bow (a common bow-aura + melee combo) leaks exactly
 * that impossible state: the hand stays in an active bow draw across several entity attacks.
 *
 * <p>Each entity attack that lands while the hand is actively drawing a bow/crossbow feeds a decaying
 * {@link ConfidenceBuffer}. A single hit — a legitimate click that ends the draw the same tick —
 * fades; a sustained run during one continuous draw, which manual play cannot produce, crosses
 * {@link #RELEASE_THRESHOLD} and flags, at a
 * {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double) graded confidence} that
 * scales with how long the draw has been held. Like the other behavioural tells it ships at violation
 * level {@code 0} (see {@code heuristics.classic.attack-while-bow-draw}) so out of the box it only
 * feeds cross-heuristic corroboration and verbose output; raise it after confirming no false positives.
 */
public final class AttackWhileBowDrawHeuristic extends ClassicHeuristic<AttackWhileBowDrawHeuristic.BowDrawMeta> {
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated attack-while-drawing evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;
  /** Hand-active ticks at which the draw is clearly sustained (a full bow draw is ~20 ticks). */
  private static final double SUSTAINED_DRAW_TICKS = 10.0d;

  public AttackWhileBowDrawHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_WHILE_BOW_DRAW, BowDrawMeta.class);
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
    if (!inventory.handActive() || !isRangedDraw(inventory.activeItemType())) {
      return; // only a melee attack landed during an active bow/crossbow draw is of interest
    }

    long now = System.currentTimeMillis();
    BowDrawMeta meta = metaOf(user);
    meta.evidence.add(1.0d, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.4d, inventory.handActiveTicks / SUSTAINED_DRAW_TICKS, 1.0d);
      flag(user.player(), "melee attack while drawing a bow (hand active "
        + inventory.handActiveTicks + "t)", confidence);
    }
  }

  private static boolean isRangedDraw(Material activeItem) {
    return activeItem != null && activeItem.name().contains("BOW"); // BOW + CROSSBOW
  }

  public static final class BowDrawMeta extends CheckCustomMetadata {
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
