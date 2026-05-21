package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Set;

final class PowderSnowPhysics implements BlockPhysic {
  private Set<Material> materials;
  private boolean supported;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    materials = MaterialSearch.materialsThatContain("POWDER_SNOW");
    supported = !materials.isEmpty();
  }

  @Override
  public Motion entityInside(User user, Location location, Position playerPosition, double motionX, double motionY, double motionZ, boolean flag4) {
    MovementMetadata movementData = user.meta().movement();
    Material block = VolatileBlockAccess.typeAccess(
      user, user.player().getWorld(), playerPosition
    );
    if (materials.contains(block)) {
      movementData.setMotionMultiplier(new Vector(0.9f, 1.5f, 0.9f));
    }
    return null;
  }

  @Override
  public boolean supportedOnServerVersion() {
    return supported;
  }

  @Override
  public Set<Material> applicableMaterials() {
    return materials;
  }
}
