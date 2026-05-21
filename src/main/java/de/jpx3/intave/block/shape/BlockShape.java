package de.jpx3.intave.block.shape;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import it.unimi.dsi.fastutil.doubles.DoubleSet;

import java.util.List;

public interface BlockShape {
  double allowedOffset(Direction.Axis axis, BoundingBox entity, double offset);
  double min(Direction.Axis axis);
  double max(Direction.Axis axis);

  boolean intersectsWith(BoundingBox boundingBox);
  BlockShape contextualized(int posX, int posY, int posZ);
  BlockShape normalized(int posX, int posY, int posZ);

  default BlockShape contextualized(BlockPosition pos) {
    return contextualized(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
  }

  default BlockShape normalized(BlockPosition pos) {
    return normalized(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
  }

  void appendUnsortedCoordsTo(Direction.Axis axis, DoubleSet appendTo);

  @Nullable
  BlockRaytrace raytrace(Position origin, Position target);
  BoundingBox outline();

  @Deprecated
  List<BoundingBox> boundingBoxes();
  boolean isEmpty();
  boolean isCubic();
}
