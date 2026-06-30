package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class InvalidPitch extends CheckPart<ProtocolScanner> {
  public InvalidPitch(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveRotation(PacketEvent event) {
    Player player = event.getPlayer();
    float rotationYaw = event.getPacket().getFloat().read(0);
    float rotationPitch = event.getPacket().getFloat().read(1);
    // The vanilla client clamps pitch to [-90, 90] and never emits a non-finite (NaN/Infinite) yaw
    // or pitch. Non-finite values must be tested explicitly, because `NaN > 90.000001f` is false:
    // such packets would otherwise bypass the range guard and go on to poison every rotation-based
    // combat heuristic with NaN arithmetic. Any offending component is reset to a safe value.
    boolean nonFiniteYaw = !Float.isFinite(rotationYaw);
    boolean nonFinitePitch = !Float.isFinite(rotationPitch);
    boolean invalidPitchRange = !nonFinitePitch && Math.abs(rotationPitch) > 90.000001f;

    if (nonFiniteYaw || nonFinitePitch || invalidPitchRange) {
      if (nonFiniteYaw) {
        event.getPacket().getFloat().writeSafely(0, 0f);
      }
      if (nonFinitePitch || invalidPitchRange) {
        event.getPacket().getFloat().writeSafely(1, 0f);
      }
      String message = "sent invalid rotation";
      String details = "pitch at " + (nonFinitePitch ? String.valueOf(rotationPitch) : MathHelper.formatDouble(rotationPitch, 4))
        + ", yaw at " + (nonFiniteYaw ? String.valueOf(rotationYaw) : MathHelper.formatDouble(rotationYaw, 4));
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withVL(100)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
  }
}