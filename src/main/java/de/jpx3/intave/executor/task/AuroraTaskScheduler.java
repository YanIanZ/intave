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
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.user.User;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link TaskScheduler} over SourbyCraft's {@code AuroraScheduler}. The scheduler object and its
 * methods are resolved reflectively so this class never links against SourbyAPI's Java 25
 * classfiles. If the reflected contract is unavailable, construction fails and {@link Tasks}
 * falls back to the Folia/Bukkit scheduler.
 */
final class AuroraTaskScheduler implements TaskScheduler {
  private final Object scheduler;
  private final Method runAt;
  private final Method runAtLater;
  private final Method runAtTimer;
  private final Method runFor;
  private final Method runForLater;
  private final Method runForTimer;
  private final Method runGlobal;
  private final Method runGlobalLater;
  private final Method runGlobalTimer;
  private final Method runAsync;
  private final Method runAsyncLater;
  private final Method runAsyncTimer;
  private final Map<Task, Object> scheduledTasks = new ConcurrentHashMap<>();

  AuroraTaskScheduler(Object scheduler) {
    if (scheduler == null) {
      throw new IllegalArgumentException("AuroraScheduler must not be null");
    }
    this.scheduler = scheduler;
    Class<?> type = scheduler.getClass();
    this.runAt = method(type, "runAt", Plugin.class, World.class, int.class, int.class, Runnable.class);
    this.runAtLater = method(type, "runAtLater", Plugin.class, World.class, int.class, int.class, Runnable.class, long.class);
    this.runAtTimer = method(type, "runAtTimer", Plugin.class, World.class, int.class, int.class, Consumer.class, long.class, long.class);
    this.runFor = method(type, "runFor", Plugin.class, Entity.class, Runnable.class, Runnable.class);
    this.runForLater = method(type, "runForLater", Plugin.class, Entity.class, Runnable.class, long.class, Runnable.class);
    this.runForTimer = method(type, "runForTimer", Plugin.class, Entity.class, Consumer.class, long.class, long.class, Runnable.class);
    this.runGlobal = method(type, "runGlobal", Plugin.class, Runnable.class);
    this.runGlobalLater = method(type, "runGlobalLater", Plugin.class, Runnable.class, long.class);
    this.runGlobalTimer = method(type, "runGlobalTimer", Plugin.class, Consumer.class, long.class, long.class);
    this.runAsync = method(type, "runAsync", Plugin.class, Runnable.class);
    this.runAsyncLater = method(type, "runAsyncLater", Plugin.class, Runnable.class, Duration.class);
    this.runAsyncTimer = method(type, "runAsyncTimer", Plugin.class, Consumer.class, Duration.class, Duration.class);
  }

  @Override
  public void startSync(Task task) {
    ensureNotRunning(task);
    if (task.period() >= 1) {
      Consumer<Object> consumer = ignored -> runTask(task);
      Object handle = invoke(
        runGlobalTimer, plugin(), consumer, Math.max(1, task.delay()), task.period()
      );
      remember(task, handle);
      return;
    }
    if (task.delay() == 0) {
      startGlobalOneShot(task, runGlobal);
      return;
    }
    startGlobalOneShot(task, runGlobalLater, task.delay());
  }

  @Override
  public void startUserSync(Task task, User user) {
    ensureNotRunning(task);
    Entity entity;
    try {
      entity = user.player();
    } catch (Throwable throwable) {
      startSync(task);
      return;
    }
    if (entity == null) {
      startSync(task);
      return;
    }
    Consumer<Object> consumer = ignored -> runTask(task);
    Runnable retired = () -> scheduledTasks.remove(task);
    try {
      if (task.period() >= 1) {
        Object handle = invoke(
          runForTimer, plugin(), entity, consumer, Math.max(1, task.delay()), task.period(), retired
        );
        remember(task, handle);
      } else if (task.delay() == 0) {
        startUserOneShot(task, entity, runFor, retired);
      } else {
        startUserOneShot(task, entity, runForLater, retired, task.delay());
      }
    } catch (UnsupportedOperationException exception) {
      // Entity schedulers are unavailable for some entity types on Folia/SourbyCraft.
      startSync(task);
    }
  }

  @Override
  public void startRegionSync(Task task, World world, int chunkX, int chunkZ) {
    ensureNotRunning(task);
    if (task.period() >= 1) {
      Consumer<Object> consumer = ignored -> runTask(task);
      Object handle = invoke(
        runAtTimer, plugin(), world, chunkX, chunkZ, consumer, Math.max(1, task.delay()), task.period()
      );
      remember(task, handle);
      return;
    }
    if (task.delay() == 0) {
      startRegionOneShot(task, world, chunkX, chunkZ, runAt);
      return;
    }
    startRegionOneShot(task, world, chunkX, chunkZ, runAtLater, task.delay());
  }

  @Override
  public void startAsync(Task task) {
    ensureNotRunning(task);
    if (task.period() >= 1) {
      Consumer<Object> consumer = ignored -> runTask(task);
      Object handle = invoke(
        runAsyncTimer, plugin(), consumer,
        Duration.ofMillis(Math.max(1, task.delay()) * 50L),
        Duration.ofMillis(task.period() * 50L)
      );
      remember(task, handle);
      return;
    }
    if (task.delay() == 0) {
      startAsyncOneShot(task, runAsync);
      return;
    }
    startAsyncOneShot(task, runAsyncLater, Duration.ofMillis(task.delay() * 50L));
  }

  @Override
  public void stop(Task task) {
    Object handle = scheduledTasks.remove(task);
    cancel(handle);
  }

  @Override
  public void stopAll() {
    for (Object handle : scheduledTasks.values()) {
      cancel(handle);
    }
    scheduledTasks.clear();
  }

  int scheduledTaskCount() {
    return scheduledTasks.size();
  }

  private void startGlobalOneShot(Task task, Method method, Object... delays) {
    AtomicBoolean completed = new AtomicBoolean();
    AtomicReference<Object> handleReference = new AtomicReference<>();
    Runnable runnable = oneShot(task, completed, handleReference);
    Object handle;
    if (delays.length == 0) {
      handle = invoke(method, plugin(), runnable);
    } else {
      handle = invoke(method, plugin(), runnable, delays[0]);
    }
    handleReference.set(handle);
    rememberOneShot(task, handle, completed);
  }

  private void startUserOneShot(Task task, Entity entity, Method method, Runnable retired, Object... delays) {
    AtomicBoolean completed = new AtomicBoolean();
    AtomicReference<Object> handleReference = new AtomicReference<>();
    Runnable runnable = oneShot(task, completed, handleReference);
    Object handle;
    if (delays.length == 0) {
      handle = invoke(method, plugin(), entity, runnable, retired);
    } else {
      handle = invoke(method, plugin(), entity, runnable, delays[0], retired);
    }
    handleReference.set(handle);
    rememberOneShot(task, handle, completed);
  }

  private void startRegionOneShot(
    Task task, World world, int chunkX, int chunkZ, Method method, Object... delays
  ) {
    AtomicBoolean completed = new AtomicBoolean();
    AtomicReference<Object> handleReference = new AtomicReference<>();
    Runnable runnable = oneShot(task, completed, handleReference);
    Object handle;
    if (delays.length == 0) {
      handle = invoke(method, plugin(), world, chunkX, chunkZ, runnable);
    } else {
      handle = invoke(method, plugin(), world, chunkX, chunkZ, runnable, delays[0]);
    }
    handleReference.set(handle);
    rememberOneShot(task, handle, completed);
  }

  private void startAsyncOneShot(Task task, Method method, Object... delays) {
    AtomicBoolean completed = new AtomicBoolean();
    AtomicReference<Object> handleReference = new AtomicReference<>();
    Runnable runnable = oneShot(task, completed, handleReference);
    Object handle;
    if (delays.length == 0) {
      handle = invoke(method, plugin(), runnable);
    } else {
      handle = invoke(method, plugin(), runnable, delays[0]);
    }
    handleReference.set(handle);
    rememberOneShot(task, handle, completed);
  }

  private Runnable oneShot(Task task, AtomicBoolean completed, AtomicReference<Object> handleReference) {
    return () -> {
      try {
        runTask(task);
      } finally {
        completed.set(true);
        Object handle = handleReference.get();
        if (handle != null) {
          scheduledTasks.remove(task, handle);
        }
      }
    };
  }

  private void rememberOneShot(Task task, Object handle, AtomicBoolean completed) {
    if (handle == null) {
      return;
    }
    scheduledTasks.put(task, handle);
    if (completed.get()) {
      scheduledTasks.remove(task, handle);
    }
  }

  private void remember(Task task, Object handle) {
    if (handle != null) {
      scheduledTasks.put(task, handle);
    }
  }

  private void ensureNotRunning(Task task) {
    if (scheduledTasks.containsKey(task)) {
      throw new IllegalStateException("Task is already running: " + task.name());
    }
  }

  private void runTask(Task task) {
    try {
      task.run();
    } catch (RuntimeException | Error throwable) {
      IntaveLogger.logger().error("Failed to execute Aurora scheduled task " + task.name());
      throwable.printStackTrace();
      throw throwable;
    }
  }

  private void cancel(Object handle) {
    if (handle == null) {
      return;
    }
    try {
      Method cancel = handle.getClass().getMethod("cancel");
      cancel.setAccessible(true);
      cancel.invoke(handle);
    } catch (InvocationTargetException exception) {
      IntaveLogger.logger().error(
        "Failed to cancel Aurora scheduled task: " + exception.getCause()
      );
    } catch (ReflectiveOperationException exception) {
      IntaveLogger.logger().error("Failed to cancel Aurora scheduled task: " + exception);
    }
  }

  private Plugin plugin() {
    return IntavePlugin.singletonInstance();
  }

  private static Method method(Class<?> type, String name, Class<?>... parameters) {
    try {
      Method method = type.getMethod(name, parameters);
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException exception) {
      throw new IllegalArgumentException(
        "AuroraScheduler is missing " + name + "(" + parameterNames(parameters) + ")",
        exception
      );
    }
  }

  private static String parameterNames(Class<?>... parameters) {
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < parameters.length; index++) {
      if (index > 0) {
        builder.append(", ");
      }
      builder.append(parameters[index].getSimpleName());
    }
    return builder.toString();
  }

  private Object invoke(Method method, Object... arguments) {
    try {
      return method.invoke(scheduler, arguments);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new IllegalStateException("AuroraScheduler invocation failed", cause);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("AuroraScheduler invocation failed", exception);
    }
  }
}
