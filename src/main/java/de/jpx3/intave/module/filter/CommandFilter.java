package de.jpx3.intave.module.filter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntaveBuildConfig;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CHAT_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TAB_COMPLETE_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.TAB_COMPLETE_OUT;

public final class CommandFilter extends Filter {
  private final boolean separateEnable;
  private final boolean disabled;
  private final boolean hidePluginList;
  private final Map<String, String> redirects = new HashMap<>();

  public CommandFilter(IntavePlugin plugin) {
    super("command");
    separateEnable = plugin.settings().getBoolean("command.hide", true);
    disabled = plugin.settings().getBoolean("command.fix-tab-kicks", false);
    hidePluginList = plugin.settings().getBoolean(
      "command.hide-plugin-list", IntaveBuildConfig.BRAND_STEALTH
    );

    ConfigurationSection reroute = plugin.settings().getConfigurationSection("command.reroute");
    if (reroute != null) {
      reroute.getKeys(false).forEach(key -> redirects.put(key, plugin.settings().getString("command.reroute." + key)));
    }
  }

  @PacketSubscription(
    packetsIn = {
      CHAT_IN, TAB_COMPLETE_IN
    }
  )
  public void receiveChatPacket(PacketEvent event) {
    Player player = event.getPlayer();
    String message = event.getPacket().getStrings().getValues().get(0);

    String trimmedMessage = message.trim().toLowerCase();

    for (Map.Entry<String, String> stringStringEntry : redirects.entrySet()) {
      if (trimmedMessage.startsWith(stringStringEntry.getKey())) {
        // remove the command and replace it with the redirect without regex
        String redirect = stringStringEntry.getValue();
        if (redirect.toLowerCase().contains("root")) {
          continue;
        }
        trimmedMessage = redirect + trimmedMessage.substring(stringStringEntry.getKey().length());
        event.getPacket().getStrings().writeSafely(0, trimmedMessage);
        trimmedMessage = trimmedMessage.trim().toLowerCase();
      }
    }

    boolean permitted = BukkitPermissionCheck.permissionCheck(player, "intave.command");
    if (!permitted && isManagedCommand(trimmedMessage)) {
      event.getPacket().getStrings().writeSafely(
        0, "/" + de.jpx3.intave.IntaveBuildConfig.BRAND_COMMAND + "commandforward"
      );
    }
  }

//  @PacketSubscription(
////    engine = Engine.ASYNC_INTERNAL,
//    packetsOut = {
//      COMMANDS
//    }
//  )
//  public void receiveCommands(PacketEvent event) {
//    Player player = event.getPlayer();
//    PacketContainer packet = event.getPacket();
//    StructureModifier<RootCommandNode> rootModifier = packet.getSpecificModifier(RootCommandNode.class);
//    RootCommandNode<?> rootCommandNode = rootModifier.readSafely(0);
////    player.sendMessage("Removing " + rootCommandNode.getChildren());
////    for (CommandNode<?> child : rootCommandNode.getChildren()) {
////      System.out.println(child.getName() + " -> " + child.getUsageText());
////    }
//    rootCommandNode.removeCommand("iac");
//    rootCommandNode.removeCommand("intave:iac");
//    rootCommandNode.removeCommand("intave");
//    rootCommandNode.removeCommand("intave:intave");
////    rootModifier.write(0, new RootCommandNode());
//    rootModifier.write(0, rootCommandNode);
//  }

  @PacketSubscription(
//    engine = Engine.ASYNC_INTERNAL,
    packetsOut = {
      TAB_COMPLETE_OUT
    }
  )
  public void receiveTabComplete(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    boolean permitted = BukkitPermissionCheck.permissionCheck(player, "intave.command");
    if (permitted) {
      return;
    }
    String[] stuff = packet.getStringArrays().readSafely(0);
    if (stuff != null) {
      List<String> newTabCompletions = Lists.newArrayList();
      Arrays.stream(stuff)
        .filter(string -> !isManagedCommand(string.toLowerCase()))
        .forEach(newTabCompletions::add);
      if (newTabCompletions.size() != stuff.length) {
        packet.getStringArrays().writeSafely(0, newTabCompletions.toArray(new String[0]));
      }
    }
  }

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void on(PlayerCommandPreprocessEvent event) {
    if (!hidePluginList) {
      return;
    }
    Player player = event.getPlayer();
    if (BukkitPermissionCheck.permissionCheck(player, "intave.command")) {
      return;
    }
    if (!isPluginListCommand(event.getMessage())) {
      return;
    }
    event.setCancelled(true);
    String visible = Arrays.stream(Bukkit.getPluginManager().getPlugins())
      .map(Plugin::getName)
      .filter(name -> !name.equalsIgnoreCase(IntaveBuildConfig.BRAND_NAME))
      .sorted(String.CASE_INSENSITIVE_ORDER)
      .collect(Collectors.joining(", "));
    player.sendMessage(ChatColor.GRAY + "Plugins: " + (visible.isEmpty() ? "none" : visible));
  }

  static boolean isPluginListCommand(String message) {
    String token = commandToken(message);
    return token.equalsIgnoreCase("plugins")
      || token.equalsIgnoreCase("pl")
      || token.equalsIgnoreCase("bukkit:plugins")
      || token.equalsIgnoreCase("paper:plugins");
  }

  static boolean isManagedCommand(String message) {
    String token = commandToken(message);
    return token.equalsIgnoreCase(IntaveBuildConfig.BRAND_COMMAND)
      || token.equalsIgnoreCase("wcore")
      || token.equalsIgnoreCase("intave")
      || token.equalsIgnoreCase("iac");
  }

  private static String commandToken(String message) {
    String normalized = message.startsWith("/") ? message.substring(1) : message;
    int space = normalized.indexOf(' ');
    return space < 0 ? normalized : normalized.substring(0, space);
  }

  @Override
  protected boolean enabled() {
    return (super.enabled() || separateEnable) && !disabled;
  }
}
