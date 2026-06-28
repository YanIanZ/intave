package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.SustainedStreakDetector;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

/**
 * Detects the "short-stop" anti-detection trick — aim-assist that injects brief, total rotation
 * pauses to fragment the windowed smoothness tells (LiquidBounce ships it as
 * {@code ShortStopRotationProcessor}).
 *
 * <p>The other rotation heuristics measure how a <i>turn</i> is shaped (constant speed, ease ratio,
 * collinear path, autocorrelated jitter). A cheap way to disrupt them is to randomly freeze the
 * rotation for a tick or two mid-track, which breaks the contiguous windows those checks need. But
 * that freeze is itself unnatural: while the player is actively tracking a <b>moving</b> target, the
 * aim should keep adjusting — a human decelerates through a turn, they do not snap from an active
 * turn to a dead stop and back. This heuristic flags exactly that — a tick that was actively turning
 * ({@code ≥ }{@link #MIN_TURN_SPEED}{@code °}) followed by a tick frozen on <i>both</i> axes
 * ({@code < }{@link #FREEZE_EPSILON}{@code °}) while the target is still moving.
 *
 * <p>A single hold can be legitimate (the target briefly sat inside the cursor), so each injected
 * pause feeds a {@link SustainedStreakDetector}: only a sustained run of freeze-mid-track events
 * flags, at a {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence}. It turns
 * the short-stop's own evasion mechanism into a behavioural tell and adds breadth to the ledger so a
 * heavily-smoothed aim still reaches cross-heuristic corroboration. Like the other behavioural
 * rotation tells it ships at violation level {@code 0} (see {@code heuristics.classic.rotation-pause})
 * — observe/corroborate only until tuned.
 */
public final class RotationPauseHeuristic extends ClassicHeuristic<RotationPauseHeuristic.PauseMeta> {
  /** The previous tick must have been turning at least this fast (degrees) for a freeze to count. */
  private static final float MIN_TURN_SPEED = 2.0f;
  /** Both axes moving less than this (degrees) this tick is a total aim freeze. */
  private static final float FREEZE_EPSILON = 0.1f;
  /** The target must be moving at least this much (blocks/tick) for tracking to require turning. */
  private static final double MIN_TARGET_MOTION = 0.1d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated freeze-mid-track evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 4.0d;
  private static final long STREAK_GAP_MILLIS = 1_000L;
  private static final double SUSTAINED_STREAK = 8.0d;

  public RotationPauseHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_PAUSE, PauseMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {LOOK, POSITION_LOOK}
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AttackMetadata attackData = meta.attack();
    Entity entity = attackData.lastAttackedEntity();
    PauseMeta heuristicMeta = metaOf(user);

    if (entity == null
      || movementData.ticksPast(TELEPORT) < 20
      || !attackData.recentlyAttacked(1000)
      || !entity.moving(MIN_TARGET_MOTION)) {
      heuristicMeta.lastYawMagnitude = 0f;
      return;
    }

    float yawMagnitude = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
    float pitchMagnitude = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);
    float previousYawMagnitude = heuristicMeta.lastYawMagnitude;
    heuristicMeta.lastYawMagnitude = yawMagnitude;

    if (!isInjectedPause(previousYawMagnitude, yawMagnitude, pitchMagnitude)) {
      return;
    }
    double confidence = heuristicMeta.detector.note(System.currentTimeMillis());
    if (confidence != SustainedStreakDetector.NO_FLAG) {
      flag(player, "aim froze mid-track while the target kept moving (short-stop, streak "
        + heuristicMeta.detector.streak() + ")", confidence);
    }
  }

  /**
   * Pure tell: the previous tick was actively turning, this tick is frozen on both axes — an injected
   * rotation pause. (The caller has already established the target is moving, so the freeze is not the
   * target simply sitting still.)
   */
  static boolean isInjectedPause(float previousYawMagnitude, float yawMagnitude, float pitchMagnitude) {
    return previousYawMagnitude >= MIN_TURN_SPEED
      && yawMagnitude < FREEZE_EPSILON
      && pitchMagnitude < FREEZE_EPSILON;
  }

  public static final class PauseMeta extends CheckCustomMetadata {
    private float lastYawMagnitude;
    private final SustainedStreakDetector detector =
      new SustainedStreakDetector(BUFFER_HALF_LIFE_MILLIS, RELEASE_THRESHOLD, STREAK_GAP_MILLIS, SUSTAINED_STREAK, 0.3d);
  }
}
