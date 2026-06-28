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
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.CLOSE_WINDOW;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Detects kill-aura / inventory-aura that hides itself by closing the container GUI immediately before
 * each attack — LiquidBounce's {@code simulateInventoryClosing} is the canonical example.
 *
 * <p>{@link AttackWhileInventoryOpenHeuristic} catches an aura that attacks <i>through</i> an open
 * inventory. A smarter client dodges that by sending a {@code CLOSE_WINDOW} packet a tick before every
 * attack (and reopening the screen client-side afterwards), so the server believes no container is open
 * at attack time. The tell it cannot hide is the timing: a deliberate human close (pressing E) and then
 * aiming and clicking an entity takes far longer than a tick, whereas the bot's close lands within
 * {@link #CLOSE_TO_ATTACK_MILLIS} of the attack, every time.
 *
 * <p>A single close-then-attack can legitimately race the packet boundary, so each one feeds a decaying
 * {@link ConfidenceBuffer}: an isolated edge fades and only a sustained run of attacks tightly trailing a
 * window-close crosses {@link #RELEASE_THRESHOLD} and flags, at a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence} that scales with the
 * streak. It is a hard invariant when sustained, so it ships enforced (see
 * {@code heuristics.classic.inventory-close-attack}).
 */
public final class InventoryCloseAttackHeuristic extends ClassicHeuristic<InventoryCloseAttackHeuristic.CloseAttackMeta> {
  /** An attack landing within this long after a CLOSE_WINDOW is super-humanly close to the close. */
  private static final long CLOSE_TO_ATTACK_MILLIS = 75L;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated close-then-attack evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;
  /** Close-then-attack events closer together than this continue a streak (otherwise it restarts). */
  private static final long STREAK_GAP_MILLIS = 1_500L;
  /** Streak length at which the run is unambiguously sustained automation. */
  private static final double SUSTAINED_STREAK = 6.0d;

  public InventoryCloseAttackHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.INVENTORY_CLOSE_ATTACK, CloseAttackMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {CLOSE_WINDOW}
  )
  public void onClose(User user) {
    AbilityMetadata abilityData = user.meta().abilities();
    ProtocolMetadata clientData = user.meta().protocol();
    // keep the close→attack timing reliable: only on clients whose packet stream we fully observe and
    // while we are not deliberately ignoring movement (item-use / teleport bundles)
    if (abilityData.ignoringMovementPackets() || !clientData.flyingPacketsAreSent()) {
      return;
    }
    metaOf(user).lastCloseMillis = System.currentTimeMillis();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void onAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    CloseAttackMeta meta = metaOf(user);
    long now = System.currentTimeMillis();
    long sinceClose = now - meta.lastCloseMillis;
    if (meta.lastCloseMillis == 0L || sinceClose > CLOSE_TO_ATTACK_MILLIS) {
      return;
    }
    // consume this close so the same window-close cannot arm two attacks
    meta.lastCloseMillis = 0L;

    meta.streak = now - meta.lastEventMillis < STREAK_GAP_MILLIS ? meta.streak + 1 : 1;
    meta.lastEventMillis = now;
    meta.evidence.add(1.0d, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.4d, meta.streak / SUSTAINED_STREAK, 1.0d);
      flag(user.player(), "attacked " + sinceClose + "ms after closing a container (streak "
        + meta.streak + ") — simulated inventory close", confidence);
    }
  }

  public static final class CloseAttackMeta extends CheckCustomMetadata {
    private long lastCloseMillis;
    private long lastEventMillis;
    private int streak;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
