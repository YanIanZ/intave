package de.jpx3.intave.check.combat.heuristics;

import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.violation.Violation;
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

  protected void flag(Player player, String details) {
    Violation violation = Violation.builderFor(Heuristics.class)
      .forPlayer(player).withMessage("failed " + type.verboseName())
      .withDetails(details)
      .withVL(violationLevelIncrease)
      .withCustomThreshold("classic.thresholds")
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  @Override
  public boolean enabled() {
    return violationLevelIncrease >= 0;
  }
}
