package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

/**
 * Cancels "civbreak", a fast-break exploit that abuses a server bug.
 *
 * <p>Civbreak instant-breaks a block on a position where a block was already destroyed by sending
 * additional {@code STOP_DESTROY_BLOCK} packets without the preceding {@code START_DESTROY_BLOCK}.
 * This heuristic tracks the mining state and drops the rogue stop packets so the duplicate break
 * never reaches the server.
 *
 * <p><b>Version scope:</b> reliable only below 1.14 ({@code protocolVersion() < VER_1_14}). From
 * 1.14 onward the client legitimately omits the start packet when breaking the same block
 * repeatedly, so the two cases can no longer be told apart from the dig packets alone — see the
 * inline TODO. Unlike the other entries here this is a pure mitigation (no violation level), so it
 * extends {@link MetaCheckPart} directly rather than {@code ClassicHeuristic}.
 */
public final class CivbreakHeuristic extends MetaCheckPart<Heuristics, CivbreakHeuristic.CivbreakMeta> {

  public CivbreakHeuristic(Heuristics parentCheck) {
    super(parentCheck, CivbreakMeta.class);
  }
  @PacketSubscription(
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    CivbreakMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();
    EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
    // Note: isMining should set to false on every PlayerDigType except START_DESTROY_BLOCK
//    player.sendMessage("" + playerDigType);
    if (playerDigType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
      meta.isMining = true;
    }
    if (playerDigType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
      if (user.protocolVersion() < ProtocolMetadata.VER_1_14) {
        if (!meta.isMining) {
//          player.sendMessage("cancel");
          event.setCancelled(true);
        }
      } else {
        // TODO: fix civbreak on 1.14+
        // players don't send a start break packet when destroying a block multiple times on 1.14+
      }
      meta.isMining = false;
    }
  }

  public static final class CivbreakMeta extends CheckCustomMetadata {
    private boolean isMining;
  }
}