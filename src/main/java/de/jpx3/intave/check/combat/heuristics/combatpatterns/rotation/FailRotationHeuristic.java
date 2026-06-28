package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
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
 * Detects aim-assist that fakes "human" misses to evade the accuracy / smoothness tells — the
 * countermeasure LiquidBounce ships as its {@code FailRotationProcessor}.
 *
 * <p>The accuracy and rotation heuristics flag aim that is too perfect (a tiny, robotically tight
 * residual to the target). A fail processor defeats them by riding on top of that perfect aim and, at a
 * low rate, injecting a deliberate miss: it shoves the rotation a few degrees off the target for one to
 * a few ticks and snaps straight back. That lowers the long-term accuracy and raises the apparent
 * jitter/entropy enough to slip the other tells — but it leaves its own fingerprint, which this
 * heuristic measures with {@link PulseTracker}: a baseline far tighter than human motor noise, broken by
 * discrete, bounded, self-reverting pulses whose magnitudes are tightly clustered (the processor draws
 * from a small strength range).
 *
 * <p>While the player tracks a moving target, each rotation packet's angular residual to the target's
 * closest hit-box yaw is fed to the tracker. Once a {@link #WINDOW}-tick window is complete, a tight
 * baseline ({@link #MIN_TINY_RATIO}) punctuated by at least {@link #MIN_PULSES} uniform revert pulses
 * ({@link #MAX_PEAK_CV}) is treated as artificial fail injection; the verdict accrues in a decaying
 * {@link ConfidenceBuffer} so a single window fades. It flags with a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence} and, like the other
 * behavioural rotation tells, ships at violation level {@code 0} (see
 * {@code heuristics.classic.fail-rotation}) so out of the box it only feeds cross-heuristic
 * corroboration and verbose output; raise it after confirming no false positives.
 */
public final class FailRotationHeuristic extends ClassicHeuristic<FailRotationHeuristic.FailRotationMeta> {
  /** Rotation packets of tracking analysed per evaluation window. */
  private static final int WINDOW = 40;
  /** The aim must sit on-target at least this fraction of the window — the tight aim-assist baseline. */
  private static final double MIN_TINY_RATIO = 0.6d;
  /** At least this many clean revert pulses must appear in the window to suspect injected fails. */
  private static final int MIN_PULSES = 3;
  /** Pulse-peak coefficient of variation below which the misses look processor-bounded (uniform). */
  private static final double MAX_PEAK_CV = 0.35d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 6_000d;
  /** Accumulated artificial-fail evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 2.0d;

  public FailRotationHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.FAIL_ROTATION, FailRotationMeta.class);
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
    FailRotationMeta heuristicMeta = metaOf(user);

    if (entity == null
      || movementData.ticksPast(TELEPORT) < 20
      || !attackData.recentlyAttacked(1000)
      || attackData.recentlySwitchedEntity(1000)
      || !entity.moving(0.05)) {
      // not in a clean tracking run — drop any partial window so unrelated samples never mix
      if (heuristicMeta.tracker.totalTicks() > 0) {
        heuristicMeta.tracker.reset();
      }
      return;
    }

    double residual = Math.abs(MathHelper.distanceInDegrees(movementData.rotationYaw, attackData.perfectClosestYaw()));
    heuristicMeta.tracker.accept(residual);
    if (heuristicMeta.tracker.totalTicks() < WINDOW) {
      return;
    }

    double tinyRatio = heuristicMeta.tracker.tinyTickRatio();
    int pulses = heuristicMeta.tracker.pulseCount();
    double peakCv = heuristicMeta.tracker.peakMagnitudeCv();
    heuristicMeta.tracker.reset();

    long now = System.currentTimeMillis();
    if (indicatesFailInjection(tinyRatio, pulses, peakCv)) {
      heuristicMeta.evidence.add(1.0d, now);
      if (heuristicMeta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
        double confidence = MathHelper.minmax(0.3d, pulses / 6.0d, 1.0d);
        flag(player, "artificial fail-rotation (" + pulses + " uniform revert pulses on a tight aim, peak CV "
          + MathHelper.formatDouble(peakCv, 2) + ")", confidence);
      }
    } else {
      // human-noisy or no clean pulses this window — let the evidence decay
      heuristicMeta.evidence.value(now);
    }
  }

  /**
   * Pure window verdict: a tight aim-assist baseline ({@link #MIN_TINY_RATIO}) punctuated by enough
   * ({@link #MIN_PULSES}) uniform ({@link #MAX_PEAK_CV}) revert pulses is artificial fail injection.
   */
  static boolean indicatesFailInjection(double tinyRatio, int pulseCount, double peakCv) {
    return tinyRatio >= MIN_TINY_RATIO && pulseCount >= MIN_PULSES && peakCv <= MAX_PEAK_CV;
  }

  public static final class FailRotationMeta extends CheckCustomMetadata {
    private final PulseTracker tracker = new PulseTracker();
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
