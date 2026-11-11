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
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.wureta.proxy.protocol.FrameType;
import de.jpx3.wureta.proxy.protocol.NetworkFrame;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkAlertRelay {
  private final NetworkSync network;
  private final NetworkSettings settings;
  private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();

  NetworkAlertRelay(NetworkSync network, NetworkSettings settings) {
    this.network = network;
    this.settings = settings;
  }

  public void publish(ViolationContext context) {
    if (!settings.syncAlerts()) {
      return;
    }
    Violation violation = context.violation();
    Player player = violation.findPlayer().orElse(null);
    if (player == null) {
      return;
    }
    String checkName = violation.check().name().toLowerCase(Locale.ROOT);
    String key = player.getUniqueId() + ":" + checkName;
    long now = System.currentTimeMillis();
    Long previous = lastAlert.get(key);
    if (previous != null && now - previous < settings.alertRateLimitMillis()) {
      return;
    }
    lastAlert.put(key, now);
    if (lastAlert.size() > 4096) {
      lastAlert.clear();
    }

    User user = UserRepository.userOf(player);
    NetworkPayloads.Alert alert = new NetworkPayloads.Alert(
      player.getUniqueId(),
      player.getName(),
      checkName,
      violation.message(),
      violation.details(),
      context.violationLevelBefore(),
      context.violationLevelAfter(),
      user.trustFactor().name()
    );
    network.broadcast(FrameType.ALERT, UUID.randomUUID(), player.getUniqueId(), alert.encode());
  }

  public void handle(NetworkFrame frame) {
    if (!settings.syncAlerts() || frame.source().equals(settings.serverId())) {
      return;
    }
    NetworkPayloads.Alert alert;
    try {
      alert = NetworkPayloads.Alert.decode(frame.payload());
    } catch (RuntimeException exception) {
      IntaveLogger.logger().warn("Invalid cross-server alert: " + exception.getMessage());
      return;
    }
    String message = format(alert, frame.source());
    deliver(MessageChannel.VIOLATION_FINE, message);
  }

  private void deliver(MessageChannel channel, String message) {
    Collection<Player> receivers = MessageChannelSubscriptions.receiverOf(channel);
    for (Player receiver : receivers) {
      User user = UserRepository.userOf(receiver);
      if (!user.receives(channel)) {
        continue;
      }
      user.sendMessage(message);
    }
  }

  private String format(NetworkPayloads.Alert alert, String source) {
    StringBuilder message = new StringBuilder();
    message.append(IntavePlugin.prefix())
      .append(ChatColor.DARK_GRAY).append('[').append(source).append("] ")
      .append(ChatColor.RED).append(alert.playerName())
      .append(ChatColor.GRAY).append(" failed ")
      .append(ChatColor.RED).append(alert.checkName());
    if (alert.details() != null && !alert.details().isEmpty()) {
      message.append(ChatColor.GRAY).append(" (").append(ChatColor.WHITE)
        .append(alert.details()).append(ChatColor.GRAY).append(')');
    }
    message.append(ChatColor.GRAY).append(" VL ")
      .append(ChatColor.RED).append(formatDouble(alert.violationLevelAfter()));
    return message.toString();
  }

  private static String formatDouble(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}
