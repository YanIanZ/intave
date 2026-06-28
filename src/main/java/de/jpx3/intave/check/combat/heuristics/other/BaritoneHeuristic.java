package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.SustainedStreakDetector;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Combat-domain expression of the Baritone tell — a player attacking while simultaneously auto-pathing.
 *
 * <p>The {@code Pathfinder} movement check ({@link de.jpx3.intave.check.movement.pathfinder.HeadingLock})
 * stamps {@code MovementMetadata#lastPathfinderHeadingLockMillis} whenever it sees a window of robotic
 * heading-lock-through-turns. This heuristic reads that stamp on every attack: a human who decides to
 * fight breaks off the bot-perfect travel pattern, so landing attacks <i>while</i> the pathing bot is
 * still steering is a strong "bot-pathing while fighting" combination.
 *
 * <p>It records into the shared {@link de.jpx3.intave.check.combat.heuristics.ConfidenceLedger} via
 * {@link ClassicHeuristic#flag(org.bukkit.entity.Player, String, double)}, so it corroborates with the
 * combat tells and sharpens the {@code corroboration} / {@code ghost-client} verdicts rather than
 * deciding alone. A single attack can race the pathing window, so a decaying {@link ConfidenceBuffer}
 * requires a sustained run. It ships at violation level {@code 0} (see {@code heuristics.classic.baritone})
 * so out of the box it only feeds corroboration and verbose output; raise it after tuning.
 */
public final class BaritoneHeuristic extends ClassicHeuristic<BaritoneHeuristic.BaritoneMeta> {
  /** An attack counts as "while pathing" if a robotic heading-lock window was seen this recently. */
  private static final long PATHING_RECENCY_MILLIS = 1_500L;
  private static final double BUFFER_HALF_LIFE_MILLIS = 6_000d;
  /** Accumulated attack-while-pathing evidence required before a flag is released. */
  private static final double RELEASE_THRESHOLD = 3.0d;
  /** Streak length at which the run is unambiguously a bot fighting while it paths. */
  private static final double SUSTAINED_STREAK = 6.0d;

  public BaritoneHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.BARITONE, BaritoneMeta.class);
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
    long lock = movementData.lastPathfinderHeadingLockMillis;
    if (lock == 0L) {
      return;
    }
    long now = System.currentTimeMillis();
    long sinceLock = now - lock;
    if (sinceLock > PATHING_RECENCY_MILLIS) {
      return;
    }

    BaritoneMeta meta = metaOf(user);
    double confidence = meta.detector.note(now);
    if (confidence != SustainedStreakDetector.NO_FLAG) {
      flag(user.player(), "attacked while auto-pathing (heading-locked " + sinceLock
        + "ms ago, streak " + meta.detector.streak() + ")", confidence);
    }
  }

  public static final class BaritoneMeta extends CheckCustomMetadata {
    private final SustainedStreakDetector detector =
      new SustainedStreakDetector(BUFFER_HALF_LIFE_MILLIS, RELEASE_THRESHOLD, PATHING_RECENCY_MILLIS, SUSTAINED_STREAK, 0.4d);
  }
}
