package de.jpx3.intave.check.combat.heuristics.combatpatterns;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.ConfidenceLedger;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Corroboration meta-detector — the first check built on the shared heuristic engine.
 *
 * <p>The individual heuristics each watch a single facet of combat (rotation accuracy, snaps,
 * swing/attack pairing, …). On their own, a borderline cheat may only ever nudge several of them
 * weakly without any single one escalating. This detector consults the per-player
 * {@link ConfidenceLedger} on every attack and reacts to <i>breadth of agreement</i>: when at least
 * {@link #MIN_DISTINCT_HEURISTICS} <b>distinct</b> heuristics have flagged the same player within a
 * short window, that is far stronger evidence than any one of them repeating.
 *
 * <p>It is deliberately conservative and false-positive resistant:
 * <ul>
 *   <li>it requires several <i>independent</i> detectors to agree (its own type is excluded);</li>
 *   <li>agreement is accumulated in a decaying {@link ConfidenceBuffer}, so a one-off coincidence of
 *       signals fades instead of flagging — only <i>sustained</i> corroboration crosses
 *       {@link #RELEASE_THRESHOLD};</li>
 *   <li>it flags with a {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double)
 *       graded confidence} that scales with how many heuristics corroborate, so broader agreement
 *       weighs more.</li>
 * </ul>
 *
 * <p>It adds no detection of its own beyond what the other heuristics already observed; it only
 * weighs their <i>combination</i>. Its violation weight is configured under
 * {@code heuristics.classic.corroboration} and can be set to {@code 0} to observe (log/verbose) it
 * without contributing violation level.
 */
public final class CorroborationHeuristic extends ClassicHeuristic<CorroborationHeuristic.CorroborationMeta> {
  private static final long WINDOW_MILLIS = ConfidenceLedger.DEFAULT_CORROBORATION_WINDOW_MILLIS;
  /** Minimum number of distinct, independent heuristics that must agree before evidence builds. */
  private static final int MIN_DISTINCT_HEURISTICS = 3;
  /** Accumulated corroboration required before a flag is released (i.e. sustained agreement). */
  private static final double RELEASE_THRESHOLD = 4.0d;
  private static final double BUFFER_HALF_LIFE_MILLIS = 6_000d;

  public CorroborationHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.CORROBORATION, CorroborationMeta.class);
  }

  @PacketSubscription(
    // Run late so attack-driven heuristics for this packet have already recorded their flags.
    priority = ListenerPriority.LOW,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void onAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    int corroboration = ledgerOf(user)
      .corroboratingHeuristics(WINDOW_MILLIS, HeuristicsClassicType.CORROBORATION);
    if (corroboration < MIN_DISTINCT_HEURISTICS) {
      return;
    }
    long now = System.currentTimeMillis();
    CorroborationMeta meta = metaOf(user);
    // More agreement reinforces faster; the buffer decays so isolated coincidences fade.
    meta.evidence.add(corroboration - MIN_DISTINCT_HEURISTICS + 1, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.3, (corroboration - 2) / 4.0d, 1.0);
      flag(user.player(), corroboration + " independent heuristics agree", confidence);
    }
  }

  public static final class CorroborationMeta extends CheckCustomMetadata {
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
