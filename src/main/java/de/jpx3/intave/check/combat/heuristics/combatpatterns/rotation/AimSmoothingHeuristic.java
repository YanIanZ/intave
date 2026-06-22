package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.RollingStatistics;
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
 * Detects aim-assist that <i>smooths</i> (interpolates / "eases") its rotation onto the target.
 *
 * <p>This is a different fingerprint to {@link RotationConstantSpeedHeuristic}, which catches aim
 * that turns at a constant <i>speed</i>. A smoothing aimbot instead covers a fixed <i>fraction</i> of
 * the remaining angle every tick, so the turn <i>decelerates</i> and successive yaw steps form a
 * near-geometric sequence: {@code step[n] / step[n-1]} stays almost constant (the smoothing factor)
 * across the whole sweep. A human easing onto a target produces no such stable ratio — their
 * micro-corrections make the step-to-step ratio jitter.
 *
 * <p>While the player is actively fighting a moving target, each pair of consecutive genuine turning
 * ticks contributes a step ratio. Over a window of {@link #WINDOW} contiguous <i>decelerating</i>
 * steps, the {@linkplain RollingStatistics#coefficientOfVariation() coefficient of variation} of those
 * ratios is measured; a value below {@link #RATIO_CV_THRESHOLD}, with a mean ratio in the
 * easing band {@code [}{@value #MIN_MEAN_RATIO}{@code , }{@value #MAX_MEAN_RATIO}{@code ]}, means a
 * robotically constant smoothing factor. Any still tick or re-acceleration breaks the sweep and clears
 * the partial window, so only an uninterrupted easing curve is judged.
 *
 * <p>Evidence is gathered in a decaying {@link ConfidenceBuffer} and it flags with a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence}. Like the other
 * behavioural rotation tells it ships at violation level {@code 0} (see
 * {@code heuristics.classic.aim-smoothing}) so out of the box it only feeds cross-heuristic
 * corroboration and verbose output; raise it after confirming no false positives.
 */
public final class AimSmoothingHeuristic extends ClassicHeuristic<AimSmoothingHeuristic.SmoothingMeta> {
  /** Only ticks turning at least this fast (degrees) are sampled, so idle aim is ignored. */
  private static final float MIN_TURN_SPEED = 2.0f;
  /** Number of consecutive decelerating step ratios evaluated per window. */
  private static final int WINDOW = 8;
  /** Ratio coefficient of variation below which the smoothing factor is considered robotically constant. */
  private static final double RATIO_CV_THRESHOLD = 0.06d;
  /** Mean step ratio must sit in this "easing" band — clearly decelerating, but not collapsing to a stop. */
  private static final double MIN_MEAN_RATIO = 0.30d;
  private static final double MAX_MEAN_RATIO = 0.92d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated easing-window evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public AimSmoothingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.AIM_SMOOTHING, SmoothingMeta.class);
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

    if (entity == null
      || movementData.ticksPast(TELEPORT) < 20
      || !attackData.recentlyAttacked(1000)
      || !entity.moving(0.05)) {
      return;
    }

    float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
    SmoothingMeta heuristicMeta = metaOf(user);
    float previous = heuristicMeta.lastYawSpeed;
    heuristicMeta.lastYawSpeed = yawSpeed;

    // A still / non-turning tick breaks the easing sweep; only contiguous turning steps form ratios.
    if (yawSpeed < MIN_TURN_SPEED || previous < MIN_TURN_SPEED) {
      heuristicMeta.ratios.reset();
      return;
    }

    double ratio = yawSpeed / previous;
    // Smoothing eases *toward* the target → each step is smaller than the last (ratio < 1). A rising
    // ratio is acquisition/overshoot acceleration, not the ease curve this check fingerprints.
    if (ratio >= 1.0d) {
      heuristicMeta.ratios.reset();
      return;
    }
    heuristicMeta.ratios.accept(ratio);
    if (heuristicMeta.ratios.count() < WINDOW) {
      return;
    }

    double meanRatio = heuristicMeta.ratios.mean();
    double cv = heuristicMeta.ratios.coefficientOfVariation();
    heuristicMeta.ratios.reset();

    long now = System.currentTimeMillis();
    boolean easing = meanRatio >= MIN_MEAN_RATIO && meanRatio <= MAX_MEAN_RATIO;
    if (easing && cv < RATIO_CV_THRESHOLD) {
      heuristicMeta.evidence.add(1.0d, now);
      if (heuristicMeta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
        double confidence = MathHelper.minmax(0.3d, (RATIO_CV_THRESHOLD - cv) / RATIO_CV_THRESHOLD, 1.0d);
        flag(player, "robotic aim-smoothing (ratio " + MathHelper.formatDouble(meanRatio, 2)
          + ", cv " + MathHelper.formatDouble(cv * 100d, 2) + "%)", confidence);
      }
    } else {
      // not a constant-factor ease this window — let the accumulated evidence decay
      heuristicMeta.evidence.value(now);
    }
  }

  public static final class SmoothingMeta extends CheckCustomMetadata {
    private float lastYawSpeed;
    private final RollingStatistics ratios = new RollingStatistics();
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
