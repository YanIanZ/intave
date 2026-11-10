/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.platform;

/**
 * Detects the server family Intave is running on. The order matters:
 * SourbyCraft also exposes Folia's regionized-server marker, so the brand check has to come first.
 */
public enum ServerPlatform {
  BUKKIT,
  FOLIA,
  SOURBYCRAFT;

  private static volatile ServerPlatform current;

  public static ServerPlatform current() {
    ServerPlatform platform = current;
    if (platform == null) {
      platform = detect();
      current = platform;
    }
    return platform;
  }

  static ServerPlatform detect() {
    if (SourbyCraftBridge.isSourbyCraft()) {
      return SOURBYCRAFT;
    }
    if (classPresent("io.papermc.paper.threadedregions.RegionizedServer")) {
      return FOLIA;
    }
    return BUKKIT;
  }

  static boolean classPresent(String className) {
    try {
      Class.forName(className, false, ServerPlatform.class.getClassLoader());
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }
}
