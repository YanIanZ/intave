package de.jpx3.intave.share;

public interface BlockPositionConsumer {
	// return true if should keep going
	boolean accept(BlockPositionCursor cursor, int num);
}
