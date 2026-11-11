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
    Fixture fixture = fixture(776, TrustFactor.GREEN, false);
    try {
      fixture.accept();
      fixture.combatAction();
      fixture.confirmServerView();
      fixture.revertToPreviousView();
      assertTrue(fixture.part.lastDecision() != null);
      assertTrue(fixture.part.lastDecision().shouldFlag());
    } finally {
      fixture.close();
    }
  }

  @Test(testCode = "teleport-rotation-normal-adoption", severity = Severity.ERROR)
  public void testNormalAdoptionHasNoDecision() throws ReflectiveOperationException {
    Fixture fixture = fixture(776, TrustFactor.GREEN, false);
    try {
      fixture.accept();
      fixture.confirmServerView();
      assertNull(fixture.part.lastDecision());
    } finally {
      fixture.close();
    }
  }

  @Test(testCode = "teleport-rotation-bypass-ignored", severity = Severity.ERROR)
  public void testBypassTrustFactorIsIgnored() throws ReflectiveOperationException {
    Fixture fixture = fixture(776, TrustFactor.BYPASS, false);
    try {
      fixture.accept();
      fixture.combatAction();
      fixture.confirmServerView();
      fixture.revertToPreviousView();
      assertNull(fixture.part.lastDecision());
    } finally {
      fixture.close();
    }
  }

  @Test(testCode = "teleport-rotation-inventory-ignored", severity = Severity.ERROR)
  public void testInventoryOpenIsIgnored() throws ReflectiveOperationException {
    Fixture fixture = fixture(776, TrustFactor.GREEN, false);
    try {
      fixture.user.meta().inventory().updateInventoryOpenState(true);
      fixture.accept();
      fixture.combatAction();
      fixture.confirmServerView();
      fixture.revertToPreviousView();
      assertNull(fixture.part.lastDecision());
    } finally {
      fixture.close();
    }
  }

  @Test(testCode = "teleport-rotation-camera-ignored", severity = Severity.ERROR)
  public void testCameraEntityIsIgnored() throws ReflectiveOperationException {
    Fixture fixture = fixture(776, TrustFactor.GREEN, false);
    try {
      fixture.user.meta().abilities().hasViewEntity = true;
      fixture.accept();
      fixture.combatAction();
      fixture.confirmServerView();
      fixture.revertToPreviousView();
      assertNull(fixture.part.lastDecision());
    } finally {
      fixture.close();
    }
  }

  @Test(testCode = "teleport-rotation-just-joined-ignored", severity = Severity.ERROR)
  public void testJustJoinedIsIgnored() throws ReflectiveOperationException {
    Fixture fixture = fixture(776, TrustFactor.GREEN, true);
    try {
      fixture.accept();
      fixture.combatAction();
      fixture.confirmServerView();
      fixture.revertToPreviousView();
      assertNull(fixture.part.lastDecision());
    } finally {
      fixture.close();
    }
  }

  @Test(testCode = "teleport-rotation-position-only", severity = Severity.ERROR)
  public void testPositionOnlyTeleportDoesNotFlag() throws ReflectiveOperationException {
    Fixture fixture = fixture(776, TrustFactor.GREEN, false);
    try {
      fixture.accept();
      fixture.combatAction();
      assertNull(fixture.part.lastDecision());
    } finally {
      fixture.close();
    }
  }

  private static Fixture fixture(
    int protocolVersion, TrustFactor trustFactor, boolean justJoined
  ) {
    Player player = FakePlayerFactory.createPlayer((methodName, arguments) -> null);
    User user = UserFactory.createTestUserFor(player, (ignored, key) -> {
      if (key.equals("protocolVersion")) {
        return protocolVersion;
      }
      if (key.equals("justJoined")) {
        return justJoined;
      }
      if (key.equals("joined")) {
        return 0L;
      }
      if (key.equals("trustFactor")) {
        return trustFactor;
      }
      return null;
    });
    UserRepository.manuallyRegisterUser(player, user);
    ProtocolScanner scanner = IntavePlugin.singletonInstance().checks()
      .searchCheck(ProtocolScanner.class);
    return new Fixture(player, user, new TeleportRotationRefusal(user, scanner));
  }

  private static final class Fixture {
    private final Player player;
    private final User user;
    private final TeleportRotationRefusal part;
    private final MovementMetadata movement;

    private Fixture(
      Player player, User user, TeleportRotationRefusal part
    ) {
      this.player = player;
      this.user = user;
      this.part = part;
      this.movement = user.meta().movement();
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
    }

    private void accept() {
      try (TeleportAcceptReader accept = PacketReaders.readerOf(acceptPacket(37))) {
        part.receiveTeleportAccept(accept);
      }
    }

    private void combatAction() {
      part.receiveCombatAction(user);
    }

    private void confirmServerView() throws ReflectiveOperationException {
      try (PlayerMoveReader confirm = PacketReaders.readerOf(
        positionLookPacket(0.0D, 64.0D, 0.0D, 90.0F, 0.0F)
      )) {
        part.receiveMovement(confirm);
      }
    }

    private void revertToPreviousView() throws ReflectiveOperationException {
      try (PlayerMoveReader revert = PacketReaders.readerOf(
        positionLookPacket(0.0D, 64.0D, 0.0D, 0.0F, 0.0F)
      )) {
        part.receiveMovement(revert);
      }
    }

    private void close() {
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
