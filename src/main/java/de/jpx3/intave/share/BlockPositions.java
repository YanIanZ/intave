package de.jpx3.intave.share;

import de.jpx3.intave.annotate.Nullable;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public interface BlockPositions extends Iterable<BlockPositionCursor> {
	@Override
	@NotNull Iterator<BlockPositionCursor> iterator();

	default Iterable<BlockPosition> immutablePositions() {
		return () -> new Iterator<BlockPosition>() {
			final Iterator<BlockPositionCursor> cursorIterator = iterator();

			@Override
			public boolean hasNext() {
				return cursorIterator.hasNext();
			}

			@Override
			public @Nullable BlockPosition next() {
				BlockPositionCursor cursor = cursorIterator.next();
				if (cursor == null) return null;
				return cursor.toBlockPosition();
			}
		};
	}

	default @NotNull BlockPositions and(BlockPositions other) {
		return () -> new Iterator<BlockPositionCursor>() {
			final Iterator<BlockPositionCursor> thisIterator = iterator();
			final Iterator<BlockPositionCursor> otherIterator = other.iterator();

			@Override
			public boolean hasNext() {
				return thisIterator.hasNext() || otherIterator.hasNext();
			}

			@Override
			public @Nullable BlockPositionCursor next() {
				if (thisIterator.hasNext()) {
					return thisIterator.next();
				}
				if (otherIterator.hasNext()) {
					return otherIterator.next();
				}
				return null;
			}
		};
	}

	default @NotNull BlockPositions distinct() {
		return () -> new Iterator<BlockPositionCursor>() {
			private final LongSet visitedBlocks = new LongOpenHashSet();
			private final Iterator<BlockPositionCursor> thisIterator = iterator();

			@Override
			public boolean hasNext() {
				while (thisIterator.hasNext()) {
					BlockPositionCursor cursor = thisIterator.next();
					if (visitedBlocks.add(cursor.asLong())) {
						return true;
					}
				}
				return false;
			}

			@Override
			public BlockPositionCursor next() {
				while (thisIterator.hasNext()) {
					BlockPositionCursor cursor = thisIterator.next();
					if (visitedBlocks.add(cursor.asLong())) {
						return cursor;
					}
				}
				return null;
			}
		};
	}
}
