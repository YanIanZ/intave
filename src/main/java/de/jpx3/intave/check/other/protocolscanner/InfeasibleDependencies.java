package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ALL;

public final class InfeasibleDependencies extends PlayerCheckPart<ProtocolScanner> {
  // 23 total nodes (p0 to p22)
  private static final int SIZE = 23;
  private static final boolean[][] ALLOWED = new boolean[SIZE][SIZE];
  private static final boolean[] START_NODES = new boolean[SIZE];
  private static final Map<PacketType, Integer> TYPE_TO_ID = new HashMap<>();

  static {
    TYPE_TO_ID.put(PacketType.Play.Client.KEEP_ALIVE, 0);
    TYPE_TO_ID.put(PacketType.Play.Client.CHAT, 1);
    TYPE_TO_ID.put(PacketType.Play.Client.USE_ENTITY, 2);
    TYPE_TO_ID.put(PacketType.Play.Client.FLYING, 3); // C03PacketPlayer
    TYPE_TO_ID.put(PacketType.Play.Client.POSITION, 4);
    TYPE_TO_ID.put(PacketType.Play.Client.LOOK, 5);
    TYPE_TO_ID.put(PacketType.Play.Client.POSITION_LOOK, 6);
    TYPE_TO_ID.put(PacketType.Play.Client.BLOCK_DIG, 7);
    TYPE_TO_ID.put(PacketType.Play.Client.BLOCK_PLACE, 8);
    TYPE_TO_ID.put(PacketType.Play.Client.HELD_ITEM_SLOT, 9);
    TYPE_TO_ID.put(PacketType.Play.Client.ARM_ANIMATION, 10);
    TYPE_TO_ID.put(PacketType.Play.Client.ENTITY_ACTION, 11);
    TYPE_TO_ID.put(PacketType.Play.Client.STEER_VEHICLE, 12); // C0CPacketInput
    TYPE_TO_ID.put(PacketType.Play.Client.CLOSE_WINDOW, 13);
    TYPE_TO_ID.put(PacketType.Play.Client.TRANSACTION, 14);
    TYPE_TO_ID.put(PacketType.Play.Client.SET_CREATIVE_SLOT, 15);
    TYPE_TO_ID.put(PacketType.Play.Client.ENCHANT_ITEM, 16);
    TYPE_TO_ID.put(PacketType.Play.Client.UPDATE_SIGN, 17);
    TYPE_TO_ID.put(PacketType.Play.Client.TAB_COMPLETE, 18);
    TYPE_TO_ID.put(PacketType.Play.Client.SETTINGS, 19);
    TYPE_TO_ID.put(PacketType.Play.Client.CLIENT_COMMAND, 20); // C16PacketClientStatus
    TYPE_TO_ID.put(PacketType.Play.Client.CUSTOM_PAYLOAD, 21);
    TYPE_TO_ID.put(PacketType.Play.Client.SPECTATE, 22);
  }

  static {
    int[] startConnections = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22};
    for (int id : startConnections) START_NODES[id] = true;

    // Mapping transitions
    allow(0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(2, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 15, 21);
    allow(3, 3, 4, 5, 6, 11, 14, 15, 21);
    allow(4, 3, 4, 5, 6, 11, 14, 15, 21);
    allow(5, 3, 4, 5, 6, 11, 12, 14, 15, 21);
    allow(6, 3, 4, 5, 6, 11, 14, 15, 21);
    allow(7, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 15, 17, 19, 20, 21);
    allow(8, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 15, 21);
    allow(9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(10, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 15, 21);
    allow(11, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 15, 17, 19, 20, 21);
    allow(12, 3, 4, 5, 6, 11, 15, 21);
    allow(13, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(14, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 17, 18, 19, 20, 21, 22);
    allow(15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(16, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(17, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(18, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 17, 18, 19, 20, 21, 22);
    allow(19, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(20, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(21, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
    allow(22, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 19, 20, 21, 22);
  }

  private static void allow(int from, int... targets) {
    for (int to : targets) {
      ALLOWED[from][to] = true;
    }
  }

  public static int getIdFromType(PacketType type) {
    return TYPE_TO_ID.getOrDefault(type, -1);
  }

  public static String typeNameFromId(int id) {
    for (Map.Entry<PacketType, Integer> entry : TYPE_TO_ID.entrySet()) {
      if (entry.getValue() == id) {
        return entry.getKey().name();
      }
    }
    return "unknown";
  }

  public static boolean canTransition(int fromId, int toId) {
    return ALLOWED[fromId][toId];
  }

  private int currentState = -1; // -1 represents __start__

  public InfeasibleDependencies(User user, ProtocolScanner parent) {
    super(user, parent);
  }

  @PacketSubscription(
    packetsIn = ALL
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    int transition = getIdFromType(event.getPacketType());
    if (currentState >= 0 && transition >= 0) {
      if (!canTransition(currentState, transition)) {
        Violation violation = Violation.builderFor(ProtocolScanner.class)
          .forPlayer(player).withMessage("infeasible packet order")
          .withDetails("from " + (typeNameFromId(currentState)) + " to " + (typeNameFromId(transition)))
          .withVL(1)
          .build();
        Modules.violationProcessor().processViolation(violation);
      }
    }
    currentState = transition;
  }
}
