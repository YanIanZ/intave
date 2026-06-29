package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.SustainedStreakDetector;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Detects kill-aura that lands attacks on an entity standing <i>behind a solid block</i> ("through-wall"
 * / wallbang aura).
 *
 * <p>The reach/hit-box check ({@code AttackRaytrace}) deliberately ignores terrain — it only proves the
 * angle and distance are valid. So an aura that swings at a target through a wall passes it. Vanilla
 * cannot: a melee hit requires an unobstructed line from the eye to the hit-box. This heuristic isolates
 * exactly that by ray-tracing the attacked entity twice from the player's validated eye/look:
 *
 * <ul>
 *   <li><b>ignoring blocks</b> — confirms the swing <i>would</i> have been a valid hit on the hit-box
 *       (right angle, within reach), so a plain miss is never considered;</li>
 *   <li><b>respecting blocks</b> — the same ray is stopped by terrain before it reaches the entity
 *       ({@code reach == }{@link #NO_HIT}).</li>
 * </ul>
 *
 * The two disagreeing means a solid block sits between the player and the target along the exact ray
 * they hit on — a wallbang.
 *
 * <p>Block updates, ghost blocks and lag can momentarily desync a single hit, so each through-wall
 * attack feeds the shared {@link SustainedStreakDetector}: only a sustained run flags, at a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence}. It only runs for a
 * stable, long-tracked target (no recent entity switch, well past the last teleport) to avoid
 * position-desync false positives, and ships at violation level {@code 0} (see
 * {@code heuristics.classic.attack-through-wall}) so out of the box it observes/corroborates; raise it
 * after confirming no false positives on your map geometry.
 */
public final class AttackThroughWallHeuristic extends ClassicHeuristic<AttackThroughWallHeuristic.ThroughWallMeta> {
  /** Sentinel {@link Raytrace#reach()} value meaning the ray reached nothing (blocked or out of range). */
  private static final double NO_HIT = 10.0d;
  /** {@link Raytrace#reach()} value meaning the ray passed but missed the hit-box. */
  private static final double OUTSIDE_HITBOX = -1.0d;
  /** Expansion applied to the entity hit-box during the trace (default vanilla hit-box slack). */
  private static final float HITBOX_EXPANSION = 0.1f;
  /** Ticks past the last teleport before sampling, so warp/position desync is ignored. */
  private static final int MIN_TICKS_SINCE_TELEPORT = 20;
  /** A target switched more recently than this is too fresh to trust its tracked position. */
  private static final long ENTITY_STABLE_MILLIS = 1_000L;
  private static final double BUFFER_HALF_LIFE_MILLIS = 5_000d;
  /** Accumulated through-wall evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;
  /** Through-wall attacks closer together than this continue a streak (otherwise it restarts). */
  private static final long STREAK_GAP_MILLIS = 1_500L;
  /** Streak length at which the run is unambiguously a wallbang aura. */
  private static final double SUSTAINED_STREAK = 6.0d;

  public AttackThroughWallHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_THROUGH_WALL, ThroughWallMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void onAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    MetadataBundle meta = user.meta();
    AttackMetadata attackData = meta.attack();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();

    Entity entity = attackData.lastAttackedEntity();
    if (entity == null
      || attackData.recentlySwitchedEntity(ENTITY_STABLE_MILLIS)
      || movementData.ticksPast(TELEPORT) < MIN_TICKS_SINCE_TELEPORT
      || entity.positionHistory.size() <= 2) {
      return;
    }

    Player player = user.player();
    boolean alternativePositionY = clientData.protocolVersion() == ProtocolMetadata.VER_1_8;

    Raytrace ignoringBlocks = Raytracing.blockIgnoringEntityRaytrace(
      player, entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      movementData.rotationYaw, movementData.rotationPitch, HITBOX_EXPANSION);
    Raytrace respectingBlocks = Raytracing.blockConstraintEntityRaytrace(
      player, entity, alternativePositionY,
      movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ,
      movementData.rotationYaw, movementData.rotationPitch, HITBOX_EXPANSION);

    if (!isThroughWall(ignoringBlocks.reach(), respectingBlocks.reach())) {
      return;
    }

    ThroughWallMeta heuristicMeta = metaOf(user);
    double confidence = heuristicMeta.detector.note(System.currentTimeMillis());
    if (confidence != SustainedStreakDetector.NO_FLAG) {
      flag(player, "attacked an entity through a solid block (streak "
        + heuristicMeta.detector.streak() + ") — wallbang aura", confidence);
    }
  }

  /**
   * Pure tell: the swing is a valid hit on the hit-box when terrain is ignored ({@code ignoreReach} hit)
   * but the same ray is stopped by a block before it reaches the entity ({@code respectReach ==}
   * {@link #NO_HIT}). A hit is any non-sentinel reach (inside the box or a positive distance to it); a
   * plain miss ({@link #OUTSIDE_HITBOX} or {@link #NO_HIT}) on the ignoring trace is never flagged.
   */
  static boolean isThroughWall(double ignoreReach, double respectReach) {
    boolean ignoringWasAHit = ignoreReach != NO_HIT && ignoreReach != OUTSIDE_HITBOX;
    boolean respectingWasBlocked = respectReach == NO_HIT;
    return ignoringWasAHit && respectingWasBlocked;
  }

  public static final class ThroughWallMeta extends CheckCustomMetadata {
    private final SustainedStreakDetector detector =
      new SustainedStreakDetector(BUFFER_HALF_LIFE_MILLIS, RELEASE_THRESHOLD, STREAK_GAP_MILLIS, SUSTAINED_STREAK, 0.4d);
  }
}
