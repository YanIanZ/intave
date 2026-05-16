package de.jpx3.intave.test.client;


import de.jpx3.intave.share.Position;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

final class TouchedBlock {
	private final int x;
	private final int y;
	private final int z;
	private final String block;

	TouchedBlock(int x, int y, int z, String block) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.block = block;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int z() {
		return z;
	}

	public Position position() {
		return new Position(x, y, z);
	}

	public String block() {
		return block;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		TouchedBlock that = (TouchedBlock) obj;
		return this.x == that.x &&
			this.y == that.y &&
			this.z == that.z &&
			Objects.equals(this.block, that.block);
	}

	public BlockData blockData() {
		try {
			Method createBlockData = Bukkit.class.getMethod("createBlockData", String.class);
			return (BlockData) createBlockData.invoke(null, block);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("Failed to create block data for block: " + block, e);
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y, z, block);
	}

	@Override
	public String toString() {
		return "TouchedBlock[" +
			"x=" + x + ", " +
			"y=" + y + ", " +
			"z=" + z + ", " +
			"block=" + block + ']';
	}
}