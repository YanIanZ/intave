package de.jpx3.intave.test.client;

import de.jpx3.intave.module.violation.Violation;
import org.bukkit.entity.Player;

public interface ClientTestTrap {
	void onMovementFault(Player player);

	void onViolation(Player player, Violation violation);
}
