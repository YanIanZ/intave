package de.jpx3.intave.block.shape;

import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static de.jpx3.intave.share.Direction.Axis.*;
import static org.junit.jupiter.api.Assertions.*;

final class VoxelShapeTest {
	private static final double EPSILON = 1.0E-7;

	@Test
	void originBoxExposesBoundsAndElementaryBox() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.25, 0.0, 0.5, 0.75, 1.0, 1.0);

		assertEquals(0.25, shape.min(X_AXIS), EPSILON);
		assertEquals(0.75, shape.max(X_AXIS), EPSILON);
		assertEquals(0.0, shape.min(Y_AXIS), EPSILON);
		assertEquals(1.0, shape.max(Y_AXIS), EPSILON);
		assertEquals(0.5, shape.min(Z_AXIS), EPSILON);
		assertEquals(1.0, shape.max(Z_AXIS), EPSILON);
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.25, 0.0, 0.5, 0.75, 1.0, 1.0)),
			shape.elementaryBoxes()
		);
		assertEquals(BoundingBox.fromBounds(0.25, 0.0, 0.5, 0.75, 1.0, 1.0), shape.outline());
		assertFalse(shape.isEmpty());
		assertFalse(shape.isCubic());
	}

	@Test
	void fullOriginCubeIsCubic() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

		assertTrue(shape.isCubic());
		assertEquals(1.0, shape.max(X_AXIS), EPSILON);
		assertEquals(1.0, shape.max(Y_AXIS), EPSILON);
		assertEquals(1.0, shape.max(Z_AXIS), EPSILON);
	}

	@Test
	void contextualizedShapeTranslatesBoundsAndBoxes() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.25, 0.0, 0.5, 0.75, 1.0, 1.0)
			.contextualized(3, 4, 5);

		assertEquals(3.25, shape.min(X_AXIS), EPSILON);
		assertEquals(3.75, shape.max(X_AXIS), EPSILON);
		assertEquals(4.0, shape.min(Y_AXIS), EPSILON);
		assertEquals(5.0, shape.max(Y_AXIS), EPSILON);
		assertEquals(5.5, shape.min(Z_AXIS), EPSILON);
		assertEquals(6.0, shape.max(Z_AXIS), EPSILON);
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(3.25, 4.0, 5.5, 3.75, 5.0, 6.0)),
			shape.elementaryBoxes()
		);

		BlockShape normalized = shape.normalized(3, 4, 5);
		assertInstanceOf(VoxelShape.class, normalized);
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.25, 0.0, 0.5, 0.75, 1.0, 1.0)),
			normalized.elementaryBoxes()
		);
	}

	@Test
	void booleanOperationsProduceExpectedVolumes() {
		VoxelShape leftHalf = VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 0.5, 1.0, 1.0);
		VoxelShape rightHalf = VoxelShape.fromOriginBox(0.5, 0.0, 0.0, 1.0, 1.0, 1.0);
		VoxelShape full = leftHalf.combineWith(rightHalf).optimized();

		assertTrue(full.isCubic());
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
			full.elementaryBoxes()
		);
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.0, 0.0, 0.0, 0.5, 1.0, 1.0)),
			full.subtract(rightHalf).optimized().elementaryBoxes()
		);
		assertEquals(
			Collections.singletonList(BoundingBox.fromBounds(0.5, 0.0, 0.0, 1.0, 1.0, 1.0)),
			full.intersectWith(rightHalf).optimized().elementaryBoxes()
		);
	}

	@Test
	void intersectsWithDoesNotTreatEmptySectorsAsFilled() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.0, 0.0, 0.5, 1.0, 1.0, 1.0);

		assertFalse(shape.intersectsWith(BoundingBox.fromBounds(0.25, 0.25, 0.1, 0.75, 0.75, 0.4)));
		assertTrue(shape.intersectsWith(BoundingBox.fromBounds(0.25, 0.25, 0.75, 0.75, 0.75, 0.9)));
	}

	@Test
	void allowedOffsetClampsAgainstFilledVoxel() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
		BoundingBox leftEntity = BoundingBox.fromBounds(-0.5, 0.25, 0.25, -0.25, 0.75, 0.75);
		BoundingBox rightEntity = BoundingBox.fromBounds(1.25, 0.25, 0.25, 1.5, 0.75, 0.75);

		assertEquals(0.25, shape.allowedOffset(X_AXIS, leftEntity, 1.0), EPSILON);
		assertEquals(-0.25, shape.allowedOffset(X_AXIS, rightEntity, -1.0), EPSILON);
		assertEquals(1.0, shape.allowedOffset(X_AXIS, leftEntity.offset(0.0, 1.5, 0.0), 1.0), EPSILON);
	}

	@Test
	void raytraceReturnsClosestHit() {
		VoxelShape shape = VoxelShape.fromOriginBox(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);

		BlockRaytrace hit = shape.raytrace(new Position(-1.0, 0.5, 0.5), new Position(1.0, 0.5, 0.5));

		assertNotNull(hit);
		assertEquals(Direction.WEST, hit.direction());
		assertEquals(1.25, hit.lengthOffset(), EPSILON);
		assertNull(shape.raytrace(new Position(-1.0, 0.1, 0.1), new Position(1.0, 0.1, 0.1)));
	}

	@Test
	void streamCodecRoundTripsMultipleBoxes() {
		VoxelShape shape = VoxelShape.fromBoxes(Arrays.asList(
			BoundingBox.fromBounds(0.0, 0.0, 0.0, 0.25, 0.5, 1.0),
			BoundingBox.fromBounds(0.75, 0.5, 0.0, 1.0, 1.0, 1.0)
		));

		VoxelShape decoded = roundTrip(shape);

		assertEquals(shape.elementaryBoxes(), decoded.elementaryBoxes());
		assertEquals(shape.outline(), decoded.outline());
	}

	private static VoxelShape roundTrip(VoxelShape shape) {
		ByteBuf buffer = Unpooled.buffer();
		try {
			VoxelShape.STREAM_CODEC.encode(buffer, shape);
			return VoxelShape.STREAM_CODEC.decode(buffer);
		} finally {
			buffer.release();
		}
	}
}
