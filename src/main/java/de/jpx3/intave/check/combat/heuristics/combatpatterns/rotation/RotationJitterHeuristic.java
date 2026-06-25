package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.RollingCorrelation;
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
 * Detects aim-assist that injects <i>artificial</i> jitter to evade the smoothness tells.
 *
 * <p>The other rotation heuristics catch aim that is too <i>orderly</i> — constant speed, a constant
 * ease ratio, a collinear path, low entropy. A common countermeasure is to add random jitter so the
 * motion no longer looks robotically smooth. But injected jitter is statistically <i>white</i>: each
 * tick's noise is drawn independently, so consecutive rotation deltas are uncorrelated. Genuine human
 * aim is the opposite — motor control has momentum and tremor that is temporally <b>autocorrelated</b>,
 * so while a player is actively turning, this tick's delta predicts the next.
 *
 * <p>While the player is fighting a moving target, each pair of consecutive genuine turning ticks
 * contributes a (previous delta, current delta) sample to a {@link RollingCorrelation}, whose result
 * is the <b>lag-1 autocorrelation</b> of the signed yaw deltas. Over a window of {@link #WINDOW}
 * contiguous turning ticks, an autocorrelation below {@link #AUTOCORRELATION_THRESHOLD} — i.e. near
 * zero or negative (an oscillating {@code +,-,+,-} jitter) — means the motion lacks the temporal
 * structure of human aim.
 *
 * <p>A variance gate keeps the two concerns separate: the window is only judged once the deltas vary
 * by at least {@link #MIN_JITTER_STDDEV} degrees, i.e. there is actual jitter to assess. A near-constant
 * turn (zero-variance, where autocorrelation is undefined) is left to
 * {@link RotationConstantSpeedHeuristic}; a smooth accelerating turn keeps a high autocorrelation and is
 * not flagged. Any still / non-turning tick breaks the run and clears the partial window.
 *
 * <p>This is the complement of the smoothness tells: a cheat is caught whether its aim is too smooth
 * (those heuristics) <i>or</i> its added noise is too artificial (this one). Evidence accrues in a
 * decaying {@link ConfidenceBuffer} so a single window fades, and it flags with a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence}. Like the other
 * behavioural rotation tells it ships at violation level {@code 0} (see
 * {@code heuristics.classic.rotation-jitter}) so out of the box it only feeds cross-heuristic
 * corroboration and verbose output; raise it after confirming no false positives.
 */
public final class RotationJitterHeuristic extends ClassicHeuristic<RotationJitterHeuristic.JitterMeta> {
  /** Only ticks turning at least this fast (degrees) are sampled, so idle aim is ignored. */
  private static final float MIN_TURN_SPEED = 2.0f;
  /** Number of consecutive autocorrelation samples evaluated per window. */
  private static final int WINDOW = 10;
  /** Lag-1 autocorrelation below which the jitter is considered artificial (human aim sits well above). */
  private static final double AUTOCORRELATION_THRESHOLD = 0.12d;
  /** The deltas must vary at least this much (degrees) for there to be jitter worth judging. */
  private static final double MIN_JITTER_STDDEV = 1.5d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated artificial-jitter evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public RotationJitterHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_JITTER, JitterMeta.class);
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

    float delta = signedYawDelta(movementData.lastRotationYaw, movementData.rotationYaw);
    JitterMeta heuristicMeta = metaOf(user);
    float previousDelta = heuristicMeta.lastDelta;
    heuristicMeta.lastDelta = delta;

    // A still / non-turning tick breaks the run; only contiguous turning steps form delta pairs.
    if (Math.abs(delta) < MIN_TURN_SPEED || Math.abs(previousDelta) < MIN_TURN_SPEED) {
      heuristicMeta.reset();
      return;
    }

    heuristicMeta.autocorrelation.accept(previousDelta, delta);
    heuristicMeta.deltaSpread.accept(delta);
    if (heuristicMeta.autocorrelation.count() < WINDOW) {
      return;
    }

    double autocorrelation = heuristicMeta.autocorrelation.correlation();
    double spread = heuristicMeta.deltaSpread.standardDeviation();
    heuristicMeta.reset();

    long now = System.currentTimeMillis();
    // Only a window that actually contains jitter (non-trivial spread) and lacks human temporal
    // structure (low autocorrelation) counts; a near-constant turn is constant-speed's concern.
    if (spread >= MIN_JITTER_STDDEV && autocorrelation < AUTOCORRELATION_THRESHOLD) {
      heuristicMeta.evidence.add(1.0d, now);
      if (heuristicMeta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
        double confidence = MathHelper.minmax(0.3d,
          (AUTOCORRELATION_THRESHOLD - autocorrelation) / (AUTOCORRELATION_THRESHOLD + 1.0d), 1.0d);
        flag(player, "artificial aim jitter (lag-1 autocorrelation "
          + MathHelper.formatDouble(autocorrelation, 2) + ")", confidence);
      }
    } else {
      // temporally structured (human-like) or near-constant this window — let the evidence decay
      heuristicMeta.evidence.value(now);
    }
  }

  /** Signed shortest-arc yaw delta in degrees, wrapped to {@code [-180, 180)}. */
  private static float signedYawDelta(float from, float to) {
    float delta = (to - from) % 360f;
    if (delta >= 180f) {
      delta -= 360f;
    } else if (delta < -180f) {
      delta += 360f;
    }
    return delta;
  }

  public static final class JitterMeta extends CheckCustomMetadata {
    private float lastDelta;
    private final RollingCorrelation autocorrelation = new RollingCorrelation();
    private final RollingStatistics deltaSpread = new RollingStatistics();
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);

    private void reset() {
      autocorrelation.reset();
      deltaSpread.reset();
    }
  }
}
