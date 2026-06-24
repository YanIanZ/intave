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
 * Detects kill-aura / inventory-aura that keeps attacking while a container GUI is open.
 *
 * <p>While the inventory or any container screen is open, vanilla routes the mouse to the GUI, not
 * the world — the player physically cannot swing at or attack an entity. So an attack packet arriving
 * while the server knows a container is open is the signature of an aura acting through the screen
 * (the classic "browsing a chest while still fighting" tell). This is distinct from
 * {@link PacketInventoryHeuristic}, which catches <i>look</i> packets sent in the inventory; this one
 * catches <i>attacks</i>.
 *
 * <p>Because a single attack can momentarily race an inventory open/close at the packet boundary, each
 * attack-while-open feeds a decaying {@link ConfidenceBuffer}: an isolated edge fades and only a
 * sustained run of attacks during an open GUI crosses {@link #RELEASE_THRESHOLD} and flags, at a
 * {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double) graded confidence} that
 * scales with the consecutive streak. Like the other behavioural tells it ships at violation level
 * {@code 0} (see {@code heuristics.classic.attack-while-inventory}) so out of the box it only feeds
 * cross-heuristic corroboration and verbose output; raise it after confirming no false positives (it
 * is a hard invariant and a natural candidate for the next enforcement tier).
 */
public final class AttackWhileInventoryOpenHeuristic extends ClassicHeuristic<AttackWhileInventoryOpenHeuristic.InventoryAttackMeta> {
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated attack-while-open evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;
  /** Attacks closer together than this continue a streak (otherwise it restarts). */
  private static final long STREAK_GAP_MILLIS = 1_000L;
  /** Streak length at which the run is unambiguously sustained automation. */
  private static final double SUSTAINED_STREAK = 6.0d;

  public AttackWhileInventoryOpenHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_WHILE_INVENTORY_OPEN, InventoryAttackMeta.class);
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
    if (!inventory.inventoryOpen()) {
      return;
    }

    long now = System.currentTimeMillis();
    InventoryAttackMeta meta = metaOf(user);
    meta.streak = now - meta.lastAttackMillis < STREAK_GAP_MILLIS ? meta.streak + 1 : 1;
    meta.lastAttackMillis = now;
    meta.evidence.add(1.0d, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.4d, meta.streak / SUSTAINED_STREAK, 1.0d);
      flag(user.player(), "attacked while a container GUI was open (streak " + meta.streak + ")", confidence);
    }
  }

  public static final class InventoryAttackMeta extends CheckCustomMetadata {
    private long lastAttackMillis;
    private int streak;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
