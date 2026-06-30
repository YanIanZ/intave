package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

/**
 * Detects aimbots that compute and send the <i>mathematically perfect</i> angle to a target.
 *
 * <p>The "perfect" yaw/pitch is the angle that points exactly at the attacked entity. A human
 * aiming with a mouse will always be off by some sub-degree residual; a naive aimbot that sets
 * the rotation to the computed value lands a residual of literally {@code 0}. When the player
 * is actively turning ({@code yawSpeed}/{@code pitchSpeed} above {@link #MIN_ROTATION_SPEED}) at
 * a moving, recently-attacked target and the residual to either the perfect yaw or its nearest
 * 360°-wrapped equivalent is exactly zero, this heuristic flags and applies a criticals nerf.
 *
 * <p>Because an exact match is essentially impossible for a real input device, this is a
 * high-weight, low-noise signal — it deliberately does not fire for merely "close" angles, as
 * those are covered with buffering by {@code RotationAccuracyYawHeuristic} and
 * {@code RotationStandardDeviationHeuristic}.
 */
public class RotationExactHeuristic extends ClassicHeuristic<RotationExactHeuristic.RotationExactHeuristicMeta> {
  // Minimum per-tick rotation (degrees) required before an exact match counts; standing still
  // can momentarily yield a zero residual without any aiming taking place.
  private static final float MIN_ROTATION_SPEED = 1.0f;

  public RotationExactHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_EXACT, RotationExactHeuristicMeta.class);
  }


  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AttackMetadata attackData = meta.attack();
    Entity attackedEntity = attackData.lastAttackedEntity();

    if (movementData.ticksPast(TELEPORT) < 20) {
      return;
    }

    if (attackedEntity == null || !attackedEntity.moving(0.05) || !attackData.recentlyAttacked(1000)) {
      return;
    }

    float rotationYaw = movementData.rotationYaw;
    float yawSpeed = MathHelper.distanceInDegrees(rotationYaw, movementData.lastRotationYaw);
    if (yawSpeed > MIN_ROTATION_SPEED) {
      float perfectYaw = attackData.perfectYaw();
      float closestPerfectYaw = attackData.perfectClosestYaw();
      float distanceToPerfectYaw = MathHelper.distanceInDegrees(perfectYaw, rotationYaw);
      float distanceToClosestPerfectYaw = MathHelper.distanceInDegrees(closestPerfectYaw, rotationYaw);

      if (distanceToPerfectYaw == 0 || distanceToClosestPerfectYaw == 0) {
        flag(user.player(), "sent exact yaw rotation");
        user.nerf(AttackNerfStrategy.CRITICALS, nerfId);
      }
    }

    float pitchSpeed = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);
    float distanceToPerfectPitch = Math.abs(movementData.rotationPitch - attackData.perfectPitch());
    if (pitchSpeed > MIN_ROTATION_SPEED && distanceToPerfectPitch == 0) {
      flag(player, "sent exact pitch rotation");
      user.nerf(AttackNerfStrategy.CRITICALS, nerfId);
    }
  }

  public static class RotationExactHeuristicMeta extends CheckCustomMetadata {
  }
}
