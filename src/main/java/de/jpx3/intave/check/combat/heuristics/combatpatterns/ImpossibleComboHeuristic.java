package de.jpx3.intave.check.combat.heuristics.combatpatterns;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ConfidenceBuffer;
import de.jpx3.intave.check.combat.heuristics.ConfidenceLedger;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Definitive cheat verdict — fires when at least two <i>distinct physical-impossibility</i> tells
 * agree on the same player within a short window. Zero false positive by construction.
 *
 * <p>The other meta-detector, {@link CorroborationHeuristic}, weighs the breadth of agreement across
 * <i>all</i> heuristics, including the statistical "implausible" ones (constant-speed aim, smoothing,
 * …) — strong evidence, but probabilistic. This detector instead consults only the subset of tells
 * that describe something the game does <b>not physically allow</b>: striking distinct entities within
 * one tick ({@code multi-aura}), attacking while eating/drinking, while drawing a bow, or while a
 * container GUI is open, and a mace smash with more fall distance than its airtime permits. A
 * legitimate player trips <i>none</i> of these, each is internally sustained-/lag-gated already, so two
 * of them coinciding is not "suspicious" — it is certain. That makes this a safe place to escalate
 * hard and fast.
 *
 * <p>It still smooths through a decaying {@link ConfidenceBuffer} (so a lone window does not instantly
 * release) and flags at full confidence. Its weight is configured under
 * {@code heuristics.classic.impossible-combo}; because the verdict is definitive it ships
 * <b>enforced</b>, unlike the statistical tells which remain observe-only.
 */
public final class ImpossibleComboHeuristic extends ClassicHeuristic<ImpossibleComboHeuristic.ComboMeta> {
  /** The physical-impossibility tells. A legitimate player trips none of these. */
  private static final HeuristicsClassicType[] HARD_INVARIANTS = {
    HeuristicsClassicType.MULTI_AURA,
    HeuristicsClassicType.ATTACK_WHILE_CONSUMING,
    HeuristicsClassicType.ATTACK_WHILE_BOW_DRAW,
    HeuristicsClassicType.ATTACK_WHILE_INVENTORY_OPEN,
    HeuristicsClassicType.MACE_FALL_DISTANCE
  };
  private static final long WINDOW_MILLIS = ConfidenceLedger.DEFAULT_CORROBORATION_WINDOW_MILLIS;
  /** Distinct impossibilities that must coincide. Two at once cannot happen legitimately. */
  private static final int MIN_IMPOSSIBILITIES = 2;
  private static final double BUFFER_HALF_LIFE_MILLIS = 8_000d;
  /** Accumulated agreement required before the verdict is released (a little smoothing). */
  private static final double RELEASE_THRESHOLD = 1.5d;

  public ImpossibleComboHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.IMPOSSIBLE_COMBO, ComboMeta.class);
  }

  @PacketSubscription(
    // Run late so the impossibility tells for this packet have already recorded their flags.
    priority = ListenerPriority.LOW,
    packetsIn = {ATTACK_ENTITY, USE_ENTITY}
  )
  public void onAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket()) {
      return;
    }
    int impossibilities = ledgerOf(user).distinctRecent(WINDOW_MILLIS, HARD_INVARIANTS);
    if (impossibilities < MIN_IMPOSSIBILITIES) {
      return;
    }
    long now = System.currentTimeMillis();
    ComboMeta meta = metaOf(user);
    // More coinciding impossibilities release faster; the buffer decays so it cannot latch forever.
    meta.evidence.add(impossibilities - (MIN_IMPOSSIBILITIES - 1), now);
    if (meta.evidence.consumeIfAtLeast(RELEASE_THRESHOLD, now)) {
      flag(user.player(), impossibilities + " distinct physical impossibilities agree (definitive)", 1.0d);
    }
  }

  public static final class ComboMeta extends CheckCustomMetadata {
    private final ConfidenceBuffer evidence = new ConfidenceBuffer(BUFFER_HALF_LIFE_MILLIS);
  }
}
