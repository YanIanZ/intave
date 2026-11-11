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

package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.user.User;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static de.jpx3.intave.math.MathHelper.formatDouble;

public final class PerformanceStage extends CommandStage {
  private static PerformanceStage singletonInstance;
  private final IntavePlugin plugin;

  private PerformanceStage() {
    super(BaseStage.singletonInstance(), "performance");
    plugin = IntavePlugin.singletonInstance();
  }

  @SubCommand(
    selectors = "timings",
    usage = "",
    description = "Output timing data",
    permission = "intave.command.diagnostics"
  )
  public void timingsCommand(User user, @Optional String[] specifier) {
    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";
    Player player = user.player();
    user.sendMessage(ChatColor.RED + "Loading timings...");
    List<Timing> timings = new ArrayList<>(Timings.timingPool());
    timings.sort(Timing::compareTo);

    timings.forEach(timing -> {
      if (timing.isPacketEventTiming() || timing.isBukkitEventTiming()) {
        return;
      }
      boolean suspicious = timing.averageCallDurationInMillis() > 0.5d;
      boolean dumping = timing.averageCallDurationInMillis() > 1.5d;
      String message;
      ChatColor outputColor = suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN;
      ChatColor p99Color = timing.p99CallDurationInMillis() > 1.5d ? ChatColor.RED : ChatColor.GREEN;
      message = String.format(
        "%s: %s::%s%s (%s&f %s/c, %s&f ns p99)",
        timing.coloredName(),
        timing.recordedCalls(),
        formatDouble(timing.totalDurationMillis() / 1000d, 2),
        "s",
        outputColor + largeNumberFormat((long) timing.averageCallDurationInNanos()),
        "ns",
        p99Color + largeNumberFormat(timing.p99CallDurationInNanos())
      );
      if (!fullSpecifier.isEmpty() && !"ns".equals(fullSpecifier) && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
        message = IntavePlugin.defaultColor() + ChatColor.stripColor(message);
      }
      TimingChatOutput.sendSelectableTiming(player, timing, message, "/" + de.jpx3.intave.IntaveBuildConfig.BRAND_COMMAND + " performance histogram ");
    });
  }

  @SubCommand(
    selectors = "events",
    usage = "",
    description = "Output timing data",
    permission = "intave.command.diagnostics"
  )
  public void eventTimingsCommand(User user, @Optional String[] specifier) {
    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";
    Player player = user.player();
    user.sendMessage(ChatColor.RED + "Loading timings...");

    List<Timing> timings = new ArrayList<>(Timings.timingPool());
    timings.sort(Timing::compareTo);

    timings.forEach(timing -> {
      if (!timing.isBukkitEventTiming()) return;
      boolean suspicious = timing.averageCallDurationInMillis() > 0.5d;
      boolean dumping = timing.averageCallDurationInMillis() > 1.5d;
      String message = String.format(
        "%s: %s::%sms (%s ms/c, p99 %sms)",
        timing.coloredName(),
        timing.recordedCalls(),
        formatDouble(timing.totalDurationMillis(), 4),
        (suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN) + "" +
          formatDouble(timing.averageCallDurationInMillis(), 8)
          + ChatColor.WHITE,
        formatDouble(timing.p99CallDurationInMillis(), 8)
      );
      if (!fullSpecifier.isEmpty() && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
        message = IntavePlugin.defaultColor() + ChatColor.stripColor(message);
      }
      TimingChatOutput.sendSelectableTiming(player, timing, message, "/" + de.jpx3.intave.IntaveBuildConfig.BRAND_COMMAND + " performance histogram ");
    });

  }

  @SubCommand(
    selectors = "packets",
    usage = "",
    description = "Output timing data",
    permission = "intave.command.diagnostics"
  )
  public void packetTimingsCommand(User user, @Optional String[] specifier) {
    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";

    Player player = user.player();
    user.sendMessage(ChatColor.RED + "Loading timings...");

    List<Timing> timings = new ArrayList<>(Timings.timingPool());
    timings.sort(Timing::compareTo);

    timings.forEach(timing -> {
      if (!timing.isPacketEventTiming()) return;
      boolean suspicious = timing.averageCallDurationInMillis() > 0.5d;
      boolean dumping = timing.averageCallDurationInMillis() > 1.5d;
      String message = String.format(
        "%s: %s::%sms (%s&f ms/c, p99 %sms)",
        timing.coloredName(),
        timing.recordedCalls(),
        formatDouble(timing.totalDurationMillis(), 4),
        (suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN) + "" +
          formatDouble(timing.averageCallDurationInMillis(), 8),
        formatDouble(timing.p99CallDurationInMillis(), 8)
      );
      if (!fullSpecifier.isEmpty() && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
        message = IntavePlugin.defaultColor() + ChatColor.stripColor(message);
      }
      TimingChatOutput.sendSelectableTiming(player, timing, message, "/" + de.jpx3.intave.IntaveBuildConfig.BRAND_COMMAND + " performance histogram ");
    });

  }

  @SubCommand(
    selectors = "histogram",
    usage = "<timing>",
    description = "Output the duration histogram for a timing",
    permission = "intave.command.diagnostics",
    hideInHelp = true
  )
  public void histogramCommand(User user, String[] timingName) {
    TimingChatOutput.sendHistogram(user, timingName);
  }

  public static String largeNumberFormat(double value) {
    return RootStage.largeNumberFormat(value);
  }

  public static PerformanceStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new PerformanceStage();
    }
    return singletonInstance;
  }
}
