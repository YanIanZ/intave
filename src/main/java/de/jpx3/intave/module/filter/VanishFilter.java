package de.jpx3.intave.module.filter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerInfoReader;
import de.jpx3.intave.packet.reader.PlayerInfoRemoveReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class VanishFilter extends Filter {
  private final boolean disabled;
  private boolean yukiJoined = false;
  private long lastYukiJoin = 0;

  public VanishFilter(IntavePlugin plugin) {
    super("vanish");
    disabled = plugin.settings().getBoolean("command.fix-tab-kicks", false);
  }


  @PacketSubscription(
    packetsOut = {PLAYER_INFO}
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    PlayerInfoReader reader = PacketReaders.readerOf(packet);
    Set<EnumWrappers.PlayerInfoAction> actions = reader.playerInfoActions();
    List<PlayerInfoData> playerInfos = reader.playerInfoData();

    for (EnumWrappers.PlayerInfoAction action : actions) {
      switch (action) {
        case ADD_PLAYER:
          playerInfos.forEach(data -> {
            UUID uuid = data.getProfile().getUUID();
            if (shownPlayers.contains(uuid)) {
              return;
            }
            shownPlayers.add(uuid);
          });
          break;
        case UPDATE_GAME_MODE:
        case UPDATE_LATENCY:
          playerInfos.removeIf(playerInfo -> {
            UUID infoId = playerInfo.getProfile().getUUID();
	          return !shownPlayers.contains(infoId);
          });
          break;
        case REMOVE_PLAYER:
          playerInfos.removeIf(playerInfoData -> {
            UUID uuid = playerInfoData.getProfile().getUUID();
            boolean wasVisible = shownPlayers.remove(uuid);
            return !wasVisible;
          });
          break;
      }
    }

    if (playerInfos.isEmpty()) {
      event.setCancelled(true);
    }

    Collections.shuffle(playerInfos);
    reader.writePlayerInfoData(playerInfos);
    reader.release();
  }

  @PacketSubscription(
    prioritySlot = PrioritySlot.EXTERNAL,
    priority = ListenerPriority.MONITOR,
    packetsOut = {
      TAB_COMPLETE_OUT
    }
  )
  public void receiveTabComplete(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;

    PacketContainer packet = event.getPacket();
    String[] stuff = packet.getStringArrays().readSafely(0);
    if (stuff != null) {
      List<String> playerNames = Bukkit.getOnlinePlayers().stream()
        .map(Player::getName).collect(Collectors.toList());
      List<String> hiddenPlayers = Lists.newArrayList();
      for (String name : playerNames) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
          continue;
        }
        if (!shownPlayers.contains(target.getUniqueId())) {
          hiddenPlayers.add(name);
        }
      }
      List<String> newTabCompletions = Lists.newArrayList();
      Arrays.stream(stuff).filter(string -> !hiddenPlayers.contains(string)).forEach(newTabCompletions::add);
      if (newTabCompletions.size() != stuff.length) {
        packet.getStringArrays().writeSafely(0, newTabCompletions.toArray(new String[0]));
      }
    }
  }

  @PacketSubscription(
    packetsOut = {
      PLAYER_INFO_REMOVE
    }
  )
  public void onRemoval(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();
    Set<UUID> shownPlayers = protocol.shownPlayers;
    PlayerInfoRemoveReader reader = PacketReaders.readerOf(packet);
    List<UUID> uuids = reader.playersToRemove();
    uuids.removeIf(uuid -> !shownPlayers.contains(uuid));
    reader.release();
  }

  @Override
  protected boolean enabled() {
    return !disabled && super.enabled();
  }
}
