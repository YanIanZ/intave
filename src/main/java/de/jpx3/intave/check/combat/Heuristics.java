package de.jpx3.intave.check.combat;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckConfiguration;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.AttackRequiredHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.CorroborationHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.GhostClientHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.PreAttackHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy.AccuracyHitboxCornerHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy.AccuracyLongTermHeuristic;
import de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.*;
import de.jpx3.intave.check.combat.heuristics.inventory.PacketInventoryHeuristic;
import de.jpx3.intave.check.combat.heuristics.other.*;
import de.jpx3.intave.check.combat.heuristics.testing.TestingHeuristic;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Aggregating combat check that hosts every "classic" (on-premise) heuristic.
 *
 * <p>Unlike Intave's deterministic simulation checks, which prove a client broke the rules,
 * the heuristics gathered here detect the <i>statistical fingerprints</i> that combat cheats
 * (kill-aura, aimbot, auto-clicker, auto-blocker, scaffold-assist, …) leave behind. Each
 * registered {@link de.jpx3.intave.check.combat.heuristics.ClassicHeuristic} contributes to a
 * single, shared and decaying violation level so that independent weak signals can compound
 * into a confident detection before any mitigation or punishment is applied.
 *
 * <p>The per-heuristic violation weights and the escalation thresholds are read from
 * {@code heuristics.classic.*}; see {@link HeuristicsClassicType} for the mapping. All
 * heuristics target the full supported protocol span (1.7 – 26.2); version-specific
 * restrictions are documented on the individual heuristic classes.
 */
public final class Heuristics extends Check {
  private final Map<HeuristicsClassicType, Integer> classicViolationLevelMap = new HashMap<>();

  public Heuristics(IntavePlugin plugin) {
    super("Heuristics", "heuristics");
    this.loadClassicConfiguration();
    this.setupClassicHeuristics();
  }

  private void setupClassicHeuristics() {
    // for testing
    appendCheckPart(new TestingHeuristic(this));

    appendCheckPart(new RotationStandardDeviationHeuristic(this));
    appendCheckPart(new RotationSnapHeuristic(this));
    appendCheckPart(new AccuracyLongTermHeuristic(this));
    appendCheckPart(new RotationAccuracyYawHeuristic(this));
    appendCheckPart(new RotationExactHeuristic(this));
    appendCheckPart(new AccuracyHitboxCornerHeuristic(this));
    appendCheckPart(new RotationSensitivityHeuristic(this));
    appendCheckPart(new RotationModuloResetHeuristic(this));
    appendCheckPart(new RotationConstantSpeedHeuristic(this));
    appendCheckPart(new RotationAccelerationHeuristic(this));
    appendCheckPart(new AimSmoothingHeuristic(this));
    appendCheckPart(new RotationLinearityHeuristic(this));
    appendCheckPart(new RotationEntropyHeuristic(this));
    appendCheckPart(new RotationJitterHeuristic(this));
    appendCheckPart(new PreAttackHeuristic(this));

    appendCheckPart(new AttackRequiredHeuristic(this));
    appendCheckPart(new ToolSwitchHeuristic(this));
    appendCheckPart(new FastSwapHeuristic(this));
    appendCheckPart(new MaceFallDistanceHeuristic(this));
    appendCheckPart(new MultiAuraHeuristic(this));
    appendCheckPart(new CrystalAuraHeuristic(this));
    appendCheckPart(new SpearAttackSpeedHeuristic(this));
    appendCheckPart(new AttackWhileConsumingHeuristic(this));
    appendCheckPart(new AttackWhileBowDrawHeuristic(this));
    appendCheckPart(new AttackWhileInventoryOpenHeuristic(this));

    appendCheckPart(new PacketOrderSwingHeuristic(this));
    appendCheckPart(new PacketPlayerActionToggleHeuristic(this));
    appendCheckPart(new PacketInventoryHeuristic(this));
    appendCheckPart(new BlockingHeuristic(this));
    appendCheckPart(new NoSwingHeuristic(this));
    appendCheckPart(new CivbreakHeuristic(this));

    // Meta-detectors: weigh the combination of the above via the shared confidence ledger.
    // Registered last so they are the final attack-packet listeners in the cluster; the
    // ghost-client verdict reads the breadth of agreement the corroboration detector also consults.
    appendCheckPart(new CorroborationHeuristic(this));
    appendCheckPart(new GhostClientHeuristic(this));
  }

  private void loadClassicConfiguration() {
    CheckConfiguration.CheckSettings settings = configuration().settings();
    for (HeuristicsClassicType classType : HeuristicsClassicType.values()) {
      String fullConfigurationName = "classic." + classType.configurationName();
      int violationLevelIncrease = settings.intBy(fullConfigurationName);
      classicViolationLevelMap.put(classType, violationLevelIncrease);
    }
  }

  public void cloudFlag(Player player, String details) {
    // soon:TM:
  }

  public Map<HeuristicsClassicType, Integer> classicViolationLevelMap() {
    return classicViolationLevelMap;
  }
}