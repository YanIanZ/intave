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
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.wureta.proxy.protocol.FrameType;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class NetworkSync extends Module {
  private NetworkSettings settings;
  private ProxyMessageTransport transport;
  private ProxyStorageGateway storageGateway;
  private NetworkAlertRelay alertRelay;
  private NetworkTrustFactorRelay trustFactorRelay;
  private NetworkSanctionRelay sanctionRelay;
  private Task tickTask;
  private boolean storageGatewayInstalled;
  private boolean active;

  @Override
  public void enable() {
    settings = NetworkSettings.from(plugin);
    if (!settings.enabled()) {
      return;
    }
    if (!settings.hasSecret()) {
      IntaveLogger.logger().error(
        "Cross-server sync is enabled but network.secret is empty; refusing to run unsigned"
      );
      return;
    }
    if (settings.serverId().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64) {
      IntaveLogger.logger().error(
        "Cross-server sync is enabled but network.server-id exceeds 64 bytes"
      );
      return;
    }

    try {
      ProxyMessageTransport candidate = new ProxyMessageTransport(plugin, settings, this::handleFrame);
      if (!candidate.register()) {
        return;
      }
      transport = candidate;
      storageGateway = new ProxyStorageGateway(transport, settings);
      alertRelay = new NetworkAlertRelay(this, settings);
      trustFactorRelay = new NetworkTrustFactorRelay(this, settings);
      sanctionRelay = new NetworkSanctionRelay(this, settings);

      if (settings.syncStorage() && !Modules.storage().hasStorageGateway()) {
        Modules.storage().setStorageGateway(storageGateway);
        storageGatewayInstalled = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
          Modules.storage().requestStorageFor(player);
        }
      }

      tickTask = Tasks.periodicNamed(
        "NetworkSync.tick",
        () -> {
          transport.flush();
          transport.cleanup();
          storageGateway.tick(System.currentTimeMillis());
        },
        0,
        20
      ).startAsync();

      active = true;
      IntaveLogger.logger().info(
        "Cross-server sync enabled as " + settings.serverId() +
          " on " + settings.channel() +
          " (alerts=" + settings.syncAlerts() +
          ", trust=" + settings.syncTrustFactor() +
          ", sanctions=" + settings.syncSanctions() +
          ", storage=" + settings.syncStorage() + ")"
      );
    } catch (RuntimeException exception) {
      IntaveLogger.logger().error("Unable to enable cross-server sync: " + exception.getMessage());
      active = false;
      if (tickTask != null) {
        tickTask.cancel();
        tickTask = null;
      }
      if (transport != null) {
        transport.close();
        transport = null;
      }
      if (storageGatewayInstalled) {
        try {
          Modules.storage().setStorageGateway(null);
        } catch (RuntimeException ignored) {
        }
        storageGatewayInstalled = false;
      }
    }
  }

  @Override
  public void disable() {
    if (!active) {
      return;
    }
    active = false;
    if (tickTask != null) {
      tickTask.cancel();
      tickTask = null;
    }
    if (storageGateway != null) {
      storageGateway.failAll();
    }
    if (storageGatewayInstalled) {
      try {
        Modules.storage().setStorageGateway(null);
      } catch (RuntimeException exception) {
        IntaveLogger.logger().warn("Unable to detach cross-server storage: " + exception.getMessage());
      }
      storageGatewayInstalled = false;
    }
    if (transport != null) {
      transport.close();
      transport = null;
    }
    IntaveLogger.logger().info("Cross-server sync disabled");
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    if (transport != null) {
      transport.flush();
    }
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    if (transport != null) {
      transport.flush();
    }
  }

  public void handleFrame(NetworkFrame frame) {
    if (!active || frame.source().equals(settings.serverId())) {
      return;
    }
    switch (frame.type()) {
      case ALERT:
        alertRelay.handle(frame);
        break;
      case TRUST:
        trustFactorRelay.handle(frame);
        break;
      case SANCTION:
        sanctionRelay.handle(frame);
        break;
      case REQUEST:
      case RESPONSE:
        storageGateway.handle(frame);
        break;
      default:
        break;
    }
  }

  public void publishViolation(ViolationContext context) {
    if (active) {
      alertRelay.publish(context);
    }
  }

  public void publishTrustFactor(Player player, TrustFactor trustFactor, String reason) {
    if (active) {
      trustFactorRelay.publish(player, trustFactor, reason);
    }
  }

  public void publishBlock(String playerName, UUID playerId, String address, String reason) {
    if (active) {
      sanctionRelay.publishBlock(playerName, playerId, address, reason);
    }
  }

  public void publishKick(String playerName, UUID playerId, String reason) {
    if (active) {
      sanctionRelay.publishKick(playerName, playerId, reason);
    }
  }

  void broadcast(FrameType type, UUID requestId, UUID playerId, byte[] payload) {
    if (transport == null) {
      return;
    }
    transport.send(new NetworkFrame(
      type,
      requestId,
      playerId,
      settings.serverId(),
      "",
      System.currentTimeMillis(),
      payload
    ));
  }

  public boolean active() {
    return active;
  }

  public String status() {
    if (!active) {
      return "disabled";
    }
    return "server=" + settings.serverId() +
      " channel=" + settings.channel() +
      " queued=" + transport.queuedFrames() +
      " sent=" + transport.sentFrames() +
      " received=" + transport.receivedFrames() +
      " dropped=" + transport.droppedFrames() +
      " storagePending=" + storageGateway.pendingCount();
  }
}
