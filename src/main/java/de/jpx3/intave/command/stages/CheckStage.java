package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.SubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class CheckStage extends CommandStage {
  private static CheckStage singletonInstance;

  private CheckStage() {
    super(BaseStage.singletonInstance(), "check");
  }

  @SubCommand(
    selectors = "disable",
    usage = "<check-name>",
    description = "Temporarily disable a check",
    permission = "intave.command.check"
  )
  public void disableCheck(CommandSender sender, Check check) {
    if (check instanceof Physics || check instanceof Timer) {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "The " + check.name() + " check cannot be disabled");
      return;
    }
    if (!IntavePlugin.singletonInstance().checks().setEnabled(check, false)) {
      sender.sendMessage(IntavePlugin.prefix() + check.name() + " is already disabled");
      return;
    }
    sender.sendMessage(IntavePlugin.prefix() + check.name() + " is now " + ChatColor.RED + "disabled");
    sender.sendMessage(IntavePlugin.prefix() + ChatColor.YELLOW + "Warning: This deactivation is not permanent and will be reset when " + de.jpx3.intave.IntaveBuildConfig.BRAND_NAME + " restarts.");
  }

  @SubCommand(
    selectors = "enable",
    usage = "<check-name>",
    description = "Enable a check",
    permission = "intave.command.check"
  )
  public void enableCheck(CommandSender sender, Check check) {
    if (!IntavePlugin.singletonInstance().checks().setEnabled(check, true)) {
      sender.sendMessage(IntavePlugin.prefix() + check.name() + " is already enabled");
      return;
    }
    sender.sendMessage(IntavePlugin.prefix() + check.name() + " is now " + ChatColor.GREEN + "enabled");
  }

  public static CheckStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new CheckStage();
    }
    return singletonInstance;
  }
}
