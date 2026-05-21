package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;

public interface Fluid {
  boolean isDry();
  boolean isOfWater();
  boolean isOfLava();
  float height();
  int level();

  boolean falling();
  boolean isSource();

  default boolean affectsFlow(Fluid other) {
    return other.isOfWater() || other.similarTo(this);
  }

  default boolean similarTo(Fluid other) {
    return isOfWater() == other.isOfWater() && isOfLava() == other.isOfLava();
  }

  default boolean sameAs(Fluid other) {
    return similarTo(other) && isSource() == other.isSource();
  }

  default BlockShape uncachedShapeAt(User user, BlockPosition pos) {
    return BlockShapes.emptyShape();
  }
}
