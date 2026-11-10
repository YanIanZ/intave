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

import de.jpx3.intave.user.User;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuroraTaskSchedulerTest {
  @Test
  void periodicSyncUsesGlobalTimerWithClampedDelay() {
    FakeAuroraScheduler fake = new FakeAuroraScheduler();
    AuroraTaskScheduler scheduler = new AuroraTaskScheduler(fake);
    TestTask task = new TestTask(0, 5);

    scheduler.startSync(task);

    assertEquals("runGlobalTimer", fake.lastCall);
    assertEquals(1L, fake.lastDelay);
    assertEquals(5L, fake.lastPeriod);
    assertEquals(1, scheduler.scheduledTaskCount());
  }

  @Test
  void oneShotSyncIsRemovedAfterCompletion() {
    FakeAuroraScheduler fake = new FakeAuroraScheduler().runOneShotImmediately();
    AuroraTaskScheduler scheduler = new AuroraTaskScheduler(fake);
    TestTask task = new TestTask(0, -1);

    scheduler.startSync(task);

    assertEquals("runGlobal", fake.lastCall);
    assertEquals(1, task.runs);
    assertEquals(0, scheduler.scheduledTaskCount());
  }

  @Test
  void userSyncUsesEntitySchedulerAndRetiredCallback() {
    FakeAuroraScheduler fake = new FakeAuroraScheduler();
    AuroraTaskScheduler scheduler = new AuroraTaskScheduler(fake);
    User user = fakeUser();
    TestTask task = new TestTask(2, 5);

    scheduler.startUserSync(task, user);

    assertEquals("runForTimer", fake.lastCall);
    assertEquals(2L, fake.lastDelay);
    assertEquals(5L, fake.lastPeriod);
    assertEquals(1, scheduler.scheduledTaskCount());

    fake.lastRetired.run();
    assertEquals(0, scheduler.scheduledTaskCount());

    scheduler.stop(task);
    assertEquals(0, fake.cancelCount);
  }

  @Test
  void regionSyncUsesChunkCoordinates() {
    FakeAuroraScheduler fake = new FakeAuroraScheduler();
    AuroraTaskScheduler scheduler = new AuroraTaskScheduler(fake);
    TestTask task = new TestTask(2, 5);
    World world = null;

    scheduler.startRegionSync(task, world, 12, -4);

    assertEquals("runAtTimer", fake.lastCall);
    assertEquals(world, fake.lastWorld);
    assertEquals(12, fake.lastChunkX);
    assertEquals(-4, fake.lastChunkZ);
    assertEquals(2L, fake.lastDelay);
    assertEquals(5L, fake.lastPeriod);
  }

  @Test
  void asyncSyncUsesWallClockDurations() {
    FakeAuroraScheduler fake = new FakeAuroraScheduler();
    AuroraTaskScheduler scheduler = new AuroraTaskScheduler(fake);
    TestTask task = new TestTask(2, 5);

    scheduler.startAsync(task);

    assertEquals("runAsyncTimer", fake.lastCall);
    assertEquals(Duration.ofMillis(100), fake.lastAsyncDelay);
    assertEquals(Duration.ofMillis(250), fake.lastAsyncPeriod);
  }

  @Test
  void stopCancelsAndRemovesTheHandle() {
    FakeAuroraScheduler fake = new FakeAuroraScheduler();
    AuroraTaskScheduler scheduler = new AuroraTaskScheduler(fake);
    TestTask task = new TestTask(1, 5);

    scheduler.startSync(task);
    scheduler.stop(task);

    assertEquals(1, fake.cancelCount);
    assertEquals(0, scheduler.scheduledTaskCount());
  }

  @Test
  void stopAllCancelsEveryHandle() {
    FakeAuroraScheduler fake = new FakeAuroraScheduler();
    AuroraTaskScheduler scheduler = new AuroraTaskScheduler(fake);

    scheduler.startSync(new TestTask(1, 5));
    scheduler.startSync(new TestTask(1, 5));
    scheduler.stopAll();

    assertEquals(2, fake.cancelCount);
    assertEquals(0, scheduler.scheduledTaskCount());
  }

  @Test
  void secondStartOfSameTaskIsRejected() {
    FakeAuroraScheduler fake = new FakeAuroraScheduler();
    AuroraTaskScheduler scheduler = new AuroraTaskScheduler(fake);
    TestTask task = new TestTask(1, 5);

    scheduler.startSync(task);

    assertThrows(IllegalStateException.class, () -> scheduler.startSync(task));
  }

  @Test
  void unsupportedEntitySchedulerFallsBackToGlobal() {
    FakeAuroraScheduler fake = new FakeAuroraScheduler();
    fake.entitySchedulerUnsupported = true;
    AuroraTaskScheduler scheduler = new AuroraTaskScheduler(fake);
    User user = fakeUser();

    scheduler.startUserSync(new TestTask(0, -1), user);

    assertTrue(fake.calls.contains("runFor"));
    assertTrue(fake.calls.contains("runGlobal"));
    assertFalse(fake.calls.contains("runGlobalLater"));
  }

  private static User fakeUser() {
    Player player = (Player) Proxy.newProxyInstance(
      AuroraTaskSchedulerTest.class.getClassLoader(),
      new Class<?>[]{Player.class},
      (proxy, method, arguments) -> null
    );
    return (User) Proxy.newProxyInstance(
      AuroraTaskSchedulerTest.class.getClassLoader(),
      new Class<?>[]{User.class},
      (proxy, method, arguments) -> method.getName().equals("player") ? player : null
    );
  }

  private static final class TestTask implements Task {
    private final long delay;
    private final long period;
    private int runs;

    private TestTask(long delay, long period) {
      this.delay = delay;
      this.period = period;
    }

    @Override
    public void run() {
      runs++;
    }

    @Override
    public long delay() {
      return delay;
    }

    @Override
    public long period() {
      return period;
    }
  }

  private static final class FakeAuroraScheduler {
    private final List<String> calls = new ArrayList<>();
    private int cancelCount;
    private boolean entitySchedulerUnsupported;
    private boolean runOneShotImmediately;
    private String lastCall;
    private long lastDelay;
    private long lastPeriod;
    private World lastWorld;
    private int lastChunkX;
    private int lastChunkZ;
    private Duration lastAsyncDelay;
    private Duration lastAsyncPeriod;
    private Runnable lastRetired;

    private FakeAuroraScheduler runOneShotImmediately() {
      this.runOneShotImmediately = true;
      return this;
    }

    public FakeHandle runGlobal(Plugin plugin, Runnable runnable) {
      record("runGlobal");
      if (runOneShotImmediately) {
        runnable.run();
      }
      return new FakeHandle(this);
    }

    public FakeHandle runGlobalLater(Plugin plugin, Runnable runnable, long delay) {
      record("runGlobalLater");
      lastDelay = delay;
      return new FakeHandle(this);
    }

    public FakeHandle runGlobalTimer(Plugin plugin, Consumer<Object> consumer, long delay, long period) {
      record("runGlobalTimer");
      lastDelay = delay;
      lastPeriod = period;
      return new FakeHandle(this);
    }

    public FakeHandle runFor(Plugin plugin, Entity entity, Runnable runnable, Runnable retired) {
      record("runFor");
      if (entitySchedulerUnsupported) {
        throw new UnsupportedOperationException("entity scheduler unavailable");
      }
      lastRetired = retired;
      return new FakeHandle(this);
    }

    public FakeHandle runForLater(
      Plugin plugin, Entity entity, Runnable runnable, long delay, Runnable retired
    ) {
      record("runForLater");
      lastDelay = delay;
      lastRetired = retired;
      return new FakeHandle(this);
    }

    public FakeHandle runForTimer(
      Plugin plugin, Entity entity, Consumer<Object> consumer,
      long delay, long period, Runnable retired
    ) {
      record("runForTimer");
      lastDelay = delay;
      lastPeriod = period;
      lastRetired = retired;
      return new FakeHandle(this);
    }

    public FakeHandle runAt(Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable) {
      record("runAt");
      lastWorld = world;
      lastChunkX = chunkX;
      lastChunkZ = chunkZ;
      return new FakeHandle(this);
    }

    public FakeHandle runAtLater(
      Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable, long delay
    ) {
      record("runAtLater");
      lastWorld = world;
      lastChunkX = chunkX;
      lastChunkZ = chunkZ;
      lastDelay = delay;
      return new FakeHandle(this);
    }

    public FakeHandle runAtTimer(
      Plugin plugin, World world, int chunkX, int chunkZ,
      Consumer<Object> consumer, long delay, long period
    ) {
      record("runAtTimer");
      lastWorld = world;
      lastChunkX = chunkX;
      lastChunkZ = chunkZ;
      lastDelay = delay;
      lastPeriod = period;
      return new FakeHandle(this);
    }

    public FakeHandle runAsync(Plugin plugin, Runnable runnable) {
      record("runAsync");
      if (runOneShotImmediately) {
        runnable.run();
      }
      return new FakeHandle(this);
    }

    public FakeHandle runAsyncLater(Plugin plugin, Runnable runnable, Duration delay) {
      record("runAsyncLater");
      lastAsyncDelay = delay;
      return new FakeHandle(this);
    }

    public FakeHandle runAsyncTimer(
      Plugin plugin, Consumer<Object> consumer, Duration delay, Duration period
    ) {
      record("runAsyncTimer");
      lastAsyncDelay = delay;
      lastAsyncPeriod = period;
      return new FakeHandle(this);
    }

    private void record(String call) {
      calls.add(call);
      lastCall = call;
    }
  }

  private static final class FakeHandle {
    private final FakeAuroraScheduler scheduler;

    private FakeHandle(FakeAuroraScheduler scheduler) {
      this.scheduler = scheduler;
    }

    public void cancel() {
      scheduler.cancelCount++;
    }
  }
}
