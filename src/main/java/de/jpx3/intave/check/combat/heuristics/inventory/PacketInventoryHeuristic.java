package de.jpx3.intave.check.combat.heuristics.inventory;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.BURN_LONGER;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.DMG_HIGH;

/**
 * Detects inventory-aura and silent inventory automation by what the client does while a
 * container screen is open.
 *
 * <p>Two behaviours are caught:
 * <ul>
 *   <li><b>Rotations while in inventory</b> — vanilla freezes look input behind the inventory GUI,
 *       so look packets that carry a rotation change while the inventory is open reveal an aura
 *       acting through the screen. Flagged after more than one such packet, with a light hit nerf.</li>
 *   <li><b>Instant inventory close</b> — opening and closing the inventory within the same tick is
 *       the signature of automated item management (e.g. auto-armor/auto-totem); it is flagged and
 *       softly mitigated.</li>
 * </ul>
 *
 * <p>Only evaluated for clients whose flying-packet stream is observable and while the player is
 * not in a vehicle, both to keep the inventory-open state reliable.
 */
public final class PacketInventoryHeuristic extends ClassicHeuristic<PacketInventoryHeuristic.PacketInventoryMeta> {

	public PacketInventoryHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.INVENTORY_ROTATIONS, PacketInventoryHeuristic.PacketInventoryMeta.class);
	}

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLIENT_COMMAND
    }
  )
  public void receiveInventoryOpen(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    EnumWrappers.ClientCommand clientCommand = event.getPacket().getClientCommands().read(0);
    if (clientCommand == EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT) {
      PacketInventoryMeta meta = metaOf(user);
      meta.performedInventoryOpenOperation = true;
      meta.inventoryTicks = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      CLOSE_WINDOW
    }
  )
  public void receiveInventoryClose(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketInventoryMeta meta = metaOf(user);
    ProtocolMetadata clientData = user.meta().protocol();
    AbilityMetadata abilityData = user.meta().abilities();

    if (abilityData.ignoringMovementPackets()) {
      return;
    }

    if (clientData.flyingPacketsAreSent() && meta.inventoryTicks == 0 && meta.performedInventoryOpenOperation) {
      flag(player, "closed inventory too quickly (" + meta.inventoryTicks + ")");
      user.nerf(BURN_LONGER, nerfId);
      user.nerf(DMG_HIGH, nerfId);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, POSITION_LOOK, FLYING, LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketInventoryMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();
    boolean hasRotation = packet.getBooleans().read(2);

    InventoryMetadata inventoryData = user.meta().inventory();
    SimulationEnvironment movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();

    if (!clientData.flyingPacketsAreSent() || movementData.isInVehicle()) {
      return;
    }

    boolean inventoryOpen = inventoryData.inventoryOpen();

    if (!inventoryOpen) {
      meta.performedInventoryOpenOperation = false;
    }

    if (inventoryOpen && hasRotation && movementData.ticksPast(TELEPORT) > 20 && !player.isInsideVehicle()) {
      if (meta.rotationsInInventory++ > 1) {
        flag(player, "sent rotations in inventory (" + meta.rotationsInInventory + " rotations)");
        user.nerf(AttackNerfStrategy.HT_LIGHT, nerfId);
      }
    }

    if (!inventoryOpen) {
      meta.reset();
    }

    if (meta.performedInventoryOpenOperation) {
      meta.inventoryTicks++;
    } else {
      meta.inventoryTicks = 0;
    }
  }

  public static final class PacketInventoryMeta extends CheckCustomMetadata {
    private int rotationsInInventory;
    private int inventoryTicks;
    private boolean performedInventoryOpenOperation;

    private void reset() {
      rotationsInInventory = 0;
    }
  }
}