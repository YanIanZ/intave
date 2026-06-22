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
 * Detects "linear" aim-assist that turns at a robotically <i>constant angular velocity</i>.
 *
 * <p>The existing rotation heuristics catch aim that is too <i>accurate</i>, too <i>snappy</i>, or
 * lands <i>exactly</i> on the target. This one catches a different fingerprint: a human's turn speed
 * fluctuates tick-to-tick (acceleration, overshoot, micro-corrections), whereas a naive aimbot that
 * interpolates towards the target sweeps at a near-constant rate. While the player is actively
 * fighting a moving target, the per-tick yaw speed is sampled over a small window and its
 * <i>coefficient of variation</i> (std-dev / mean) is measured; a value below
 * {@link #CV_THRESHOLD} means the angular velocity barely varied — robotic.
 *
 * <p>It is deliberately conservative: only genuine turning ticks are sampled, evidence is gathered
 * in a decaying {@link ConfidenceBuffer} so a single uniform burst fades, and it flags with a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence} that scales with how
 * uniform the motion was. It ships at violation level {@code 0} (see
 * {@code heuristics.classic.rotation-constant-speed}) so that out of the box it only contributes to
 * cross-heuristic {@linkplain de.jpx3.intave.check.combat.heuristics.ConfidenceLedger corroboration}
 * and verbose output rather than escalating on its own; raise it after confirming no false positives.
 */
public final class RotationConstantSpeedHeuristic extends ClassicHeuristic<RotationConstantSpeedHeuristic.ConstantSpeedMeta> {
  /** Only ticks turning at least this fast (degrees) are sampled, so idle aim is ignored. */
  private static final float MIN_TURN_SPEED = 2.0f;
  /** Number of consecutive turning samples evaluated per window. */
  private static final int WINDOW = 8;
  /** Coefficient of variation below which the angular velocity is considered robotically uniform. */
  private static final double CV_THRESHOLD = 0.03d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated robotic-window evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public RotationConstantSpeedHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_CONSTANT_SPEED, ConstantSpeedMeta.class);
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
    if (yawSpeed < MIN_TURN_SPEED) {
      return;
    }

    ConstantSpeedMeta heuristicMeta = metaOf(user);
    heuristicMeta.yawSpeeds.accept(yawSpeed);
    if (heuristicMeta.yawSpeeds.count() < WINDOW) {
      return;
    }

    double cv = heuristicMeta.yawSpeeds.coefficientOfVariation();
    heuristicMeta.yawSpeeds.reset();

    long now = System.currentTimeMillis();
    if (cv < CV_THRESHOLD) {
      heuristicMeta.evidence.add(1.0d, now);
      if (heuristicMeta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
        double confidence = MathHelper.minmax(0.3d, (CV_THRESHOLD - cv) / CV_THRESHOLD, 1.0d);
        flag(player, "robotic constant aim velocity (cv " + MathHelper.formatDouble(cv * 100d, 2) + "%)", confidence);
      }
    } else {
      // not uniform this window — let the accumulated evidence decay
      heuristicMeta.evidence.value(now);
    }
  }

  public static final class ConstantSpeedMeta extends CheckCustomMetadata {
    private final RollingStatistics yawSpeeds = new RollingStatistics();
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
