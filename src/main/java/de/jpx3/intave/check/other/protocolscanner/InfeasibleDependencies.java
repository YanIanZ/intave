package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ALL;

/**
 * Rejects packet orderings the vanilla client can never produce.
 *
 * <p>The DOT models a <em>single</em> tick. The client runs the tick 20×/s,
 * so a stateless online check must also accept the wrap-around: the last
 * packet of one tick is immediately followed by the first packet of the
 * next. We close that loop here in code (every terminal packet -&gt; every
 * start packet) so the model always stays consistent with whatever
 * single-tick graph the analyzer produced, without having to re-export a
 * separate "looped" file. Without this, every tick boundary (e.g. a
 * movement {@code C03} followed by a server-triggered {@code C0F}
 * transaction reply at the start of the next tick) would be falsely
 * flagged.
 *
 * <p>Fails open: if the graph resource is missing, the check
 * disables itself rather than crash or false-flag.
 */
public final class InfeasibleDependencies extends PlayerCheckPart<ProtocolScanner> {

  /**
   * Classpath location of the analyzer-exported single-tick graph.
   */
  private static final String GRAPH_RESOURCE = "infeasabledependencies/tick-packets.dot";

  /**
   * DOT short label (as produced by the analyzer) -> ProtocolLib packet
   * type. Only the packet types the tick analysis can reach appear here;
   * anything not in this table is simply not modelled and is ignored at
   * runtime (it neither flags nor breaks the transition chain).
   */
  private static final Map<String, PacketType> LABEL_TO_TYPE = new HashMap<>();

  static {
    LABEL_TO_TYPE.put("C00PacketKeepAlive", PacketType.Play.Client.KEEP_ALIVE);
    LABEL_TO_TYPE.put("C01PacketChatMessage", PacketType.Play.Client.CHAT);
    LABEL_TO_TYPE.put("C02PacketUseEntity", PacketType.Play.Client.USE_ENTITY);
    LABEL_TO_TYPE.put("C03PacketPlayer", PacketType.Play.Client.FLYING);
    LABEL_TO_TYPE.put("C03PacketPlayer$C04PacketPlayerPosition", PacketType.Play.Client.POSITION);
    LABEL_TO_TYPE.put("C03PacketPlayer$C05PacketPlayerLook", PacketType.Play.Client.LOOK);
    LABEL_TO_TYPE.put("C03PacketPlayer$C06PacketPlayerPosLook", PacketType.Play.Client.POSITION_LOOK);
    LABEL_TO_TYPE.put("C07PacketPlayerDigging", PacketType.Play.Client.BLOCK_DIG);
    LABEL_TO_TYPE.put("C08PacketPlayerBlockPlacement", PacketType.Play.Client.BLOCK_PLACE);
    LABEL_TO_TYPE.put("C09PacketHeldItemChange", PacketType.Play.Client.HELD_ITEM_SLOT);
    LABEL_TO_TYPE.put("C0APacketAnimation", PacketType.Play.Client.ARM_ANIMATION);
    LABEL_TO_TYPE.put("C0BPacketEntityAction", PacketType.Play.Client.ENTITY_ACTION);
    LABEL_TO_TYPE.put("C0CPacketInput", PacketType.Play.Client.STEER_VEHICLE);
    LABEL_TO_TYPE.put("C0DPacketCloseWindow", PacketType.Play.Client.CLOSE_WINDOW);
    LABEL_TO_TYPE.put("C0FPacketConfirmTransaction", PacketType.Play.Client.TRANSACTION);
    LABEL_TO_TYPE.put("C10PacketCreativeInventoryAction", PacketType.Play.Client.SET_CREATIVE_SLOT);
    LABEL_TO_TYPE.put("C11PacketEnchantItem", PacketType.Play.Client.ENCHANT_ITEM);
    LABEL_TO_TYPE.put("C12PacketUpdateSign", PacketType.Play.Client.UPDATE_SIGN);
    LABEL_TO_TYPE.put("C14PacketTabComplete", PacketType.Play.Client.TAB_COMPLETE);
    LABEL_TO_TYPE.put("C15PacketClientSettings", PacketType.Play.Client.SETTINGS);
    LABEL_TO_TYPE.put("C16PacketClientStatus", PacketType.Play.Client.CLIENT_COMMAND);
    LABEL_TO_TYPE.put("C17PacketCustomPayload", PacketType.Play.Client.CUSTOM_PAYLOAD);
    LABEL_TO_TYPE.put("C18PacketSpectate", PacketType.Play.Client.SPECTATE);
  }

  /**
   * Every packet type the tick analysis models (hot-path membership).
   */
  private static final Set<PacketType> MODELLED =
    new HashSet<>(LABEL_TO_TYPE.values());

  // Parsed, loop-closed model. Immutable after static init.
  private static final Set<PacketType> START_PACKETS = new HashSet<>();
  private static final Map<PacketType, Set<PacketType>> ALLOWED = new HashMap<>();

  // NOTE: these patterns must be declared BEFORE the static {} block that
  // calls loadGraph(), since static fields initialise in textual order.
  private static final Pattern NODE =
    Pattern.compile("\\s*(p\\d+)\\s*\\[label=\"([^\"]+)\"([^\\]]*)\\]");
  private static final Pattern EDGE =
    Pattern.compile("\\s*(p\\d+)\\s*->\\s*(p\\d+)");
  private static final Pattern START_EDGE =
    Pattern.compile("\\s*__start__\\s*->\\s*(p\\d+)");

  private static final boolean GRAPH_LOADED;

  static {
    boolean loaded = false;
    try {
      loaded = loadGraph();
    } catch (Throwable throwable) {
      // Fail open — never let a bad resource take the server down.
      log("InfeasibleDependencies: failed to load " + GRAPH_RESOURCE
        + " (" + throwable + ") — check disabled");
    }
    GRAPH_LOADED = loaded;
    if (loaded) {
      int edges = 0;
      for (Set<PacketType> t : ALLOWED.values()) {
        edges += t.size();
      }
      log("InfeasibleDependencies: loaded " + GRAPH_RESOURCE + " ("
        + ALLOWED.size() + " packet nodes, " + edges + " edges, "
        + START_PACKETS.size() + " start packets) — check active");
    } else {
      log("InfeasibleDependencies: " + GRAPH_RESOURCE
        + " unavailable/empty — check disabled (no violations emitted)");
    }
  }

  private static void log(String message) {
    try {
      IntaveLogger.logger().info(message);
    } catch (Throwable ignored) {
      System.out.println("[Intave] " + message);
    }
  }

  /**
   * Read the bundled DOT, trying several class loaders. Intave ships as a
   * shadow jar with custom class loading, so we don't rely on a single
   * lookup strategy.
   */
  private static List<String> readResourceLines() {
    ClassLoader[] loaders = {
      InfeasibleDependencies.class.getClassLoader(),
      Thread.currentThread().getContextClassLoader(),
      ClassLoader.getSystemClassLoader()
    };
    for (ClassLoader loader : loaders) {
      if (loader == null) {
        continue;
      }
      InputStream in = loader.getResourceAsStream(GRAPH_RESOURCE);
      if (in == null) {
        continue;
      }
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(in, StandardCharsets.UTF_8))) {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
        if (!lines.isEmpty()) {
          return lines;
        }
      } catch (Exception ignored) {
        // try the next loader
      }
    }
    // Last resort: class-relative lookup (leading slash = jar root).
    try (InputStream in =
           InfeasibleDependencies.class.getResourceAsStream("/" + GRAPH_RESOURCE)) {
      if (in != null) {
        BufferedReader reader = new BufferedReader(
          new InputStreamReader(in, StandardCharsets.UTF_8));
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
        if (!lines.isEmpty()) {
          return lines;
        }
      }
    } catch (Exception ignored) {
      // fall through to failure
    }
    return null;
  }

  private static boolean loadGraph() {
    List<String> lines = readResourceLines();
    if (lines == null || lines.isEmpty()) {
      return false;
    }

    // node id ("p0", ...) -> packet type, plus terminal-node ids.
    Map<String, PacketType> nodeType = new HashMap<>();
    Set<String> terminalIds = new HashSet<>();
    Set<String> startIds = new HashSet<>();
    // raw edges deferred until all nodes are known.
    Map<String, Set<String>> rawEdges = new HashMap<>();

    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.startsWith("__start__")) {
        Matcher m = START_EDGE.matcher(trimmed);
        if (m.find()) {
          startIds.add(m.group(1));
        }
        continue;
      }
      Matcher node = NODE.matcher(trimmed);
      if (node.find()) {
        PacketType type = LABEL_TO_TYPE.get(node.group(2));
        if (type != null) {
          nodeType.put(node.group(1), type);
          if (node.group(3).contains("peripheries=2")) {
            terminalIds.add(node.group(1));
          }
        }
        continue;
      }
      Matcher edge = EDGE.matcher(trimmed);
      if (edge.find()) {
        rawEdges.computeIfAbsent(edge.group(1), k -> new HashSet<>())
          .add(edge.group(2));
      }
    }

    if (nodeType.isEmpty()) {
      return false;
    }

    for (String startId : startIds) {
      PacketType type = nodeType.get(startId);
      if (type != null) {
        START_PACKETS.add(type);
      }
    }

    // Intra-tick edges from the graph.
    for (Map.Entry<String, Set<String>> e : rawEdges.entrySet()) {
      PacketType from = nodeType.get(e.getKey());
      if (from == null) {
        continue;
      }
      Set<PacketType> targets =
        ALLOWED.computeIfAbsent(from, k -> new HashSet<>());
      for (String toId : e.getValue()) {
        PacketType to = nodeType.get(toId);
        if (to != null) {
          targets.add(to);
        }
      }
    }

    // Close the tick loop: the last packet of a tick is immediately
    // followed by the first packet of the next tick. Every terminal
    // packet may therefore be followed by every start packet.
    for (String terminalId : terminalIds) {
      PacketType from = nodeType.get(terminalId);
      if (from == null) {
        continue;
      }
      ALLOWED.computeIfAbsent(from, k -> new HashSet<>())
        .addAll(START_PACKETS);
    }

    return !ALLOWED.isEmpty() && !START_PACKETS.isEmpty();
  }

  public static boolean canTransition(PacketType from, PacketType to) {
    Set<PacketType> targets = ALLOWED.get(from);
    return targets != null && targets.contains(to);
  }

  private final int vl;
  // Last modelled packet for this player; null = no tick context yet
  // (start of connection, or only unmodelled packets seen so far).
  private PacketType currentState = null;

  public InfeasibleDependencies(User user, ProtocolScanner parent) {
    super(user, parent);
    this.vl = parent.configuration().settings().intBy(
      "id-vl",
      parent.configuration().settings().intBy(
        "check_infeasible_dependencies_vl", 1));
  }

  @Override
  public boolean enabled() {
    return super.enabled() && GRAPH_LOADED;
  }

  @PacketSubscription(
    packetsIn = ALL
  )
  public void on(PacketEvent event) {
    // The packet-subscription linker does NOT consult enabled(); it
    // dispatches purely by reflection. So we must gate here ourselves —
    // otherwise a failed graph load makes every modelled transition
    // "infeasible" and spams violations.
    if (!GRAPH_LOADED) {
      return;
    }

    PacketType type = event.getPacketType();
    if (!MODELLED.contains(type)) {
      // Not modelled by the tick analysis — ignore it entirely so it
      // neither flags nor breaks the chain across surrounding packets.
      return;
    }

    Player player = event.getPlayer();
    if (currentState != null && !canTransition(currentState, type)) {
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player)
        .withMessage("infeasible packet order")
        .withDetails("from " + currentState.name() + " to " + type.name())
        .withVL(vl)
        .build();
      Modules.violationProcessor().processViolation(violation);
//      player.sendMessage("infeasible packet order: from " + currentState.name() + " to " + type.name());
    }

    currentState = type;
  }
}