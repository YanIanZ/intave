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

package de.jpx3.wureta.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import de.jpx3.wureta.proxy.protocol.ProxyConfig;
import de.jpx3.wureta.proxy.protocol.ProxyPlatform;
import de.jpx3.wureta.proxy.protocol.ProxyRouter;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public final class WuretaVelocityPlugin {
  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private ProxyRouter router;
  private MinecraftChannelIdentifier channel;
  private ScheduledTask tickTask;

  @Inject
  public WuretaVelocityPlugin(
    ProxyServer server, Logger logger, @DataDirectory Path dataDirectory
  ) {
    this.server = server;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  @Subscribe
  public void onProxyInitialize(ProxyInitializeEvent event) {
    ProxyConfig config = ProxyConfig.load(dataDirectory.resolve("wureta-proxy.properties"));
    if (config.secret().isEmpty()) {
      logger.warn("WuretaProxy is running without a shared secret; set one in wureta-proxy.properties");
    }
    channel = channelOf(config.channel());
    server.getChannelRegistrar().register(channel);
    router = new ProxyRouter(config, new VelocityPlatform(), dataDirectory.resolve(config.storageDirectory()));
    tickTask = server.getScheduler()
      .buildTask(this, router::tick)
      .repeat(Duration.ofSeconds(1))
      .schedule();
    logger.info(
      "WuretaProxy enabled on channel " + config.channel() +
        " with " + server.getAllServers().size() + " backend(s)"
    );
  }

  @Subscribe
  public void onProxyShutdown(ProxyShutdownEvent event) {
    if (tickTask != null) {
      tickTask.cancel();
    }
    if (channel != null) {
      server.getChannelRegistrar().unregister(channel);
    }
    if (router != null) {
      router.close();
    }
  }

  @Subscribe
  public void onPluginMessage(PluginMessageEvent event) {
    if (channel == null || !event.getIdentifier().equals(channel)) {
      return;
    }
    event.setResult(PluginMessageEvent.ForwardResult.handled());
    if (!(event.getSource() instanceof ServerConnection)) {
      return;
    }
    ServerConnection connection = (ServerConnection) event.getSource();
    router.handle(connection.getServerInfo().getName(), event.getData());
  }

  private static MinecraftChannelIdentifier channelOf(String channel) {
    int split = channel.indexOf(':');
    if (split < 1 || split == channel.length() - 1) {
      throw new IllegalArgumentException("Invalid channel " + channel);
    }
    return MinecraftChannelIdentifier.create(channel.substring(0, split), channel.substring(split + 1));
  }

  private final class VelocityPlatform implements ProxyPlatform {
    @Override
    public void sendToServer(String serverName, byte[] data) {
      server.getServer(serverName).ifPresent(target -> {
        try {
          target.sendPluginMessage(channel, data);
        } catch (RuntimeException exception) {
          logger.warn("Unable to send cross-server frame to " + serverName + ": " + exception.getMessage());
        }
      });
    }

    @Override
    public void broadcastExcept(String sourceServer, byte[] data) {
      for (RegisteredServer target : server.getAllServers()) {
        String name = target.getServerInfo().getName();
        if (!name.equals(sourceServer)) {
          try {
            target.sendPluginMessage(channel, data);
          } catch (RuntimeException exception) {
            logger.warn("Unable to broadcast cross-server frame to " + name + ": " + exception.getMessage());
          }
        }
      }
    }

    @Override
    public Set<String> serverNames() {
      Set<String> names = new HashSet<>();
      for (RegisteredServer target : server.getAllServers()) {
        names.add(target.getServerInfo().getName());
      }
      return names;
    }

    @Override
    public void log(String message) {
      logger.info(message);
    }

    @Override
    public void warn(String message) {
      logger.warn(message);
    }
  }
}
