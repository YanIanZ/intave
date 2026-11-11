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

package de.jpx3.intave.module.network;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.wureta.proxy.protocol.FrameType;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

public final class NetworkSanctionRelay {
  private final NetworkSync network;
  private final NetworkSettings settings;

  NetworkSanctionRelay(NetworkSync network, NetworkSettings settings) {
    this.network = network;
    this.settings = settings;
  }

  public void publishBlock(String playerName, UUID playerId, String address, String reason) {
    if (!settings.syncSanctions()) {
      return;
    }
    NetworkPayloads.Sanction sanction = new NetworkPayloads.Sanction(
      playerId, playerName, address, reason, NetworkPayloads.Sanction.BLOCK, 0L
    );
    network.broadcast(FrameType.SANCTION, UUID.randomUUID(), playerId, sanction.encode());
  }

  public void publishKick(String playerName, UUID playerId, String reason) {
    if (!settings.syncSanctions()) {
      return;
    }
    NetworkPayloads.Sanction sanction = new NetworkPayloads.Sanction(
      playerId, playerName, "", reason, NetworkPayloads.Sanction.KICK, 0L
    );
    network.broadcast(FrameType.SANCTION, UUID.randomUUID(), playerId, sanction.encode());
  }

  public void handle(NetworkFrame frame) {
    if (!settings.syncSanctions() || frame.source().equals(settings.serverId())) {
      return;
    }
    NetworkPayloads.Sanction sanction;
    try {
      sanction = NetworkPayloads.Sanction.decode(frame.payload());
    } catch (RuntimeException exception) {
      IntaveLogger.logger().warn("Invalid cross-server sanction: " + exception.getMessage());
      return;
    }
    if (sanction.expiresAt() > 0 && System.currentTimeMillis() > sanction.expiresAt()) {
      return;
    }
    if (sanction.type() == NetworkPayloads.Sanction.BLOCK) {
      InetAddress address = null;
      if (sanction.address() != null && !sanction.address().isEmpty()) {
        try {
          address = InetAddress.getByName(sanction.address());
        } catch (UnknownHostException exception) {
          IntaveLogger.logger().warn("Invalid sanctioned address " + sanction.address());
        }
      }
      IntavePlugin.singletonInstance().playerListService().applyNetworkSanction(
        sanction.playerName(), sanction.playerId(), address, sanction.reason()
      );
    } else if (sanction.type() == NetworkPayloads.Sanction.KICK) {
      Player player = Bukkit.getPlayer(sanction.playerId());
      if (player != null && player.isOnline()) {
        User user = UserRepository.userOf(player);
        Synchronizer.synchronize(user, () -> player.kickPlayer(sanction.reason()));
      }
    }
  }
}
