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
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.wureta.proxy.protocol.FrameType;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class NetworkTrustFactorRelay {
  private final NetworkSync network;
  private final NetworkSettings settings;

  NetworkTrustFactorRelay(NetworkSync network, NetworkSettings settings) {
    this.network = network;
    this.settings = settings;
  }

  public void publish(Player player, TrustFactor trustFactor, String reason) {
    if (!settings.syncTrustFactor() || trustFactor == TrustFactor.BYPASS) {
      return;
    }
    NetworkPayloads.Trust update = new NetworkPayloads.Trust(
      player.getUniqueId(),
      player.getName(),
      trustFactor.name(),
      reason
    );
    network.broadcast(FrameType.TRUST, UUID.randomUUID(), player.getUniqueId(), update.encode());
  }

  public void handle(NetworkFrame frame) {
    if (!settings.syncTrustFactor() || frame.source().equals(settings.serverId())) {
      return;
    }
    NetworkPayloads.Trust update;
    try {
      update = NetworkPayloads.Trust.decode(frame.payload());
    } catch (RuntimeException exception) {
      IntaveLogger.logger().warn("Invalid cross-server trust update: " + exception.getMessage());
      return;
    }
    Player player = Bukkit.getPlayer(update.playerId());
    if (player == null || !player.isOnline()) {
      return;
    }
    TrustFactor trustFactor;
    try {
      trustFactor = TrustFactor.valueOf(update.trustFactor());
    } catch (IllegalArgumentException exception) {
      return;
    }
    IntavePlugin.singletonInstance().trustFactorService()
      .applyNetworkTrustFactor(player, trustFactor, frame.source());
  }
}
