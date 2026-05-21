package de.jpx3.intave.share;

import org.bukkit.Location;
import org.bukkit.World;

import static de.jpx3.intave.share.BlockPosition.*;

public final class BlockPositionCursor {

	private int x;
	private int y;
	private int z;

	public BlockPositionCursor(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getZ() {
		return z;
	}

	public void setZ(int z) {
		this.z = z;
	}

	public Location toLocation(World world) {
		return new Location(world, this.getX(), this.getY(), this.getZ());
	}

	public Position toPosition() {
		return new Position(this.getX(), this.getY(), this.getZ());
	}

	public long asLong() {
		return asLong(this.getX(), this.getY(), this.getZ());
	}

	private static long asLong(int x, int y, int z) {
		long i = 0L;
		i |= (x & X_MASK) << X_SHIFT;
		i |= (y & Y_MASK) << Y_SHIFT;
		return i | (z & Z_MASK) << Z_SHIFT;
	}

	public void set(int currentX, int currentY, int currentZ) {
		this.x = currentX;
		this.y = currentY;
		this.z = currentZ;
	}

	public BlockPosition toBlockPosition() {
		return new BlockPosition(x, y, z);
	}
}
