package de.jpx3.intave.codec;

import io.netty.buffer.ByteBuf;

// net.minecraft.network.VarInt
public final class VarInt {
	public static boolean hasContinuationBit(byte in) {
		return (in & 128) == 128;
	}

	public static int readFrom(ByteBuf input) {
		int result = 0;
		int bytePos = 0;
		byte in;
		do {
			in = input.readByte();
			result |= (in & 0x7f) << bytePos++ * 7;
			if (bytePos > 5) {
				throw new RuntimeException("VarInt too big");
			}
		} while (hasContinuationBit(in));
		return result;
	}

	public static ByteBuf writeTo(ByteBuf output, int value) {
		while ((value & 0xffffff80) != 0) {
			output.writeByte(value & 0x7f | 0x80);
			value >>>= 7;
		}
		output.writeByte(value);
		return output;
	}
}
