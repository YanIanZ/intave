package de.jpx3.intave.check.combat.heuristics;

import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

/**
 * Base class for every on-premise combat heuristic.
 *
 * <p>A {@code ClassicHeuristic} subscribes to client packets, accumulates evidence in its own
 * per-player {@link CheckCustomMetadata} bucket and, once that evidence is conclusive, calls
 * {@link #flag(Player, String)}. Flagging raises the player's heuristics violation level by the
 * amount configured for this heuristic's {@link HeuristicsClassicType} and routes the event
 * through the shared {@code classic.thresholds} escalation ladder.
 *
 * <p>The {@link #nerfId} field exposes the heuristic's verbose name so subclasses can attribute
 * the soft combat mitigations (knock-back/damage/hit reduction) they request via
 * {@code user.nerf(...)}.
 *
 * @param <M> the per-player state object this heuristic keeps
 */
public class ClassicHeuristic<M extends CheckCustomMetadata> extends MetaCheckPart<Heuristics, M> {
  protected final Heuristics parentCheck;
  protected final String nerfId;
  private final HeuristicsClassicType type;
  private final int violationLevelIncrease;

  protected ClassicHeuristic(Heuristics parentCheck, HeuristicsClassicType type, Class<? extends M> metaClass) {
    super(parentCheck, metaClass);
    this.parentCheck = parentCheck;
    this.type = type;
    this.violationLevelIncrease = parentCheck.classicViolationLevelMap().getOrDefault(type, 0);
    this.nerfId = type.verboseName();
  }

  /**
   * Flags this heuristic at full confidence. Equivalent to {@link #flag(Player, String, double)}
   * with a confidence of {@code 1.0}.
   */
  protected void flag(Player player, String details) {
    flag(player, details, 1.0);
  }

  /**
   * Flags this heuristic with a graded confidence in {@code [0, 1]}.
   *
   * <p>The confidence scales the violation level this flag contributes: weak, ambiguous evidence
   * can flag at, say, {@code 0.3} and add proportionally less than a textbook detection at
   * {@code 1.0}. This lets a check express <i>how sure</i> it is instead of the all-or-nothing
   * model, without any per-check threshold retuning ({@code 1.0} reproduces the previous behaviour
   * exactly). The flag is also recorded in the shared {@link ConfidenceLedger}, so the engine can
   * weigh cross-heuristic corroboration; when more than one distinct heuristic has flagged the
   * player recently, that corroboration count is attached to the violation for verbose output.
   *
   * <p>Bedrock (Geyser/Floodgate) players are exempt: their input is translated touch/controller aim
   * with built-in assist, which these Java-style heuristics cannot analyse without false positives.
   * Their flags are skipped entirely — no violation level and no ledger recording — while reach,
   * movement and ray-trace checks outside this engine still guard them.
   *
   * @param confidence strength of the evidence, clamped to {@code [0, 1]}
   */
  protected void flag(Player player, String details, double confidence) {
    if (BedrockPlayers.isBedrock(player)) {
      return;
    }
    double weight = MathHelper.minmax(0.0, confidence, 1.0);
    ConfidenceLedger ledger = ledgerOf(userOf(player));
    ledger.note(type, weight);

    Violation.Builder builder = Violation.builderFor(Heuristics.class)
      .forPlayer(player).withMessage("failed " + type.verboseName())
      .withDetails(details)
      .withVL(violationLevelIncrease * weight)
      .withCustomThreshold("classic.thresholds");

    int corroboration = ledger.corroboratingHeuristics(ConfidenceLedger.DEFAULT_CORROBORATION_WINDOW_MILLIS);
    if (corroboration > 1) {
      builder.addGranular("corroboration", corroboration + " heuristics");
    }
    Modules.violationProcessor().processViolation(builder.build());
  }

  /**
   * Resolves the player's shared cross-heuristic {@link ConfidenceLedger} (lazily created and
   * lifecycle-managed by the user metadata pool). All heuristics share one ledger per player.
   */
  protected ConfidenceLedger ledgerOf(User user) {
    return (ConfidenceLedger) user.checkMetadata(ConfidenceLedger.class, ignored -> new ConfidenceLedger());
  }

  /**
   * @return how many distinct heuristics have flagged this player within the default corroboration
   * window — a measure of how many independent detectors currently agree.
   */
  protected int corroboratingHeuristics(User user) {
    return ledgerOf(user).corroboratingHeuristics(ConfidenceLedger.DEFAULT_CORROBORATION_WINDOW_MILLIS);
  }

  @Override
  public boolean enabled() {
    return violationLevelIncrease >= 0;
  }
}
