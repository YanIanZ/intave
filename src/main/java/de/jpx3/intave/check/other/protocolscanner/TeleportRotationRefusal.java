/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.other.protocolscanner;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.packet.reader.TeleportAcceptReader;
import de.jpx3.intave.share.PositionAndRotation;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.share.Teleport;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Deque;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TELEPORT_ACCEPT;

/**
 * Detects clients that ignore the yaw and pitch of a server teleport.
 * <p>
 * A vanilla client adopts the server-set rotation and keeps that view. NoRotate-style clients
 * confirm the teleport with the server rotation (the confirmation is sent before their module
 * restores the old view), then revert to the pre-teleport rotation in the following movement
 * packets. This part arms when the teleport is acknowledged, accepts the adoption, and watches the
 * next rotation-bearing packets for a reversal to the pre-teleport view.
 * <p>
 * Detection-only: the part never cancels packets or issues teleport actions. The server's rotation
 * remains trusted by {@code TeleportController}; this part reports the refusal to the violation
 * pipeline.
 */
public final class TeleportRotationRefusal extends PlayerCheckPart<ProtocolScanner> {
  private final int vl;
  private final TeleportRotationTracker tracker;

  public TeleportRotationRefusal(User user, ProtocolScanner parentCheck) {
    super(user, parentCheck);
    this.vl = parentCheck.configuration().settings().intBy("teleport-rotation-vl", 10);
    this.tracker = new TeleportRotationTracker(
      parentCheck.configuration().settings().intBy("teleport-rotation-refusals", 2),
      parentCheck.configuration().settings().intBy("teleport-rotation-timeout", 20),
      parentCheck.configuration().settings().intBy("teleport-rotation-cooldown", 1000),
      parentCheck.configuration().settings().intBy("teleport-rotation-refusal-window", 30000),
      (float) parentCheck.configuration().settings().doubleBy("teleport-rotation-epsilon", 0.05),
      (float) parentCheck.configuration().settings().doubleBy("teleport-rotation-min-delta", 30.0)
    );
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {TELEPORT_ACCEPT}
  )
  public void receiveTeleportAccept(TeleportAcceptReader reader) {
    if (vl <= 0) {
      return;
    }
    User user = owningUser();
    if (shouldIgnore(user)) {
      tracker.reset();
      return;
    }
    arm(user, reader.teleportId());
    if (!tracker.armed() || !user.meta().protocol().teleportAcceptIncludesPositionAndRotation()) {
      return;
    }
    PositionAndRotation accepted = reader.positionAndRotation();
    if (accepted == null) {
      return;
    }
    observe(user, accepted.yaw(), accepted.pitch());
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {POSITION_LOOK, LOOK}
  )
  public void receiveMovement(PlayerMoveReader reader) {
    if (vl <= 0 || !tracker.armed()) {
      return;
    }
    User user = owningUser();
    if (shouldIgnore(user)) {
      tracker.reset();
      return;
    }
    if (!reader.hasRotation()) {
      return;
    }
    observe(user, reader.yaw(), reader.pitch());
  }

  @BukkitEventSubscription
  public void receiveRespawn(PlayerRespawnEvent event) {
    tracker.reset();
  }

  private void arm(User user, int acceptedTeleportId) {
    MovementMetadata movement = user.meta().movement();
    Deque<Teleport> queue = movement.pendingTeleports.get();
    Teleport first = null;
    for (Teleport candidate : queue) {
      if (!candidate.id().isPresent() || candidate.id().getAsInt() == acceptedTeleportId) {
        first = candidate;
        break;
      }
    }
    if (first == null || first.wasAccepted() || !first.isAllowed()) {
      return;
    }
    if (first.relativeSet().contains(Relative.Y_ROT) || first.relativeSet().contains(Relative.X_ROT)) {
      // A relative rotation is resolved against the client view we are trying to verify.
      return;
    }
    PositionMoveRotation change = first.change();
    if (change == null) {
      return;
    }
    Rotation expected = change.rotation();
    if (expected == null) {
      return;
    }
    boolean armed = tracker.arm(
      acceptedTeleportId,
      expected.yaw(), expected.pitch(),
      movement.rotationYaw(), movement.rotationPitch(),
      System.currentTimeMillis()
    );
    if (!armed) {
      tracker.clear();
    }
  }

  private boolean shouldIgnore(User user) {
    if (user.justJoined() || user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      return true;
    }
    MovementMetadata movement = user.meta().movement();
    if (movement.isInVehicle() || movement.inRespawnScreen) {
      return true;
    }
    // Spectators and camera entities do not control the player's server rotation.
    if (user.meta().abilities().ignoringMovementPackets()) {
      return true;
    }
    // Vanilla does not rotate the camera while a container screen is open.
    return user.meta().inventory().inventoryOpen();
  }

  private void observe(User user, float sentYaw, float sentPitch) {
    TeleportRotationTracker.Observation observation = tracker.observe(
      sentYaw, sentPitch, System.currentTimeMillis()
    );
    if (observation == TeleportRotationTracker.Observation.FLAGGED) {
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forUser(user)
        .withMessage("ignored teleport rotation")
        .withDetails(
          "server delta " + tracker.lastServerDelta() + " degrees, client delta "
            + tracker.lastClientDelta() + " degrees"
        )
        .withVL(vl)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
  }
}
