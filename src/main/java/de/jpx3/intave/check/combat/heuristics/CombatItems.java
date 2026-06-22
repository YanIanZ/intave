package de.jpx3.intave.check.combat.heuristics;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Lightweight, version-agnostic classification of combat-relevant items for the PvP heuristics.
 *
 * <p>Classification is done by {@link Material} <i>name</i> rather than hard enum references so it
 * works across the whole supported range and stays forward-compatible with newer weapons (e.g. the
 * mace added in 1.21, and items introduced on the 26.x line such as spears) without the codebase
 * needing to compile against a specific server version's {@code Material} constants.
 *
 * <p>"Heavy hitters" are the burst/charge weapons whose hit-and-swap combos drive the modern PvP
 * meta (mace, trident, spear-likes); rapid swapping between them and a primary weapon is what the
 * fast-swap heuristic watches for.
 */
public final class CombatItems {
  private CombatItems() {
  }

  public static boolean isCombatItem(ItemStack item) {
    return item != null && isCombatItem(item.getType());
  }

  public static boolean isCombatItem(Material material) {
    if (material == null) {
      return false;
    }
    String name = material.name();
    return name.endsWith("_SWORD")
      || name.endsWith("_AXE")
      || name.equals("MACE")
      || name.equals("TRIDENT")
      || name.endsWith("SPEAR")
      || name.equals("BOW")
      || name.equals("CROSSBOW");
  }

  public static boolean isHeavyHitter(ItemStack item) {
    return item != null && isHeavyHitter(item.getType());
  }

  public static boolean isHeavyHitter(Material material) {
    if (material == null) {
      return false;
    }
    String name = material.name();
    return name.equals("MACE")
      || name.equals("TRIDENT")
      || name.endsWith("SPEAR");
  }

  public static boolean isMace(ItemStack item) {
    return item != null && isMace(item.getType());
  }

  public static boolean isMace(Material material) {
    return material != null && material.name().equals("MACE");
  }
}
