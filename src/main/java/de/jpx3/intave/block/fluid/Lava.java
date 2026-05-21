package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;

final class Lava implements Fluid {
  private final float height;
  private final int heightIndex;
  private final boolean falling;

  private Lava(float height, int heightIndex, boolean falling) {
    this.height = height;
    this.heightIndex = heightIndex;
    this.falling = falling;
  }

  @Override
  public boolean isDry() {
    return false;
  }

  @Override
  public boolean isOfWater() {
    return false;
  }

  @Override
  public boolean isOfLava() {
    return true;
  }

  @Override
  public float height() {
    return height;
  }

  @Override
  public int level() {
    return heightIndex;
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
    return "Lava{" +
      "height=" + height +
      ", falling=" + falling +
      '}';
  }

  public static Lava ofHeight(float height, int level, boolean falling) {
    return new Lava(height, level, falling);
  }
}
