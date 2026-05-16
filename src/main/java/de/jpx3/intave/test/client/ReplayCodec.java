package de.jpx3.intave.test.client;


import com.google.gson.*;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Rotation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ReplayCodec {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final double EPSILON = 1.0E-9;
	private static final String FIELD_SCHEMA = "schema";
	private static final String FIELD_MINECRAFT_VERSION = "minecraftVersion";
	private static final String FIELD_CREATED_AT = "createdAtEpochMillis";
	private static final String FIELD_SYNC_INTERVAL = "syncIntervalTicks";
	private static final String FIELD_START = "start";
	private static final String FIELD_FRAMES = "frames";
	private static final String FIELD_DIMENSION = "dimension";
	private static final String FIELD_X = "x";
	private static final String FIELD_Y = "y";
	private static final String FIELD_Z = "z";
	private static final String FIELD_YAW = "yaw";
	private static final String FIELD_PITCH = "pitch";
	private static final String FIELD_TICK = "t";
	private static final String FIELD_INPUT = "i";
	private static final String FIELD_ACTIONS = "a";
	private static final String FIELD_LOOK = "l";
	private static final String FIELD_SYNC = "s";
	private static final String FIELD_VELOCITY = "v";
	private static final String FIELD_BLOCKS_ADDED = "ba";
	private static final String FIELD_BLOCKS_REMOVED = "br";

	private ReplayCodec() {
	}

	static void write(Path path, Replay replay) throws IOException {
		Files.createDirectories(path.getParent());
		try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			PRETTY_GSON.toJson(encodeReplay(replay), writer);
		}
	}

	static byte[] toBytes(Replay replay) {
		return GSON.toJson(encodeReplay(replay)).getBytes(StandardCharsets.UTF_8);
	}

	static Replay read(Path path) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return decodeReplay(GSON.fromJson(reader, JsonObject.class));
		}
	}

	static Replay read(byte[] bytes) {
		return decodeReplay(GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class));
	}

	private static JsonObject encodeReplay(Replay replay) {
		JsonObject object = new JsonObject();
		object.addProperty(FIELD_SCHEMA, replay.schema);
		if (replay.minecraftVersion != null) {
			object.addProperty(FIELD_MINECRAFT_VERSION, replay.minecraftVersion);
		}
		object.addProperty(FIELD_CREATED_AT, replay.createdAtEpochMillis);
		object.addProperty(FIELD_SYNC_INTERVAL, replay.syncIntervalTicks);
		if (replay.start != null) {
			object.add(FIELD_START, encodeStart(replay.start));
		}
		object.add(FIELD_FRAMES, encodeFrames(replay.frames));
		return object;
	}

	private static Replay decodeReplay(JsonObject object) {
		Replay replay = new Replay();
		if (object == null) {
			return replay;
		}

		replay.schema = intOr(object, FIELD_SCHEMA, replay.schema);
		replay.minecraftVersion = stringOr(object, FIELD_MINECRAFT_VERSION, replay.minecraftVersion);
		replay.createdAtEpochMillis = longOr(object, FIELD_CREATED_AT, replay.createdAtEpochMillis);
		replay.syncIntervalTicks = intOr(object, FIELD_SYNC_INTERVAL, replay.syncIntervalTicks);
		replay.start = decodeStart(object.getAsJsonObject(FIELD_START));
		replay.frames = decodeFrames(object.getAsJsonArray(FIELD_FRAMES));
		return replay;
	}

	private static JsonObject encodeStart(Replay.Start start) {
		JsonObject object = new JsonObject();
		if (start.dimension != null) {
			object.addProperty(FIELD_DIMENSION, start.dimension);
		}
		object.addProperty(FIELD_X, start.posMoveRot.position().getX());
		object.addProperty(FIELD_Y, start.posMoveRot.position().getY());
		object.addProperty(FIELD_Z, start.posMoveRot.position().getZ());
		object.addProperty(FIELD_YAW, start.posMoveRot.rotation().yaw());
		object.addProperty(FIELD_PITCH, start.posMoveRot.rotation().pitch());
		return object;
	}

	private static Replay.Start decodeStart(JsonObject object) {
		if (object == null) {
			return null;
		}
		return new Replay.Start(
			stringOr(object, FIELD_DIMENSION, null),
			new PositionMoveRotation(
				Position.of(
					doubleOr(object, FIELD_X, 0.0),
					doubleOr(object, FIELD_Y, 0.0),
					doubleOr(object, FIELD_Z, 0.0)
				),
				Motion.empty(),
				Rotation.of(
					floatOr(object, FIELD_YAW, 0.0F),
					floatOr(object, FIELD_PITCH, 0.0F)
				)
			)
		);
	}

	private static JsonArray encodeFrames(List<ReplayFrame> frames) {
		JsonArray array = new JsonArray();
		int previousInput = 0;
		for (ReplayFrame frame : frames) {
			array.add(encodeFrame(frame, previousInput));
			previousInput = frame.input();
		}
		return array;
	}

	private static List<ReplayFrame> decodeFrames(JsonArray array) {
		List<ReplayFrame> frames = new ArrayList<>();
		if (array == null) {
			return frames;
		}

		int previousInput = 0;
		for (JsonElement element : array) {
			ReplayFrame frame = decodeFrame(element.getAsJsonObject(), previousInput);
			frames.add(frame);
			previousInput = frame.input();
		}
		return frames;
	}

	private static JsonObject encodeFrame(ReplayFrame frame, int previousInput) {
		JsonObject object = new JsonObject();
		object.addProperty(FIELD_TICK, frame.tick());

		if (frame.input() != previousInput) {
			object.addProperty(FIELD_INPUT, frame.input());
		}
		if (frame.actions() != 0) {
			object.addProperty(FIELD_ACTIONS, frame.actions());
		}
		JsonArray look = deltaArray(frame.yawDelta(), frame.pitchDelta());
		if (look != null) {
			object.add(FIELD_LOOK, look);
		}
		if (frame.syncPose() != null) {
			object.add(FIELD_SYNC, absolutePoseArray(frame.syncPose()));
		}
		if (!frame.velocities().isEmpty()) {
			object.add(FIELD_VELOCITY, encodeVelocities(frame.velocities()));
		}
		if (!frame.addedBlocks().isEmpty()) {
			object.add(FIELD_BLOCKS_ADDED, encodeBlocks(frame.addedBlocks()));
		}
		if (!frame.removedBlocks().isEmpty()) {
			object.add(FIELD_BLOCKS_REMOVED, encodeBlocks(frame.removedBlocks()));
		}

		return object;
	}

	private static ReplayFrame decodeFrame(JsonObject object, int previousInput) {
		int input = object.has(FIELD_INPUT) ? object.get(FIELD_INPUT).getAsInt() : previousInput;
		int actions = object.has(FIELD_ACTIONS) ? object.get(FIELD_ACTIONS).getAsInt() : 0;
		JsonArray look = object.getAsJsonArray(FIELD_LOOK);
		float yawDelta = look == null ? 0.0F : look.get(0).getAsFloat();
		float pitchDelta = look == null ? 0.0F : look.get(1).getAsFloat();
		return new ReplayFrame(
			object.get(FIELD_TICK).getAsInt(),
			input,
			actions,
			yawDelta,
			pitchDelta,
			decodePoseArray(object.getAsJsonArray(FIELD_SYNC)),
			decodeVelocities(object.getAsJsonArray(FIELD_VELOCITY)),
			decodeBlocks(object.getAsJsonArray(FIELD_BLOCKS_ADDED)),
			decodeBlocks(object.getAsJsonArray(FIELD_BLOCKS_REMOVED))
		);
	}

	private static JsonArray absolutePoseArray(Replay.Pose pose) {
		JsonArray array = new JsonArray();
		array.add(new JsonPrimitive(pose.x()));
		array.add(new JsonPrimitive(pose.y()));
		array.add(new JsonPrimitive(pose.z()));
		array.add(new JsonPrimitive(pose.yaw()));
		array.add(new JsonPrimitive(pose.pitch()));
		return array;
	}

	private static Replay.Pose decodePoseArray(JsonArray array) {
		if (array == null) {
			return null;
		}
		return new Replay.Pose(
			array.get(0).getAsDouble(),
			array.get(1).getAsDouble(),
			array.get(2).getAsDouble(),
			array.get(3).getAsFloat(),
			array.get(4).getAsFloat()
		);
	}

	private static JsonArray encodeBlocks(List<TouchedBlock> blocks) {
		JsonArray result = new JsonArray();
		TouchedBlock previous = null;
		for (TouchedBlock block : blocks) {
			JsonArray encoded = new JsonArray();
			encoded.add(new JsonPrimitive(previous == null ? block.x() : block.x() - previous.x()));
			encoded.add(new JsonPrimitive(previous == null ? block.y() : block.y() - previous.y()));
			encoded.add(new JsonPrimitive(previous == null ? block.z() : block.z() - previous.z()));
			encoded.add(new JsonPrimitive(block.block()));
			result.add(encoded);
			previous = block;
		}
		return result;
	}

	private static List<TouchedBlock> decodeBlocks(JsonArray array) {
		List<TouchedBlock> blocks = new ArrayList<>();
		if (array == null) {
			return blocks;
		}

		TouchedBlock previous = null;
		for (JsonElement element : array) {
			JsonArray encoded = element.getAsJsonArray();
			int x = encoded.get(0).getAsInt();
			int y = encoded.get(1).getAsInt();
			int z = encoded.get(2).getAsInt();
			if (previous != null) {
				x += previous.x();
				y += previous.y();
				z += previous.z();
			}
			String blockName = sanitizeBlockName(encoded.get(3).getAsString());
			previous = new TouchedBlock(x, y, z, blockName);
			blocks.add(previous);
		}
		return blocks;
	}

	private static String sanitizeBlockName(String input) {
		return input.replace("Block{", "").replace("}", "");
	}

	private static JsonArray encodeVelocities(List<Motion> velocities) {
		JsonArray result = new JsonArray();
		for (Motion velocity : velocities) {
			JsonArray encoded = new JsonArray();
			encoded.add(new JsonPrimitive(velocity.motionX()));
			encoded.add(new JsonPrimitive(velocity.motionY()));
			encoded.add(new JsonPrimitive(velocity.motionZ()));
			result.add(encoded);
		}
		return result;
	}

	private static List<Motion> decodeVelocities(JsonArray array) {
		List<Motion> velocities = new ArrayList<>();
		if (array == null) {
			return velocities;
		}
		if (array.size() > 0 && array.get(0).isJsonPrimitive()) {
			velocities.add(new Motion(
				array.get(0).getAsDouble(),
				array.get(1).getAsDouble(),
				array.get(2).getAsDouble()
			));
			return velocities;
		}
		for (JsonElement element : array) {
			JsonArray velocity = element.getAsJsonArray();
			velocities.add(new Motion(
				velocity.get(0).getAsDouble(),
				velocity.get(1).getAsDouble(),
				velocity.get(2).getAsDouble()
			));
		}
		return velocities;
	}

	private static JsonArray deltaArray(double... values) {
		boolean changed = false;
		JsonArray array = new JsonArray();
		for (double value : values) {
			if (Math.abs(value) > EPSILON) {
				changed = true;
			}
			array.add(new JsonPrimitive(value));
		}
		return changed ? array : null;
	}

	private static int intOr(JsonObject object, String field, int fallback) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsInt() : fallback;
	}

	private static long longOr(JsonObject object, String field, long fallback) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsLong() : fallback;
	}

	private static double doubleOr(JsonObject object, String field, double fallback) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsDouble() : fallback;
	}

	private static float floatOr(JsonObject object, String field, float fallback) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsFloat() : fallback;
	}

	private static String stringOr(JsonObject object, String field, String fallback) {
		return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : fallback;
	}
}
