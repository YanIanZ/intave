package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

/**
 * Detects "silent-aim" cheats that snap to a target for a single tick and then reset the view.
 *
 * <p>Silent-aim sends an attack while the camera momentarily faces the target, then restores the
 * player's original look angle so the snap is invisible on screen. The reset shows up as a very
 * large single-tick yaw jump ({@code receivedDistance > }{@link #SUSPICIOUS_RESET_YAW}) <i>back</i>
 * to an angle that no longer has the entity in its line of sight.
 *
 * <p>The check is a two-stage state machine: stage&nbsp;2 arms when a large jump that still has the
 * entity in sight is seen during recent combat; stage&nbsp;1 then confirms on the following packet
 * if the entity is once again being looked at, indicating the camera bounced onto the target and
 * straight back. It only runs for a stable, long-tracked target ({@code ticksPast(TELEPORT) >}
 * {@link #MIN_TICKS_SINCE_TELEPORT}, no recent entity switch) to avoid legitimate flick-aims.
 */
public final class RotationModuloResetHeuristic extends ClassicHeuristic<RotationModuloResetHeuristic.RotationModuloResetHeuristicMeta> {
  // A target must be tracked for this many ticks since the last teleport before the state machine
  // engages, and the reset jump must exceed this many degrees in one packet to be considered.
  private static final int MIN_TICKS_SINCE_TELEPORT = 100;
  private static final float SUSPICIOUS_RESET_YAW = 100f;

	public RotationModuloResetHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_MODULO_RESET, RotationModuloResetHeuristicMeta.class);
	}

  @PacketSubscription(
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    AttackMetadata attackData = user.meta().attack();
    RotationModuloResetHeuristicMeta heuristicMeta = metaOf(user);

    Entity attackedEntity = attackData.lastAttackedEntity();
    if (attackedEntity == null || attackData.recentlySwitchedEntity(5000) || movementData.ticksPast(TELEPORT) < MIN_TICKS_SINCE_TELEPORT) {
      return;
    }

    float rotationYaw = movementData.rotationYaw;
    float lastRotationYaw = movementData.lastRotationYaw;

    /*
    1: Check stage
     */
    if (heuristicMeta.roundedRotationLooking) {
      if (entityInLineOfSight(user)) {
        float penaltyYaw = movementData.lastRotationYaw;
        if (penaltyYaw != 0) {
          flag(player, "possible rotation reset");
        }
      }
      heuristicMeta.roundedRotationLooking = false;
      return;
    }

    /*
    2: Prepare for stage 1
     */
    if (attackData.recentlyAttacked(1000) && attackData.lastReach() > 1.0) {
      float receivedDistance = Math.abs(rotationYaw - lastRotationYaw);
      boolean roundingConditions = Math.abs(rotationYaw) <= 360 && Math.abs(lastRotationYaw) <= 360;
      boolean suspiciousYaw = roundingConditions && receivedDistance > SUSPICIOUS_RESET_YAW;

      if (suspiciousYaw && entityInLineOfSight(user)) {
        heuristicMeta.roundedRotationLooking = true;
      }
    }
  }

  private boolean entityInLineOfSight(User user) {
    MetadataBundle meta = user.meta();
    AttackMetadata attackData = meta.attack();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    boolean alternativePositionY = clientData.protocolVersion() == ProtocolMetadata.VER_1_8;
    Raytrace raytraceTraceResult = Raytracing.blockIgnoringEntityRaytrace(
      user.player(),
      attackData.lastAttackedEntity(),
      alternativePositionY,
      movementData.lastPositionX,
      movementData.lastPositionY,
      movementData.lastPositionZ,
      movementData.rotationYaw,
      movementData.rotationPitch,
      0.1f
    );
    return raytraceTraceResult.reach() != 10;
  }

  public static final class RotationModuloResetHeuristicMeta extends CheckCustomMetadata {
    private boolean roundedRotationLooking;
  }
}