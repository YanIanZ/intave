package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

/**
 * Detects a pathfinding bot (e.g. Baritone) by the shape of its rotation stream <i>while it travels</i>.
 *
 * <p>Every other rotation heuristic in the engine is gated on active combat — it only samples while
 * the player is fighting a tracked target. A pathing bot, however, reveals itself most clearly when
 * it is <i>not</i> fighting: walking a route it locks the pitch onto one computed angle and steers
 * purely in yaw to follow the node chain. {@link de.jpx3.intave.check.movement.pathfinder.HeadingLock}
 * catches the <i>movement</i> side of that (yaw tracking the travel heading through turns); this catches
 * the complementary <i>rotation</i> side: over a window of travelling ticks the pitch is frozen and
 * carries only a couple of distinct steps while a large amount of yaw turning accumulates — the
 * {@link BaritonePathingTracker} fingerprint. A human sweeping that much yaw tilts their view, so the
 * conjunction is not something natural play reproduces.
 *
 * <p>Ported from the open-source MX project's {@code BaritoneCheck} and rebuilt on intave's
 * pure-core + decaying {@link ConfidenceBuffer} idiom: an isolated suspicious window fades, only a
 * sustained run releases a {@linkplain ClassicHeuristic#flag(Player, String, double) graded flag}.
 * On release it stamps {@code MovementMetadata#lastPathfinderHeadingLockMillis} so the combat-domain
 * {@link de.jpx3.intave.check.combat.heuristics.other.BaritoneHeuristic} corroborates a bot that also
 * fights. It ships at violation level {@code 0} (see {@code heuristics.classic.baritone-rotation}) so
 * out of the box it only feeds cross-heuristic corroboration and verbose output; raise it after tuning.
 */
public final class BaritonePathingRotationHeuristic extends ClassicHeuristic<BaritonePathingRotationHeuristic.BaritonePathingMeta> {
  /** Number of consecutive travelling ticks gathered before a window is evaluated. */
  private static final int WINDOW = 40;
  /** Pitch-delta spread (degrees) below which the pitch counts as robotically frozen. */
  private static final double MAX_PITCH_RANGE = 0.02d;
  /** Most distinct pitch steps a frozen-pitch window may contain. */
  private static final int MAX_DISTINCT_PITCH = 3;
  /** Total yaw turning (degrees) the window must accumulate for the tell to mean "steering a path". */
  private static final double MIN_YAW_TURN_SUM = 30.0d;
  /** Minimum horizontal speed for a tick to count as travelling (not standing or barely nudging). */
  private static final double MIN_MOVE_SPEED = 0.05d;
  /** Ticks past the last teleport before sampling, so warp/join rotations are ignored. */
  private static final int MIN_TICKS_SINCE_TELEPORT = 20;
  private static final double BUFFER_HALF_LIFE_MILLIS = 6_000d;
  /** Accumulated suspicious-window evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public BaritonePathingRotationHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.BARITONE_ROTATION, BaritonePathingMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {LOOK, POSITION_LOOK}
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();

    if (movementData.ticksPast(TELEPORT) < MIN_TICKS_SINCE_TELEPORT) {
      return;
    }
    // only sample while travelling — pathing-while-walking is exactly the gap combat-gated rotation
    // heuristics miss, and requiring motion also rules out idle frozen-aim windows
    if (Hypot.fast(movementData.motionX(), movementData.motionZ()) < MIN_MOVE_SPEED) {
      return;
    }

    double absYawDelta = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
    double pitchDelta = movementData.rotationPitch - movementData.lastRotationPitch;

    BaritonePathingMeta meta = metaOf(user);
    meta.tracker.accept(absYawDelta, pitchDelta);
    if (meta.tracker.count() < WINDOW) {
      return;
    }

    double pitchRange = meta.tracker.pitchRange();
    int distinctPitch = meta.tracker.distinctPitchDeltas();
    double yawTurnSum = meta.tracker.yawTurnSum();
    meta.tracker.reset();

    long now = System.currentTimeMillis();
    if (isMachinePathing(pitchRange, distinctPitch, yawTurnSum)) {
      meta.evidence.add(1.0d, now);
      if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
        // let the combat-domain Baritone tell corroborate this travelling-bot window
        movementData.lastPathfinderHeadingLockMillis = now;
        double confidence = MathHelper.minmax(0.3d, yawTurnSum / (2d * MIN_YAW_TURN_SUM), 1.0d);
        flag(player, "rotation stream of a pathing bot — pitch frozen ("
          + MathHelper.formatDouble(pitchRange, 3) + "° over " + distinctPitch + " steps) while turning "
          + MathHelper.formatDouble(yawTurnSum, 0) + "° in yaw", confidence);
      }
    } else {
      // a natural travelling window — let the accumulated evidence decay
      meta.evidence.value(now);
    }
  }

  /**
   * Pure tell: a window of travelling rotation whose pitch is frozen ({@code pitchRange} tiny over a
   * couple of {@code distinctPitchDeltas}) while a large amount of yaw turning ({@code yawTurnSum})
   * accumulated — the signature of a bot steering a path by yaw alone.
   */
  static boolean isMachinePathing(double pitchRange, int distinctPitchDeltas, double yawTurnSum) {
    return pitchRange < MAX_PITCH_RANGE
      && distinctPitchDeltas <= MAX_DISTINCT_PITCH
      && yawTurnSum >= MIN_YAW_TURN_SUM;
  }

  public static final class BaritonePathingMeta extends CheckCustomMetadata {
    private final BaritonePathingTracker tracker = new BaritonePathingTracker();
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
