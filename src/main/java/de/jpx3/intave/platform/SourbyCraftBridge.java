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

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflection-only bridge to SourbyAPI. Intave main sources target Java 8, while SourbyAPI classes
 * are Java 25 classfiles; no {@code dev.iyanz} type may appear in a main-source signature.
 * Every failure is fail-closed: the caller falls back to the Folia/Bukkit scheduler.
 */
public final class SourbyCraftBridge {
  private static final String SOURBY_API = "dev.iyanz.sourbycraft.api.SourbyAPI";
  private static final String SCHEDULER_PROPERTY = "intave.sourbycraft.scheduler";

  private static volatile Class<?> sourbyApiClass;
  private static volatile boolean sourbyApiUnavailable;
  private static volatile Boolean sourbyCraft;
  private static volatile Object scheduler;

  private SourbyCraftBridge() {
  }

  public static boolean isSourbyCraft() {
    Boolean cached = sourbyCraft;
    if (cached != null) {
      return cached;
    }
    Class<?> type = sourbyApiClass();
    if (type == null) {
      return false;
    }
    try {
      Method method = type.getMethod("isSourbyCraft");
      Object result = method.invoke(null);
      if (Boolean.TRUE.equals(result)) {
        sourbyCraft = Boolean.TRUE;
        return true;
      }
    } catch (Throwable ignored) {
      // SourbyAPI is present but not ready (or the method changed); retry on the next call.
    }
    return false;
  }

  public static Object findScheduler() {
    Object cached = scheduler;
    if (cached != null) {
      return cached;
    }
    Class<?> type = sourbyApiClass();
    if (type == null) {
      return null;
    }
    try {
      Method method = type.getMethod("findScheduler");
      Object result = method.invoke(null);
      if (!(result instanceof Optional)) {
        return null;
      }
      Object value = ((Optional<?>) result).orElse(null);
      if (value != null) {
        scheduler = value;
      }
      return value;
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static String buildNumber() {
    Class<?> type = sourbyApiClass();
    if (type == null) {
      return "unknown";
    }
    try {
      Method findBuild = type.getMethod("findBuild");
      Object result = findBuild.invoke(null);
      if (!(result instanceof Optional)) {
        return "unknown";
      }
      Object build = ((Optional<?>) result).orElse(null);
      if (build == null) {
        return "unknown";
      }
      Method buildNumber = build.getClass().getMethod("buildNumber");
      Object value = buildNumber.invoke(build);
      return value == null ? "unknown" : value.toString();
    } catch (Throwable ignored) {
      return "unknown";
    }
  }

  /**
   * Aurora scheduling is the default on SourbyCraft. Operators can force the Folia path with
   * {@code -Dintave.sourbycraft.scheduler=folia} (or {@code =false}).
   */
  public static boolean schedulerRequested() {
    String configured = System.getProperty(SCHEDULER_PROPERTY, "aurora");
    return !"folia".equalsIgnoreCase(configured)
      && !"false".equalsIgnoreCase(configured)
      && !"disabled".equalsIgnoreCase(configured);
  }

  private static Class<?> sourbyApiClass() {
    Class<?> cached = sourbyApiClass;
    if (cached != null) {
      return cached;
    }
    if (sourbyApiUnavailable) {
      return null;
    }
    try {
      Class<?> type = Class.forName(SOURBY_API, false, SourbyCraftBridge.class.getClassLoader());
      sourbyApiClass = type;
      return type;
    } catch (Throwable ignored) {
      sourbyApiUnavailable = true;
      return null;
    }
  }
}
