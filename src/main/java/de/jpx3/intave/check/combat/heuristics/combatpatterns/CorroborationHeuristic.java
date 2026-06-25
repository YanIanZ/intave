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
 * {@link ConfidenceLedger} on every attack and reacts to <i>breadth and strength of agreement</i>:
 * once at least {@link #MIN_DISTINCT_HEURISTICS} <b>distinct</b> heuristics have flagged the same
 * player within a short window, it fuses their evidence by <i>how strongly</i> each flagged, not just
 * how many — broad agreement among confident detectors is far stronger evidence than the same number
 * of borderline ones.
 *
 * <p>It is deliberately conservative and false-positive resistant:
 * <ul>
 *   <li>it requires several <i>independent</i> detectors to agree (its own type is excluded);</li>
 *   <li>evidence is the {@linkplain ConfidenceLedger#weightedCorroboration confidence-weighted}
 *       agreement above the baseline the minimum breadth contributes at floor confidence, so
 *       borderline agreement (everyone barely flagging) barely moves the needle — strictly more
 *       conservative than a raw count — while strong, broad agreement escalates quickly;</li>
 *   <li>that agreement is accumulated in a decaying {@link ConfidenceBuffer}, so a one-off coincidence
 *       of signals fades instead of flagging — only <i>sustained</i> corroboration crosses
 *       {@link #RELEASE_THRESHOLD};</li>
 *   <li>it flags with a {@linkplain ClassicHeuristic#flag(org.bukkit.entity.Player, String, double)
 *       graded confidence} that scales with the fused weight, so stronger agreement weighs more.</li>
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
  /** Per-heuristic confidence treated as "minimally meaningful"; baselines the weighted agreement so
   *  that breadth at trivial confidence contributes nothing (see the reinforcement calc in onAttack). */
  private static final double MIN_FLAG_CONFIDENCE = 0.3d;
  /** Weighted agreement that maps to full reported confidence. */
  private static final double FULL_CONFIDENCE_WEIGHT = 6.0d;

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
    ConfidenceLedger ledger = ledgerOf(user);
    int distinct = ledger.corroboratingHeuristics(WINDOW_MILLIS, HeuristicsClassicType.CORROBORATION);
    if (distinct < MIN_DISTINCT_HEURISTICS) {
      return; // breadth gate — several independent detectors must agree (FP-resistant, unchanged)
    }
    long now = System.currentTimeMillis();
    double weighted = ledger.weightedCorroboration(WINDOW_MILLIS, HeuristicsClassicType.CORROBORATION);
    // Confidence-weighted fusion: reinforce by *how strongly* the agreeing heuristics flagged, above the
    // baseline the minimum breadth contributes at floor confidence. Borderline agreement (everyone barely
    // flagging) therefore barely moves the buffer — strictly more conservative than a raw count — while
    // broad *and* confident agreement escalates quickly. The buffer decays so coincidences still fade.
    double reinforcement = Math.max(0d, weighted - MIN_DISTINCT_HEURISTICS * MIN_FLAG_CONFIDENCE);
    CorroborationMeta meta = metaOf(user);
    meta.evidence.add(reinforcement, now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      double confidence = MathHelper.minmax(0.3, weighted / FULL_CONFIDENCE_WEIGHT, 1.0);
      flag(user.player(), distinct + " independent heuristics agree (weighted "
        + MathHelper.formatDouble(weighted, 1) + ")", confidence);
    }
  }

  public static final class CorroborationMeta extends CheckCustomMetadata {
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
