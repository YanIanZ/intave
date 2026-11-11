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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.packet.reader.TeleportAcceptReader;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.share.Teleport;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.OptionalInt;

public final class TeleportRotationRefusalTests extends IntegrationTests {
  public TeleportRotationRefusalTests() {
    super("teleport-rotation-refusal");
  }

  @Test(testCode = "teleport-rotation-layered-refusal", severity = Severity.ERROR)
  public void testLayeredRefusalProducesViolation() throws ReflectiveOperationException {
    Player player = FakePlayerFactory.createPlayer((methodName, arguments) -> null);
    User user = UserFactory.createTestUserFor(player, (ignored, key) -> {
      if (key.equals("protocolVersion")) {
        return 776; // Minecraft 26.2
      }
      if (key.equals("justJoined")) {
        return false;
      }
      if (key.equals("joined")) {
        return 0L;
      }
      if (key.equals("trustFactor")) {
        return TrustFactor.GREEN;
      }
      return null;
    });
    UserRepository.manuallyRegisterUser(player, user);
    try {
      ProtocolScanner scanner = IntavePlugin.singletonInstance().checks()
        .searchCheck(ProtocolScanner.class);
      TeleportRotationRefusal part = new TeleportRotationRefusal(user, scanner);
      MovementMetadata movement = user.meta().movement();
      movement.setRotation(0.0F, 0.0F);
      movement.setLastRotation(0.0F, 0.0F);

      PositionMoveRotation change = new PositionMoveRotation(
        new Position(0.0D, 64.0D, 0.0D),
        Motion.newEmpty(),
        new Rotation(90.0F, 0.0F)
      );
      Teleport teleport = Teleport.of(
        1L, OptionalInt.of(37), change, EnumSet.noneOf(Relative.class), null, true
      );
      teleport.allow();
      movement.pendingTeleports.get().add(teleport);

      try (TeleportAcceptReader accept = PacketReaders.readerOf(acceptPacket(37))) {
        part.receiveTeleportAccept(accept);
      }
      part.receiveCombatAction(user);

      try (PlayerMoveReader confirm = PacketReaders.readerOf(
        positionLookPacket(0.0D, 64.0D, 0.0D, 90.0F, 0.0F)
      )) {
        part.receiveMovement(confirm);
      }

      try (PlayerMoveReader revert = PacketReaders.readerOf(
        positionLookPacket(0.0D, 64.0D, 0.0D, 0.0F, 0.0F)
      )) {
        part.receiveMovement(revert);
      }

      assertTrue(part.lastDecision() != null && part.lastDecision().shouldFlag());
    } finally {
      UserRepository.unregisterUser(player);
    }
  }

  private static PacketContainer acceptPacket(int teleportId) {
    PacketContainer packet = new PacketContainer(PacketType.Play.Client.TELEPORT_ACCEPT);
    packet.getIntegers().write(0, teleportId);
    return packet;
  }

  private static PacketContainer positionLookPacket(
    double x, double y, double z, float yaw, float pitch
  ) throws ReflectiveOperationException {
    Object handle = Class.forName(
        "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$PosRot"
      )
      .getConstructor(
        double.class, double.class, double.class,
        float.class, float.class, boolean.class, boolean.class
      )
      .newInstance(x, y, z, yaw, pitch, false, false);
    return new PacketContainer(PacketType.Play.Client.POSITION_LOOK, handle);
  }
}
