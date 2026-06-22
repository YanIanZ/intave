package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

/**
 * Detects synthetic aim by tracking the greatest common divisor (GCD) of pitch rotation deltas.
 *
 * <p>A physical mouse maps its raw counts onto rotation through a fixed sensitivity, so every
 * delta a human produces is an integer multiple of one tiny base step — i.e. consecutive deltas
 * share a stable GCD. Cheats that synthesise rotations from floating-point target maths break
 * this invariant: the GCD jumps around. The check accumulates a violation balance whenever the
 * running pitch GCD shifts by more than {@link #GCD_STABILITY_EPSILON} and only flags once that
 * balance sustains itself past {@link #SENSITIVITY_VL_LIMIT}.
 *
 * <p>Note: this heuristic is shipped <b>disabled by default</b> ({@code rotation-sensitivity: -1}
 * in {@code advanced.yml}) because GCD analysis is sensitive to client/proxy rounding and should
 * be enabled and tuned per server. It remains fully functional across all supported versions
 * (1.7 – 26.2).
 */
public final class RotationSensitivityHeuristic extends ClassicHeuristic<RotationSensitivityHeuristic.RotationGCDMeta> {
  // Largest GCD drift (degrees) still considered "stable"; above it the rotation step changed.
  private static final double GCD_STABILITY_EPSILON = 0.001;
  // Sustained violation balance at which the desynchronised-sensitivity flag is released.
  private static final int SENSITIVITY_VL_LIMIT = 400;

	public RotationSensitivityHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_SENSITIVITY, RotationGCDMeta.class);
	}

  @PacketSubscription(
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void rotationCheck(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);

    AttackMetadata attackData = user.meta().attack();
    SimulationEnvironment movementData = user.meta().movement();

    if (movementData.ticksPast(TELEPORT) < 20) {
      return;
    }

    if (attackData.recentlyAttacked(200)) {
      ensureSensitivity(player, user);
    }
  }

  private void ensureSensitivity(Player player, User user) {
    RotationGCDMeta heuristicMeta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();
    float pitchDifference = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);

    float prevPitchGCD = heuristicMeta.prevPitchGCD;
    if (prevPitchGCD == 0) {
      prevPitchGCD = pitchDifference;
    }
    double pitchA = prevPitchGCD;
    double pitchB = pitchDifference;
    double pitchR;
    int pitchCountdown = 100;

    while ((pitchR = pitchA % pitchB) > Math.max(pitchA, pitchB) * 1e-3) {
      pitchA = pitchB;
      pitchB = pitchR;
      if (pitchCountdown-- < 0) {
        break;
      }
    }

    float pitchGCD = (float) pitchB;
    double gcdDifference = Math.abs(pitchGCD - prevPitchGCD);

    heuristicMeta.prevPitchGCD = pitchGCD;

    if (gcdDifference > GCD_STABILITY_EPSILON) {
      if (pitchDifference > 1.0) {
        heuristicMeta.sensitivityVL += pitchDifference > 5 ? 10 : 5;
      }
      if ((int) Math.round(heuristicMeta.sensitivityVL / 2d) % 50 == 0 && heuristicMeta.sensitivityVL > 0) {
        if (heuristicMeta.sensitivityVL >= SENSITIVITY_VL_LIMIT) {
          flag(player, "rotations are out of sync (gcd vl:" + heuristicMeta.sensitivityVL + ")");
          heuristicMeta.sensitivityVL = 300;
        }
      }
    } else if (heuristicMeta.sensitivityVL > 0) {
      heuristicMeta.sensitivityVL--;
    }
  }

  public static class RotationGCDMeta extends CheckCustomMetadata {
    private int sensitivityVL;
    private float prevPitchGCD;
  }
}
