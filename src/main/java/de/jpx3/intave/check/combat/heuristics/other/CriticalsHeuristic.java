package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.SustainedStreakDetector;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Detects "packet" / spoofed critical-hit cheats (e.g. Wurst {@code Criticals} PACKET mode,
 * LiquidBounce's packet criticals).
 *
 * <p>Vanilla grants a critical hit only when the attacker is in a genuine downward arc — airborne,
 * falling, with real fall distance, not in water/on a ladder/riding. The cheat fakes that state at
 * attack time without ever leaving the ground: it streams a tiny rise-then-fall of position packets
 * that claim {@code onGround = false} so the server's crit test passes, while the player never
 * actually moves. Intave already derives the <i>true</i> ground state from collision rather than
 * trusting the client (see {@link MovementMetadata#onGround()}), so the spoof is visible as a
 * contradiction: the client claims to be airborne ({@code !lastClaimedOnGround}) while collision says
 * the player is firmly on the ground, with no genuine vertical motion or accumulated fall to justify
 * the claim.
 *
 * <p>The {@code MovementDispatcher} already flags the <i>opposite</i> contradiction (claiming ground
 * while really falling — the NoFall case); this closes the inverse that powers packet criticals. A
 * single attack can race a step-up or a laggy tick, so each spoofed-state attack feeds a decaying
 * {@link de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer} via {@link SustainedStreakDetector}
 * — only a sustained run flags, at a {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double)
 * graded confidence}. On a confirmed run it also requests an {@link AttackNerfStrategy#CRITICALS}
 * nerf so the illegitimate crit damage is stripped. It ships at violation level {@code 0} (see
 * {@code heuristics.classic.criticals}) so out of the box it observes/corroborates and nerfs; raise
 * the action after tuning.
 */
public final class CriticalsHeuristic extends ClassicHeuristic<CriticalsHeuristic.CriticalsMeta> {
  /** A genuine fall arc carries vertical motion; below this the player is not really moving up/down. */
  private static final double MAX_MOTION_Y = 0.02d;
  /** A genuine crit needs accumulated fall distance; below this no real fall preceded the attack. */
  private static final double MAX_FALL_DISTANCE = 0.1d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated spoofed-crit evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;
  /** Spoofed-crit attacks closer together than this continue a streak (otherwise it restarts). */
  private static final long STREAK_GAP_MILLIS = 1_500L;
  /** Streak length at which the run is unambiguously automated packet criticals. */
  private static final double SUSTAINED_STREAK = 6.0d;

  public CriticalsHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.CRITICALS, CriticalsMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void onAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    MovementMetadata movementData = user.meta().movement();
    if (!isSpoofedCritState(movementData.lastClaimedOnGround, movementData.onGround(),
      movementData.motionY(), movementData.fallDistance())) {
      return;
    }

    CriticalsMeta meta = metaOf(user);
    double confidence = meta.detector.note(System.currentTimeMillis());
    if (confidence != SustainedStreakDetector.NO_FLAG) {
      // strip the illegitimate crit bonus and record the tell for corroboration
      user.nerf(AttackNerfStrategy.CRITICALS, "criticals:spoof");
      flag(user.player(), "claimed airborne while grounded to force a critical hit (streak "
        + meta.detector.streak() + ") — packet criticals", confidence);
    }
  }

  /**
   * Pure tell: the client claims to be airborne ({@code !claimedOnGround}) while collision says it is
   * genuinely on the ground, with no real vertical motion or accumulated fall to back the claim — the
   * fall state a packet-criticals cheat fabricates without ever leaving the ground.
   */
  static boolean isSpoofedCritState(boolean claimedOnGround, boolean collisionOnGround,
                                    double motionY, double fallDistance) {
    return !claimedOnGround
      && collisionOnGround
      && Math.abs(motionY) < MAX_MOTION_Y
      && fallDistance < MAX_FALL_DISTANCE;
  }

  public static final class CriticalsMeta extends CheckCustomMetadata {
    private final SustainedStreakDetector detector =
      new SustainedStreakDetector(BUFFER_HALF_LIFE_MILLIS, RELEASE_THRESHOLD, STREAK_GAP_MILLIS, SUSTAINED_STREAK, 0.4d);
  }
}
