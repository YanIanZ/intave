package de.jpx3.intave.check.movement.pathfinder;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.RollingStatistics;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.FLYING;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

/**
 * Detects the heading-lock fingerprint of a pathfinding bot (Baritone) — a transmitted yaw welded to
 * the movement heading <i>through turns</i> over a sustained sprint.
 *
 * <p>Baritone runs with {@code antiCheatCompatibility} on by default, which forces the sent yaw to face
 * the direction it is walking so it never sprints sideways. The angular residual between the yaw and the
 * heading implied by the horizontal motion ({@link BaritoneMovementMath#headingResidual}) therefore
 * stays near zero. A human walking straight has a small residual too, which is why the discriminator is
 * the residual staying pinned <i>while the player is turning</i>: a real player over-/under-shoots when
 * they curve, a bot tracks the route exactly. So a window only flags when the cumulative yaw change
 * clears {@link #MIN_TOTAL_TURN_DEG} (the player genuinely curved) yet the mean residual and its spread
 * both stay within {@link #MAX_MEAN_RESIDUAL_DEG} / {@link #MAX_RESIDUAL_SD_DEG}.
 *
 * <p>Evidence accrues in a decaying {@link ConfidenceBuffer} so an isolated window fades, and every
 * robotic window stamps {@code MovementMetadata#lastPathfinderHeadingLockMillis} so the combat-domain
 * {@code BaritoneHeuristic} can fuse "bot-pathing while fighting" into the ledger. The check ships
 * notify/log-only (no kick) until tuned; its violation level still feeds the ghost-client cross-domain
 * breadth.
 */
public final class HeadingLock extends MetaCheckPart<Pathfinder, HeadingLock.HeadingLockMeta> {
  /** Movement packets of sprint-travel analysed per evaluation window (~2s). */
  private static final int WINDOW = 40;
  /** The yaw must change at least this much across the window — i.e. the player actually turned. */
  private static final double MIN_TOTAL_TURN_DEG = 45.0d;
  /** Mean absolute heading residual (deg) at/below which the yaw is welded to the travel direction. */
  private static final double MAX_MEAN_RESIDUAL_DEG = 3.0d;
  /** Residual standard deviation (deg) at/below which the lock holds steady (no human overshoot). */
  private static final double MAX_RESIDUAL_SD_DEG = 3.0d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 8_000d;
  /** Robotic windows of evidence required before a flag is released (~4s sustained). */
  private static final double RELEASE_THRESHOLD = 2.0d;
  private static final double BASE_VL = 4.0d;

  private final CheckViolationLevelDecrementer decrementer;

  public HeadingLock(Pathfinder parentCheck) {
    super(parentCheck, HeadingLockMeta.class);
    this.decrementer = parentCheck.decrementer();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK}
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    HeadingLockMeta meta = metaOf(user);

    boolean travelling = movementData.sprinting
      && movementData.onGround
      && !movementData.isInVehicle()
      && !movementData.elytraFlying
      && movementData.ticksPast(TELEPORT) > 20;
    if (!travelling) {
      meta.reset();
      return;
    }

    double residual = BaritoneMovementMath.headingResidual(
      movementData.rotationYaw, movementData.motionX(), movementData.motionZ());
    if (Double.isNaN(residual)) {
      // momentarily too slow to imply a heading — skip the sample but keep the running window
      return;
    }

    meta.totalTurn += Math.abs(BaritoneMovementMath.signedYawDelta(
      movementData.lastRotationYaw, movementData.rotationYaw));
    meta.residualStats.accept(Math.abs(residual));
    if (meta.residualStats.count() < WINDOW) {
      return;
    }

    double meanResidual = meta.residualStats.mean();
    double residualSpread = meta.residualStats.standardDeviation();
    double totalTurn = meta.totalTurn;
    meta.reset();

    long now = System.currentTimeMillis();
    boolean robotic = indicatesHeadingLock(totalTurn, meanResidual, residualSpread);
    if (!robotic) {
      meta.evidence.value(now);
      decrementer.decrement(user, 0.05d);
      return;
    }

    // a real bot-path window: let the combat heuristic see it, and accrue toward a flag
    movementData.lastPathfinderHeadingLockMillis = now;
    meta.evidence.add(1.0d, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.5d,
        1.0d - (meanResidual / MAX_MEAN_RESIDUAL_DEG) * 0.5d, 1.0d);
      Violation violation = Violation.builderFor(Pathfinder.class)
        .forPlayer(player)
        .withMessage("robotic heading-lock through turns (pathfinding bot)")
        .withDetails("turned " + MathHelper.formatDouble(totalTurn, 0)
          + "° keeping yaw within " + MathHelper.formatDouble(meanResidual, 2)
          + "° of travel heading (sd " + MathHelper.formatDouble(residualSpread, 2) + "°)")
        .withVL(BASE_VL * confidence)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
  }

  /**
   * Pure window verdict: the player genuinely turned (cumulative yaw change cleared
   * {@link #MIN_TOTAL_TURN_DEG}) yet the transmitted yaw stayed welded to the travel heading
   * (mean residual and its spread both within tolerance) — robotic heading-lock-through-turns.
   */
  static boolean indicatesHeadingLock(double totalTurn, double meanResidual, double residualSpread) {
    return totalTurn >= MIN_TOTAL_TURN_DEG
      && meanResidual <= MAX_MEAN_RESIDUAL_DEG
      && residualSpread <= MAX_RESIDUAL_SD_DEG;
  }

  public static final class HeadingLockMeta extends CheckCustomMetadata {
    private final RollingStatistics residualStats = new RollingStatistics();
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
    private double totalTurn;

    private void reset() {
      residualStats.reset();
      totalTurn = 0.0d;
    }
  }
}
