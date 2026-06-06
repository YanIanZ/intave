package de.jpx3.intave.block.inside;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class v2111BlockInsideCheck implements BlockInsideCheck {
	@Override
	public void applyEffectsFromBlocks(
		User user, SimulationEnvironment environment, List<EntityMovement> movements, Motion modifiableMotion, BoundingBox boundingBox
	) {
		LongSet visitedBlocks = new LongOpenHashSet();

		for (EntityMovement entityMovement : movements) {
			Position from = entityMovement.from();
			Position to = entityMovement.to();
			Motion move = from.motionTo(to);

			int i = 16;
			if (entityMovement.axisDependentOriginalMovement().isPresent() && move.lengthSquared() > 0.0) {
				for (Direction.Axis axis : Direction.axisStepOrder(entityMovement.axisDependentOriginalMovement().get())) {
					double preCollideMotionPartial = move.partialMotionIn(axis);
					if (preCollideMotionPartial != 0.0) {
						Position positionPartial = from.relative(axis.positive(), preCollideMotionPartial);
						i -= checkInsideBlocks(user, environment, from, positionPartial, modifiableMotion, visitedBlocks, i);
						from = positionPartial;
					}
				}
			} else {
				i -= checkInsideBlocks(user, environment, entityMovement.from(), to, modifiableMotion, visitedBlocks, i);
			}
			if (i <= 0) {
				checkInsideBlocks(user, environment, entityMovement.from(), to, modifiableMotion, visitedBlocks, 1);
			}
		}

		visitedBlocks.clear();
	}

	// 100% pure mojang shitcode
	// this is also very slow (50% of the complete simulation) and usually only ends up resolving just 1-2 blocks
	private int checkInsideBlocks(
		User user,
		SimulationEnvironment environment,
		Position from, Position to,
		Motion motion,
		LongSet visitedBlocks,
		int limit
	) {
		World world = user.player().getWorld();
		Position position = environment.position();
		BoundingBox playerBox = BoundingBox.fromPosition(user, environment, to).shrink(1.0E-5F);
		boolean furtherThanOneBlock = from.distanceSquared(to) > (0.9999900000002526 * 0.9999900000002526);
		AtomicInteger lastCollideIndex = new AtomicInteger();

		NativeVector fromNative = from.toNativeVec();
		NativeVector toNative = to.toNativeVec();
		playerBox.forEachBlockIntersectedBetween(
			fromNative, toNative,
			(cursor, num) -> {
				if (num >= limit) {
					user.player().sendMessage("Reached block check limit of " + limit + ", skipping remaining blocks");
					return false;
				}
				lastCollideIndex.set(num);

				Material material = VolatileBlockAccess.typeAccess(user, cursor);
				if (material == Material.AIR) {
					return true;
				}

				if (visitedBlocks.add(cursor.asLong())) {
//					user.player().sendMessage("Checking block at " + cursor.toBlockPosition() + " with material " + material.name());

//					Fluid fluid = VolatileBlockAccess.fluidAccess(user, cursor);
//					BlockShape fluidShape = fluid.uncachedShapeAt(user, cursor.toBlockPosition());
//					if (fluidShape.isEmpty()) {
//						return true;
//					}
//					BoundingBox fluidShapeBox = fluidShape.boundingBoxes().get(0);
//					NativeVector move = fromNative.subtract(toNative);
//					BoundingBox fromBoundingBox = BoundingBox.fromPosition(user, environment, from);
//					boolean collidedWithFluid = fromBoundingBox.collidesAlongVector(
//						move, Collections.singletonList(fluidShapeBox)
//					);

//					user.player().sendMessage("Collision Candidate at " + cursor.toBlockPosition() + " with material " + material.name() + " and fluid " + fluid + " with shape " + fluidShapeBox + " and move " + move + " and player box " + playerBox);
//					if (!collidedWithFluid) {
//						return true;
//					}

//					user.player().sendMessage(furtherThanOneBlock + " " + playerBox.intersectsWith(cursor) + " " + collidedWithFluid);
					Motion overrideMotion = BlockPhysics.entityInside(
						user, material, cursor.toLocation(world), position,
						motion.motionX, motion.motionY, motion.motionZ,
						furtherThanOneBlock || playerBox.intersectsWith(cursor)
					);
					if (overrideMotion != null) {
						user.player().sendMessage("Collided with " + material.name() + " at " + cursor.toBlockPosition() + " with box " +playerBox.toCompactString() + " with motion delta " + overrideMotion.difference(motion));
						motion.setTo(overrideMotion);
					}
				}
				return true;
			}
		);
		return lastCollideIndex.get() + 1;
	}
}
