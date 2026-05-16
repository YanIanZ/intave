package de.jpx3.intave.test.client;


import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.codec.VarInt;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

final class ObsidianPayload {
	static final int MAX_PACKET_BYTES = 1024 * 1024;
	static final int MAX_CHUNK_BYTES = MAX_PACKET_BYTES - 128;
	private final ObsidianCommand command;
	private final UUID transferId;
	private final int chunkIndex;
	private final int chunkCount;
	private final int totalBytes;
	private final float speed;
	private final byte[] data;

	public static final StreamCodec<ByteBuf, ByteBuf, ObsidianPayload> STREAM_CODEC = StreamCodec.of(
		(buf, payload) -> payload.write(buf),
		ObsidianPayload::read
	);

	ObsidianPayload(
		ObsidianCommand command,
		UUID transferId,
		int chunkIndex,
		int chunkCount,
		int totalBytes,
		float speed,
		byte[] data
	) {
		this.command = command;
		this.transferId = transferId;
		this.chunkIndex = chunkIndex;
		this.chunkCount = chunkCount;
		this.totalBytes = totalBytes;
		this.speed = speed;
		this.data = data;
	}

	private static ObsidianPayload read(ByteBuf buffer) {
		ObsidianCommand command = ObsidianCommand.byId(VarInt.readFrom(buffer));
		UUID transferId = new UUID(buffer.readLong(), buffer.readLong());
		int chunkIndex = VarInt.readFrom(buffer);
		int chunkCount = VarInt.readFrom(buffer);
		int totalBytes = VarInt.readFrom(buffer);
		float speed = buffer.readFloat();
		byte[] data = readByteArray(buffer, MAX_CHUNK_BYTES);
		return new ObsidianPayload(command, transferId, chunkIndex, chunkCount, totalBytes, speed, data);
	}

	private void write(ByteBuf buffer) {
		VarInt.writeTo(buffer, command.id());
		buffer.writeLong(transferId.getMostSignificantBits());
		buffer.writeLong(transferId.getLeastSignificantBits());
		VarInt.writeTo(buffer, chunkIndex);
		VarInt.writeTo(buffer, chunkCount);
		VarInt.writeTo(buffer, totalBytes);
		buffer.writeFloat(speed);
		System.out.println("Writing " + data.length + " bytes");
		writeByteArray(buffer, data);
	}

	private static byte[] readByteArray(ByteBuf input, int maxSize) {
		int size = VarInt.readFrom(input);
		if (size > maxSize) {
			throw new DecoderException("ByteArray with size " + size + " is bigger than allowed " + maxSize);
		} else {
			byte[] bytes = new byte[size];
			input.readBytes(bytes);
			return bytes;
		}
	}

	private static void writeByteArray(ByteBuf output, byte[] bytes) {
		VarInt.writeTo(output, bytes.length);
		output.writeBytes(bytes);
	}

	static ObsidianPayload ready() {
		return control(ObsidianCommand.READY);
	}

	static ObsidianPayload desync(UUID transferId, String summary) {
		byte[] bytes = summary.getBytes(StandardCharsets.UTF_8);
		return new ObsidianPayload(
			ObsidianCommand.DESYNC,
			transferId,
			0,
			1,
			bytes.length,
			0.0F,
			bytes
		);
	}

	private static ObsidianPayload control(ObsidianCommand command) {
		return new ObsidianPayload(command, new UUID(0L, 0L), 0, 0, 0, 0.0F, new byte[0]);
	}

	public ObsidianCommand command() {
		return command;
	}

	public UUID transferId() {
		return transferId;
	}

	public int chunkIndex() {
		return chunkIndex;
	}

	public int chunkCount() {
		return chunkCount;
	}

	public int totalBytes() {
		return totalBytes;
	}

	public float speed() {
		return speed;
	}

	public byte[] data() {
		return data;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		ObsidianPayload that = (ObsidianPayload) obj;
		return Objects.equals(this.command, that.command) &&
			Objects.equals(this.transferId, that.transferId) &&
			this.chunkIndex == that.chunkIndex &&
			this.chunkCount == that.chunkCount &&
			this.totalBytes == that.totalBytes &&
			Float.floatToIntBits(this.speed) == Float.floatToIntBits(that.speed) &&
			Objects.equals(this.data, that.data);
	}

	@Override
	public int hashCode() {
		return Objects.hash(command, transferId, chunkIndex, chunkCount, totalBytes, speed, data);
	}

	@Override
	public String toString() {
		return "ObsidianPayload[" +
			"command=" + command + ", " +
			"transferId=" + transferId + ", " +
			"chunkIndex=" + chunkIndex + ", " +
			"chunkCount=" + chunkCount + ", " +
			"totalBytes=" + totalBytes + ", " +
			"speed=" + speed + ", " +
			"data=" + Arrays.toString(data) + ']';
	}
}