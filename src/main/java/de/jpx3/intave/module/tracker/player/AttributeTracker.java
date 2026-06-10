package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.attribute.WrappedAttributeModifier;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.UPDATE_ATTRIBUTES;

public final class AttributeTracker extends Module {
  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      UPDATE_ATTRIBUTES
    }
  )
  public void sentAttributes(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    if (packet.getIntegers().read(0) == player.getEntityId()) {
      StructureModifier<List<WrappedAttribute>> mod = packet.getAttributeCollectionModifier();
      List<WrappedAttribute> attributes = mod.read(0);
      mod.write(0, attributes);
      user.tickFeedback(() -> {
        attributes.forEach(attribute -> receivedAttribute(user, attribute));
      });
    }
  }

  private void receivedAttribute(User user, WrappedAttribute attribute) {
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    if (abilities.findAttribute(attribute.getAttributeKey()) != null) {
      de.jpx3.intave.player.attribute.WrappedAttribute intaveAttribute = de.jpx3.intave.player.attribute.WrappedAttribute.fromProtocolLib(attribute);
      List<WrappedAttributeModifier> intaveAttributes = abilities.modifiersOf(intaveAttribute);
      intaveAttributes.clear();
      Set<WrappedAttributeModifier> serverAttributes = intaveAttribute.modifiers();
      movement.hasSprintSpeed = serverAttributes.contains(MovementMetadata.SPRINTING_MODIFIER);
      intaveAttributes.addAll(new HashSet<>(serverAttributes));
      abilities.modifyBaseValue(attribute.getAttributeKey(), attribute.getBaseValue());
    }
  }
}
