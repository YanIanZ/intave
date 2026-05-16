package de.jpx3.intave.test.client;


import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.PositionMoveRotation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Replay {
	private static final double START_TOLERANCE = 0.03;

	int schema = 3;
	String minecraftVersion = "26.1.2";
	long createdAtEpochMillis;
	int syncIntervalTicks = 20;
	Start start;
	List<ReplayFrame> frames = new ArrayList<>();

	static ReplayFrame frame(
		int tick,
		int input,
		int actions,
		Pose previousPose,
		Pose pose,
		boolean syncFrame,
		List<Motion> velocities,
		List<TouchedBlock> addedBlocks,
		List<TouchedBlock> removedBlocks
	) {
		return new ReplayFrame(
			tick,
			input,
			actions,
			pose.yaw - previousPose.yaw,
			pose.pitch - previousPose.pitch,
			syncFrame ? pose : null,
			velocities,
			addedBlocks,
			removedBlocks
		);
	}

	public static Replay from(File file) throws IOException {
		return ReplayCodec.read(file.toPath());
	}

	static final class Start {
		String dimension;
		PositionMoveRotation posMoveRot;

		Start(String dimension, PositionMoveRotation posMoveRot) {
			this.dimension = dimension;
			this.posMoveRot = posMoveRot;
		}

		PositionMoveRotation pose() {
			return posMoveRot;
		}
	}

	static final class Pose {
		private final double x;
		private final double y;
		private final double z;
		private final float yaw;
		private final float pitch;

		Pose(double x, double y, double z, float yaw, float pitch) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.pitch = pitch;
		}

		public double x() {
			return x;
		}

		public double y() {
			return y;
		}

		public double z() {
			return z;
		}

		public float yaw() {
			return yaw;
		}

		public float pitch() {
			return pitch;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj == null || obj.getClass() != this.getClass()) return false;
			Pose that = (Pose) obj;
			return Double.doubleToLongBits(this.x) == Double.doubleToLongBits(that.x) &&
				Double.doubleToLongBits(this.y) == Double.doubleToLongBits(that.y) &&
				Double.doubleToLongBits(this.z) == Double.doubleToLongBits(that.z) &&
				Float.floatToIntBits(this.yaw) == Float.floatToIntBits(that.yaw) &&
				Float.floatToIntBits(this.pitch) == Float.floatToIntBits(that.pitch);
		}

		@Override
		public int hashCode() {
			return Objects.hash(x, y, z, yaw, pitch);
		}

		@Override
		public String toString() {
			return "Pose[" +
				"x=" + x + ", " +
				"y=" + y + ", " +
				"z=" + z + ", " +
				"yaw=" + yaw + ", " +
				"pitch=" + pitch + ']';
		}

	}
}

