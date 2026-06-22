package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Detects spoofed swing/attack packet ordering produced by some aura and packet-mimic cheats.
 *
 * <p>On clients that still stream flying packets (1.8 and ViaVersion-bridged 1.8), the vanilla
 * client always sends the arm-animation in the <i>same</i> tick as the attack it belongs to.
 * Cheats that inject the attack out of band leave the attack arriving in a tick with no
 * preceding swing. The heuristic records whether the most recent packet in the tick was a swing
 * and, on a flagged attack, reports the mismatch and applies a light damage nerf.
 *
 * <p>Limited to clients whose flying-packet stream is observable; modern clients that batch
 * inputs differently are covered by {@link NoSwingHeuristic} instead.
 */
public final class PacketOrderSwingHeuristic extends ClassicHeuristic<PacketOrderSwingHeuristic.PacketOrderSwingHeuristicMeta> {
  private final IntavePlugin plugin;

  public PacketOrderSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.SWING_ORDER, PacketOrderSwingHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK, ARM_ANIMATION
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.swingTick = event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION;
  }

  @PacketSubscription(
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void receiveUseEntity(
    User user, EntityUseReader reader
  ) {
    ProtocolMetadata protocol = user.meta().protocol();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(user);
    if (user.meta().abilities().ignoringMovementPackets()) {
      return;
    }
    if (reader.isAttackPacket() && protocol.flyingPacketsAreSent() && !heuristicMeta.swingTick) {
      String description = "swing not correlated with attack (" + user.meta().protocol().versionString() + ")";
      flag(user.player(), description);
      //dmc11
      user.nerf(AttackNerfStrategy.DMG_LIGHT, "11");
    }
  }

  public static final class PacketOrderSwingHeuristicMeta extends CheckCustomMetadata {
    private boolean swingTick;
  }
}
