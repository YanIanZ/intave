package de.jpx3.intave.entity.size;

import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import de.jpx3.intave.test.unit.Severity;
import de.jpx3.intave.test.unit.UnitTest;
import de.jpx3.intave.test.unit.UnitTests;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Sheep;

public final class EntitySizeUnitTests extends UnitTests {
  public EntitySizeUnitTests() {
    super("ES");
  }

  @UnitTest(
    severity = Severity.ERROR
  )
  public void testSheep() {
    World world = Bukkit.getWorlds().get(0);
    // spawn sheep
    Sheep sheep = world.spawn(new Location(world, 0,0,0), Sheep.class);
    Object handle = ReflectiveHandleAccess.handleOf(sheep);
    // get size
    Class<?> entityClass = handle.getClass();
    HitboxSize size = HitboxSizeAccess.dimensionsOfNMSEntityClass(entityClass);
    if (size == null || Math.abs(size.width() - 0.9) > 0.01 || Math.abs(size.height() - 1.3) > 0.01) {
      fail("Failed to fetch sheep size, is " + size);
    }
  }
}
