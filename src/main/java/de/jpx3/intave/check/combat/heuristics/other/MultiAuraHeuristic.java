package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Detects "multi-aura" — kill-aura that strikes several targets at once by switching between them
 * faster than a human ever could.
 *
 * <p>A legitimate player processes one click per client tick with their cursor resting on a single
 * entity, so two attacks landing on <i>distinct</i> entities inside the same tick is something
 * manual input cannot produce: the aura is alternating between victims. Each such within-a-tick target
 * switch feeds a decaying {@link ConfidenceBuffer} (weighted by how close together the two hits were),
 * so isolated coincidences — e.g. two genuinely-spaced attacks flushed together after a lag spike —
 * fade on their own, and only a <i>sustained</i> super-human switching cadence flags, at a
 * {@linkplain ClassicHeuristic#flag(Player, String, double) graded confidence} that scales with how
 * tight the switch was.
 *
 * <p>Because players legitimately retarget quickly in crowded fights and mob farms, this ships at
 * violation level {@code 0} (see {@code heuristics.classic.multi-aura}): out of the box it only
 * contributes to cross-heuristic {@linkplain de.jpx3.intave.check.combat.heuristics.ConfidenceLedger
 * corroboration} and verbose output rather than escalating on its own. Raise it after confirming no
 * false positives.
 */
public final class MultiAuraHeuristic extends ClassicHeuristic<MultiAuraHeuristic.MultiAuraMeta> {
  /** Sentinel for "no previous target recorded yet". */
  private static final int UNSET = -1;
  /** Two attacks on distinct entities closer than one tick apart are faster than vanilla input allows. */
  private static final long ONE_TICK_MILLIS = 55L;
  private static final double BUFFER_HALF_LIFE_MILLIS = 6_000d;
  /** Accumulated evidence required before a flag is released (i.e. a sustained switching cadence). */
  private static final double RELEASE_THRESHOLD = 3.0d;

  public MultiAuraHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.MULTI_AURA, MultiAuraMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void receiveAttack(Player player, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    int targetId = reader.entityId();
    long now = System.currentTimeMillis();
    User user = userOf(player);
    MultiAuraMeta meta = metaOf(user);

    int previousTarget = meta.lastTargetId;
    long delta = now - meta.lastAttackMillis;
    meta.lastTargetId = targetId;
    meta.lastAttackMillis = now;

    // Only a switch to a *different* entity within a single tick is suspicious.
    if (previousTarget == UNSET || previousTarget == targetId || delta < 0 || delta >= ONE_TICK_MILLIS) {
      return;
    }

    double closeness = MathHelper.minmax(0.0d, (ONE_TICK_MILLIS - delta) / (double) ONE_TICK_MILLIS, 1.0d);
    meta.evidence.add(1.0d + closeness, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.4d, closeness, 1.0d);
      flag(player, "attacks alternate between distinct targets within a tick (" + delta + "ms)", confidence);
    }
  }

  public static final class MultiAuraMeta extends CheckCustomMetadata {
    private int lastTargetId = UNSET;
    private long lastAttackMillis;
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
