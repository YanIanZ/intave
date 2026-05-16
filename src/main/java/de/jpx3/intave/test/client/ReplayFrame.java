package de.jpx3.intave.test.client;

import de.jpx3.intave.share.Motion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ReplayFrame {
	private final int tick;
	private final int input;
	private final int actions;
	private final float yawDelta;
	private final float pitchDelta;
	private final Replay.Pose syncPose;
	private final List<Motion> velocities;
	private final List<TouchedBlock> addedBlocks;
	private final List<TouchedBlock> removedBlocks;

	ReplayFrame(int tick, int input, int actions, float yawDelta, float pitchDelta, Replay.Pose syncPose, List<Motion> velocities, List<TouchedBlock> addedBlocks, List<TouchedBlock> removedBlocks) {
		velocities = new ArrayList<>(velocities);
		addedBlocks = new ArrayList<>(addedBlocks);
		removedBlocks = new ArrayList<>(removedBlocks);
		this.tick = tick;
		this.input = input;
		this.actions = actions;
		this.yawDelta = yawDelta;
		this.pitchDelta = pitchDelta;
		this.syncPose = syncPose;
		this.velocities = velocities;
		this.addedBlocks = addedBlocks;
		this.removedBlocks = removedBlocks;
	}

	public int tick() {
		return tick;
	}

	public int input() {
		return input;
	}

	public int actions() {
		return actions;
	}

	public float yawDelta() {
		return yawDelta;
	}

	public float pitchDelta() {
		return pitchDelta;
	}

	public Replay.Pose syncPose() {
		return syncPose;
	}

	public List<Motion> velocities() {
		return velocities;
	}

	public List<TouchedBlock> addedBlocks() {
		return addedBlocks;
	}

	public List<TouchedBlock> removedBlocks() {
		return removedBlocks;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		ReplayFrame that = (ReplayFrame) obj;
		return this.tick == that.tick &&
			this.input == that.input &&
			this.actions == that.actions &&
			Float.floatToIntBits(this.yawDelta) == Float.floatToIntBits(that.yawDelta) &&
			Float.floatToIntBits(this.pitchDelta) == Float.floatToIntBits(that.pitchDelta) &&
			Objects.equals(this.syncPose, that.syncPose) &&
			Objects.equals(this.velocities, that.velocities) &&
			Objects.equals(this.addedBlocks, that.addedBlocks) &&
			Objects.equals(this.removedBlocks, that.removedBlocks);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tick, input, actions, yawDelta, pitchDelta, syncPose, velocities, addedBlocks, removedBlocks);
	}

	@Override
	public String toString() {
		return "Frame[" +
			"tick=" + tick + ", " +
			"input=" + input + ", " +
			"actions=" + actions + ", " +
			"yawDelta=" + yawDelta + ", " +
			"pitchDelta=" + pitchDelta + ", " +
			"syncPose=" + syncPose + ", " +
			"velocities=" + velocities + ", " +
			"addedBlocks=" + addedBlocks + ", " +
			"removedBlocks=" + removedBlocks + ']';
	}

}
