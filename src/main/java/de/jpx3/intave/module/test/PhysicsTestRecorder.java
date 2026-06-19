package de.jpx3.intave.module.test;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.module.test.record.TickRange;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.packet.reader.EntityVelocityReader;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DeflaterOutputStream;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static java.nio.file.StandardOpenOption.CREATE;

public final class PhysicsTestRecorder extends Module {
	private final UserLocal<AtomicBoolean> recording = UserLocal.withInitial(new AtomicBoolean(false));
	private final UserLocal<MovementRecording> recordingData = UserLocal.withInitial(MovementRecording::create);

	@PacketSubscription(
		packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK}
	)
	public void on(
		User user, PlayerMoveReader reader
	) {
		if (isRecording(user)) {
			Position position = reader.position();
			Rotation rotation = reader.rotation();
			BoundingBox boundingBox = user.meta().movement().boundingBox();
			recordingData.get(user).insertFrame(
				boundingBox,
				position, rotation,
				user.blockCache()
			);
		}
	}

	private final UserLocal<AtomicLong> lastVelocityStart = UserLocal.withInitial(() -> new AtomicLong(0));

	@PacketSubscription(
		packetsOut = {PacketId.Server.ENTITY_VELOCITY}
	)
	public void on(
		PacketEvent event,
		User user, EntityVelocityReader reader
	) {
		Player player = user.player();
		Motion motion = reader.motion();
		if (reader.entityId() == player.getEntityId() && isRecording(user)) {
			user.doubleTickFeedback(event,
				() -> {
					MovementRecording recording = recordingSessionOf(user);
					if (recording != null) {
						long start = recording.ticks();
						lastVelocityStart.get(user).set(start);
					}
				},
				() -> {
					MovementRecording recording = recordingSessionOf(user);
					if (recording != null) {
						long start = lastVelocityStart.get(user).getAndSet(-1);
						if (start < 0) {
							return;
						}
						long end = recording.ticks();
						recording.insertAction(new ReceiveVelocity(
							motion, TickRange.betweenInclusive(start, end)
						));
					}
				}
			);
		}
	}

	public void saveRecordingDataTo(
		User user, File file
	) throws IOException {
		MovementRecording movementRecording = recordingData.get(user);
		try (
			OutputStream outputStream = Files.newOutputStream(file.toPath(), CREATE);
			DeflaterOutputStream compressedOutputStream = new DeflaterOutputStream(outputStream)
		) {
			ByteBuf buffer = Unpooled.buffer();
			MovementRecording.STREAM_CODEC.encode(
				buffer, movementRecording
			);
			buffer.readBytes(compressedOutputStream, buffer.readableBytes());
		}
		movementRecording.clear();
	}

	public @Nullable MovementRecording recordingSessionOf(User user) {
		return isRecording(user) ? recordingData.get(user) : null;
	}

	public void setRecordingStatus(User user, boolean recording) {
		this.recording.get(user).set(recording);
	}

	public boolean isRecording(User user) {
		return recording.get(user).get();
	}
}
