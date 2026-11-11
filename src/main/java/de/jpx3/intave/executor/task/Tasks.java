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

package de.jpx3.intave.executor.task;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.platform.ServerPlatform;
import de.jpx3.intave.platform.SourbyCraftBridge;
import de.jpx3.intave.user.User;
import org.bukkit.World;

public final class Tasks {
  private static final ServerPlatform PLATFORM = ServerPlatform.current();
  private static final TaskScheduler SCHEDULER = createScheduler();

  private Tasks() {
  }

  public static Task delayed(Runnable runnable, long tickDelay) {
    return delayedNamed(null, runnable, tickDelay);
  }

  public static Task delayedNamed(String name, Runnable runnable, long tickDelay) {
    if (tickDelay < 0) {
      throw new IllegalArgumentException("Task delay cannot be negative");
    }
    return new DelayedTask(name, runnable, tickDelay);
  }

  public static Task periodic(Runnable runnable, long tickDelay, long tickPeriod) {
    return periodicNamed(null, runnable, tickDelay, tickPeriod);
  }

  public static Task periodicNamed(String name, Runnable runnable, long tickDelay, long tickPeriod) {
    if (tickDelay < 0) {
      throw new IllegalArgumentException("Task delay cannot be negative");
    }
    if (tickPeriod < 1) {
      throw new IllegalArgumentException("Task period must be positive");
    }
    return new PeriodicTask(name, runnable, tickDelay, tickPeriod);
  }

  public static void addShutdownHook() {
    ShutdownTasks.add(SCHEDULER::stopAll);
  }

  static void startSync(Task task) {
    SCHEDULER.startSync(task);
  }

  static void startUserSync(Task task, User user) {
    SCHEDULER.startUserSync(task, user);
  }

  static void startRegionSync(Task task, World world, int chunkX, int chunkZ) {
    SCHEDULER.startRegionSync(task, world, chunkX, chunkZ);
  }

  static void startAsync(Task task) {
    SCHEDULER.startAsync(task);
  }

  static void stop(Task task) {
    SCHEDULER.stop(task);
  }

  public static boolean isFoliaServer() {
    return platform() != ServerPlatform.BUKKIT;
  }

  public static ServerPlatform platform() {
    return SourbyCraftBridge.isSourbyCraft() ? ServerPlatform.SOURBYCRAFT : PLATFORM;
  }

  public static String schedulerName() {
    if (SCHEDULER instanceof AuroraTaskScheduler) {
      return "aurora";
    }
    return SCHEDULER instanceof FoliaTaskScheduler ? "folia" : "bukkit";
  }

  private static TaskScheduler createScheduler() {
    boolean sourbyCraft = PLATFORM == ServerPlatform.SOURBYCRAFT
      || SourbyCraftBridge.isSourbyCraft();
    ServerPlatform platform = sourbyCraft ? ServerPlatform.SOURBYCRAFT : PLATFORM;
    boolean auroraRequested = SourbyCraftBridge.schedulerRequested();
    Object auroraScheduler = sourbyCraft && auroraRequested
      ? SourbyCraftBridge.findScheduler()
      : null;
    return schedulerFor(platform, auroraScheduler, auroraRequested);
  }

  static TaskScheduler schedulerFor(
    ServerPlatform platform, Object auroraScheduler, boolean auroraRequested
  ) {
    if (platform == ServerPlatform.SOURBYCRAFT && auroraRequested && auroraScheduler != null) {
      try {
        return new AuroraTaskScheduler(auroraScheduler);
      } catch (RuntimeException | LinkageError exception) {
        IntaveLogger.logger().warn(
          "SourbyAPI AuroraScheduler unavailable; falling back to Folia: " + exception
        );
      }
    }
    if (platform == ServerPlatform.BUKKIT) {
      return new BukkitTaskScheduler();
    }
    return new FoliaTaskScheduler();
  }
}
