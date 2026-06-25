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

import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

/**
 * Information-theoretic aim detector — flags rotation that carries too little <i>entropy</i> to be
 * human.
 *
 * <p>This is the heuristic-engine-native counterpart to the feature class that modern ML anticheats
 * (e.g. the open-source MX project's Bi-LSTM model) key on: the <i>entropy</i> of a player's rotation
 * stream. A human's aim is driven by motor noise — countless tiny, irregular, unpredictable
 * corrections — so the distribution of their per-tick rotation steps is high-entropy (incompressible).
 * An aimbot computes each step, so its rotation stream is regular and repetitive: a small set of
 * recurring step magnitudes, i.e. <i>low entropy</i>. Unlike a neural network this needs no model or
 * training data — the low-entropy property is computed directly and is inherently hard to bypass,
 * because reproducing real human entropy requires reproducing real human motor noise.
 *
 * <p>While the player is actively fighting a moving target, the magnitude of each genuine rotation
 * step {@code |(Δyaw, Δpitch)|} is quantised and gathered into a window of {@link #WINDOW} samples.
 * The normalised Shannon entropy of that window (in {@code [0, 1]}; {@code 1} = every step distinct,
 * {@code 0} = one repeated value) is measured; a value below {@link #ENTROPY_THRESHOLD} means the
 * rotation was robotically repetitive.
 *
 * <p>Evidence is gathered in a decaying {@link ConfidenceBuffer} and it flags with a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence}. Like the other
 * behavioural rotation tells it ships at violation level {@code 0} (see
 * {@code heuristics.classic.rotation-entropy}) so out of the box it only feeds cross-heuristic
 * corroboration and verbose output; raise it after confirming no false positives.
 */
public final class RotationEntropyHeuristic extends ClassicHeuristic<RotationEntropyHeuristic.EntropyMeta> {
  /** Only ticks rotating at least this much (degrees) are sampled, so idle aim is ignored. */
  private static final float MIN_TURN_SPEED = 2.0f;
  /** Quantisation step (degrees) for binning rotation magnitudes before measuring entropy. */
  private static final double BIN_SIZE = 0.05d;
  /** Number of rotation samples per entropy window. */
  private static final int WINDOW = 30;
  /** Normalised entropy below which the rotation stream is considered robotically repetitive. */
  private static final double ENTROPY_THRESHOLD = 0.45d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated low-entropy-window evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;

  private static final double LOG_2 = Math.log(2.0d);

  public RotationEntropyHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_ENTROPY, EntropyMeta.class);
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

    float deltaYaw = wrapDegrees(movementData.rotationYaw - movementData.lastRotationYaw);
    float deltaPitch = movementData.rotationPitch - movementData.lastRotationPitch;
    double step = Math.sqrt((double) deltaYaw * deltaYaw + (double) deltaPitch * deltaPitch);
    if (step < MIN_TURN_SPEED) {
      // idle / non-aim tick — skip without resetting so the window gathers genuine aim samples
      return;
    }

    EntropyMeta heuristicMeta = metaOf(user);
    heuristicMeta.samples[heuristicMeta.count++] = (int) Math.round(step / BIN_SIZE);
    if (heuristicMeta.count < WINDOW) {
      return;
    }

    double entropy = normalisedEntropy(heuristicMeta.samples, heuristicMeta.count);
    heuristicMeta.count = 0;

    long now = System.currentTimeMillis();
    if (entropy < ENTROPY_THRESHOLD) {
      heuristicMeta.evidence.add(1.0d, now);
      if (heuristicMeta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
        double confidence = MathHelper.minmax(0.3d, (ENTROPY_THRESHOLD - entropy) / ENTROPY_THRESHOLD, 1.0d);
        flag(player, "low-entropy (robotic) aim stream (H " + MathHelper.formatDouble(entropy, 3) + ")", confidence);
      }
    } else {
      // human-like entropy this window — let the accumulated evidence decay
      heuristicMeta.evidence.value(now);
    }
  }

  /**
   * @return the Shannon entropy of the first {@code n} quantised samples normalised to {@code [0, 1]}
   * by the maximum entropy {@code log2(n)} (so it is independent of window size).
   */
  private static double normalisedEntropy(int[] bins, int n) {
    if (n <= 1) {
      return 1.0d;
    }
    Map<Integer, Integer> frequency = new HashMap<>();
    for (int i = 0; i < n; i++) {
      frequency.merge(bins[i], 1, Integer::sum);
    }
    double entropy = 0.0d;
    for (int occurrences : frequency.values()) {
      double probability = (double) occurrences / n;
      entropy -= probability * (Math.log(probability) / LOG_2);
    }
    return entropy / (Math.log(n) / LOG_2);
  }

  /** Wraps a degree delta to {@code [-180, 180)} so yaw wrap-around does not distort the step. */
  private static float wrapDegrees(float degrees) {
    float wrapped = degrees % 360.0f;
    if (wrapped >= 180.0f) {
      wrapped -= 360.0f;
    } else if (wrapped < -180.0f) {
      wrapped += 360.0f;
    }
    return wrapped;
  }

  public static final class EntropyMeta extends CheckCustomMetadata {
    private final int[] samples = new int[WINDOW];
    private int count;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
