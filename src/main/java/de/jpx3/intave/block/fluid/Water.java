package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;

final class Water implements Fluid {
  private final float height;
  private final int level;
  private final boolean falling;

  private Water(float height, int level, boolean falling) {
    this.height = height;
    this.level = level;
    this.falling = falling;
  }

  @Override
  public boolean isDry() {
    return false;
  }

  @Override
  public boolean isOfWater() {
    return true;
  }

  @Override
  public boolean isOfLava() {
    return false;
  }

  @Override
  public float height() {
    return height;
  }

  @Override
  public int level() {
    return level;
  }

  @Override
  public boolean falling() {
    return falling;
  }

  @Override
  public boolean isSource() {
    return false;
  }

  @Override
  public BlockShape uncachedShapeAt(User user, BlockPosition pos) {
    Fluid fluidAbove = VolatileBlockAccess.fluidAccess(user, pos.above());
    float height;
    if (fluidAbove.sameAs(this)) {
      height = 1.0F;
    } else {
      height = this.height;
    }
    return BoundingBox.fromBounds(
      pos.getX(), pos.getY(), pos.getZ(),
      pos.getX() + 1.0, pos.getY() + height, pos.getZ() + 1.00
    );
  }


  @Override
  public String toString() {
    return "Water{" +
      "height=" + height +
      ", falling=" + falling +
      '}';
  }

  public static Water ofHeight(float height, int level, boolean falling) {
    return new Water(height, level, falling);
  }
}
