package de.jpx3.intave.test.client;

import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.module.violation.Violation;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientTestTraps {
	private final static Map<UUID, ClientTestTrap> playerTraps = GarbageCollector.watch(new HashMap<>());

	public static void onMovementFault(
		Player player
	) {
		ClientTestTrap trap = playerTraps.get(player.getUniqueId());
		if (trap != null) {
			trap.onMovementFault(player);
		}
	}

	public static void onAnyViolation(
		Player player, Violation violation
	) {
		ClientTestTrap trap = playerTraps.get(player.getUniqueId());
		if (trap != null) {
			trap.onViolation(player, violation);
		}
	}

	public static void registerTrap(Player player, ClientTestTrap trap) {
		playerTraps.put(player.getUniqueId(), trap);
	}

	public static void unregisterTrap(Player player) {
		playerTraps.remove(player.getUniqueId());
	}
}
