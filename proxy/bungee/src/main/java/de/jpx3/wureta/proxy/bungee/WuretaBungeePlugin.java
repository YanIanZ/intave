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

package de.jpx3.wureta.proxy.bungee;

import de.jpx3.wureta.proxy.protocol.ProxyConfig;
import de.jpx3.wureta.proxy.protocol.ProxyPlatform;
import de.jpx3.wureta.proxy.protocol.ProxyRouter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class WuretaBungeePlugin extends Plugin implements Listener {
  private ProxyRouter router;
  private String channel;
  private ScheduledTask tickTask;

  @Override
  public void onEnable() {
    ProxyConfig config = ProxyConfig.load(getDataFolder().toPath().resolve("wureta-proxy.properties"));
    if (config.secret().isEmpty()) {
      getLogger().warning("WuretaProxy is running without a shared secret; set one in wureta-proxy.properties");
    }
    channel = config.channel();
    getProxy().registerChannel(channel);
    router = new ProxyRouter(
      config,
      new BungeePlatform(getProxy(), getLogger()),
      getDataFolder().toPath().resolve(config.storageDirectory())
    );
    getProxy().getPluginManager().registerListener(this, this);
    tickTask = getProxy().getScheduler().schedule(this, router::tick, 1L, 1L, TimeUnit.SECONDS);
    getLogger().info(
      "WuretaProxy enabled on channel " + channel +
        " with " + getProxy().getServers().size() + " backend(s)"
    );
  }

  @Override
  public void onDisable() {
    if (tickTask != null) {
      tickTask.cancel();
    }
    if (channel != null) {
      getProxy().unregisterChannel(channel);
    }
    if (router != null) {
      router.close();
    }
  }

  @EventHandler
  public void onPluginMessage(PluginMessageEvent event) {
    if (channel == null || !channel.equals(event.getTag())) {
      return;
    }
    event.setCancelled(true);
    if (!(event.getSender() instanceof Server)) {
      return;
    }
    Server server = (Server) event.getSender();
    router.handle(server.getInfo().getName(), event.getData());
  }

  private final class BungeePlatform implements ProxyPlatform {
    private final ProxyServer proxy;
    private final Logger logger;

    BungeePlatform(ProxyServer proxy, Logger logger) {
      this.proxy = proxy;
      this.logger = logger;
    }

    @Override
    public void sendToServer(String serverName, byte[] data) {
      net.md_5.bungee.api.config.ServerInfo target = proxy.getServerInfo(serverName);
      if (target != null) {
        try {
          target.sendData(channel, data);
        } catch (RuntimeException exception) {
          logger.warning("Unable to send cross-server frame to " + serverName + ": " + exception.getMessage());
        }
      }
    }

    @Override
    public void broadcastExcept(String sourceServer, byte[] data) {
      for (net.md_5.bungee.api.config.ServerInfo target : proxy.getServers().values()) {
        if (!target.getName().equals(sourceServer)) {
          try {
            target.sendData(channel, data);
          } catch (RuntimeException exception) {
            logger.warning("Unable to broadcast cross-server frame to " + target.getName() + ": " + exception.getMessage());
          }
        }
      }
    }

    @Override
    public Set<String> serverNames() {
      return new HashSet<>(proxy.getServers().keySet());
    }

    @Override
    public void log(String message) {
      logger.info(message);
    }

    @Override
    public void warn(String message) {
      logger.warning(message);
    }
  }
}
