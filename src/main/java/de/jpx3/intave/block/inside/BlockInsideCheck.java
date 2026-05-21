package de.jpx3.intave.block.inside;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;

import java.util.List;

public interface BlockInsideCheck {
	void applyEffectsFromBlocks(
		User user, SimulationEnvironment environment,
		List<EntityMovement> movements,
		Motion modifiableMotion,
		BoundingBox boundingBox
	);
}
