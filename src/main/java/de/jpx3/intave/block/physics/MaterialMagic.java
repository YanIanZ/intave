package de.jpx3.intave.block.physics;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.type.BlockTypeAccess;
import org.bukkit.Material;

public final class MaterialMagic {
  public static boolean blocksMovement(Material material) {
    // Liquids don't block movement
    if (isLavaOrWater(material)) {
      return false;
    }
    if (material == BlockTypeAccess.WEB) {
      return false;
    }
    // Materials of MaterialLogic and MaterialTransparent override blocksMovement() with "false"
    return !includesMaterialLogic(material) && !includesMaterialTransparent(material);
  }

  public static boolean blockSolid(Material material) {
    // Liquids aren't solid
    if (isLavaOrWater(material) || material.isTransparent()) {
      return false;
    }
    // Materials of MaterialLogic and MaterialTransparent override isSolid() with "false"
    return !includesMaterialLogic(material) && !includesMaterialTransparent(material);
  }

  private static boolean includesMaterialLogic(Material material) {
    switch (material) {
      // Material Logic
      // - portal
      case ENDER_PORTAL:
        // - Snow
      case SNOW:
        // - Carpet
      case CARPET:
        // - Circuits
      case STONE_BUTTON:
      case WOOD_BUTTON:
      case FLOWER_POT_ITEM:
      case LADDER:
      case LEVER:
      case REDSTONE_WIRE:
      case DIODE:
      case DIODE_BLOCK_OFF:
      case DIODE_BLOCK_ON:
      case SKULL:
      case REDSTONE_TORCH_ON:
      case REDSTONE_COMPARATOR_OFF:
      case TRIPWIRE:
      case TRIPWIRE_HOOK:
        // - Vines
      case DEAD_BUSH:
      case CHORUS_PLANT:
      case DOUBLE_PLANT:
      case LONG_GRASS:
      case VINE:
        // - Plants
        // BLOCK_BUSH?
        // BLOCK_REED?
      case NETHER_WART_BLOCK:
      case COCOA: {
        return true;
      }
      default: {
        return false;
      }
    }
  }

  private static boolean includesMaterialTransparent(Material material) {
    switch (material) {
      case AIR:
      case FIRE: {
        return true;
      }
      default: {
        return false;
      }
    }
  }

  private static final Material STATIONARY_WATER = Material.getMaterial("STATIONARY_WATER");
  private static final Material STATIONARY_LAVA = Material.getMaterial("STATIONARY_LAVA");
//  private static final Material TALL_SEAGRASS = Material.getMaterial("TALL_SEAGRASS");
//  private static final Material SEA_GRASS = Material.getMaterial("SEA_GRASS");
//  private static final Material KELP_PLANT = Material.getMaterial("KELP_PLANT");

  public static boolean isLavaOrWater(Material material) {
    return isLava(material) || isWater(material);
  }

  public static boolean couldContainLiquid(Material material) {
    if (material == null) {
      return false;
    }
    return isLavaOrWater(material) || Fluids.canContainFluid(material);
  }

  public static boolean couldContainLiquid(Material material, int variantIndex) {
    if (material == null) {
      return false;
    }
    return isLavaOrWater(material) || Fluids.isFluid(material, variantIndex);
  }

  public static boolean isLava(Material material) {
    return (STATIONARY_LAVA != null && material == STATIONARY_LAVA) || material == Material.LAVA;
  }

  public static boolean isWater(Material material) {
    return (STATIONARY_WATER != null && material == STATIONARY_WATER) || material == Material.WATER;
  }

  private static final Material BUBBLE_COLUMN = Material.getMaterial("BUBBLE_COLUMN");

	public static @Nullable Material bubbleColumn() {
    return BUBBLE_COLUMN;
  }
}