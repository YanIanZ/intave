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

import de.jpx3.intave.platform.ServerPlatform;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TasksSelectionTest {
  @Test
  void bukkitSelectsBukkitScheduler() {
    assertTrue(
      Tasks.schedulerFor(ServerPlatform.BUKKIT, null, true) instanceof BukkitTaskScheduler
    );
  }

  @Test
  void sourbyCraftWithAuroraSchedulerSelectsAurora() {
    assertTrue(
      Tasks.schedulerFor(ServerPlatform.SOURBYCRAFT, new MinimalAuroraScheduler(), true)
        instanceof AuroraTaskScheduler
    );
  }

  private static final class MinimalAuroraScheduler {
    public Object runAt(Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable) {
      return null;
    }

    public Object runAtLater(
      Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable, long delay
    ) {
      return null;
    }

    public Object runAtTimer(
      Plugin plugin, World world, int chunkX, int chunkZ,
      Consumer<Object> consumer, long delay, long period
    ) {
      return null;
    }

    public Object runFor(Plugin plugin, Entity entity, Runnable runnable, Runnable retired) {
      return null;
    }

    public Object runForLater(
      Plugin plugin, Entity entity, Runnable runnable, long delay, Runnable retired
    ) {
      return null;
    }

    public Object runForTimer(
      Plugin plugin, Entity entity, Consumer<Object> consumer,
      long delay, long period, Runnable retired
    ) {
      return null;
    }

    public Object runGlobal(Plugin plugin, Runnable runnable) {
      return null;
    }

    public Object runGlobalLater(Plugin plugin, Runnable runnable, long delay) {
      return null;
    }

    public Object runGlobalTimer(Plugin plugin, Consumer<Object> consumer, long delay, long period) {
      return null;
    }

    public Object runAsync(Plugin plugin, Runnable runnable) {
      return null;
    }

    public Object runAsyncLater(Plugin plugin, Runnable runnable, Duration delay) {
      return null;
    }

    public Object runAsyncTimer(
      Plugin plugin, Consumer<Object> consumer, Duration delay, Duration period
    ) {
      return null;
    }
  }
}
