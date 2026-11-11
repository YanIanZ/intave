package de.jpx3.intave.library.pledge;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.cleanup.StartupTasks;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;

import java.util.ArrayList;
import java.util.List;

public class TickEnd {
  private static final List<Runnable> tickEndSubscribers = new ArrayList<>();

  public static void start() {
    boolean regionThreaded = Tasks.isFoliaServer();
    if (!regionThreaded && TickEndTask.isSupported()) {
      try {
        TickEndTask task = TickEndTask.create(() -> tickEndSubscribers.forEach(Runnable::run));
        ShutdownTasks.add(task::cancel);
        return;
      } catch (Throwable exception) {
        IntaveLogger.logger().warn(
          "Tick-end hook registration failed; using the global tick scheduler as a fallback: " +
            exception.getMessage()
        );
      }
    } else if (!regionThreaded) {
      IntaveLogger.logger().warn(
        "Tick-end hook is unavailable on this server; using the global tick scheduler as a fallback"
      );
    } else {
      IntaveLogger.logger().info(
        "Tick-end uses the global region scheduler on this region-threaded server"
      );
    }
    StartupTasks.add(() -> {
      try {
        Task task = Tasks.periodicNamed(
          "TickEnd.fallback", () -> tickEndSubscribers.forEach(Runnable::run), 1, 1
        ).startSync();
        ShutdownTasks.add(task::cancel);
      } catch (RuntimeException exception) {
        IntaveLogger.logger().error("Tick-end fallback could not be scheduled: " + exception);
      }
    });
  }

  public static void subscribe(Runnable runnable) {
    tickEndSubscribers.add(runnable);
  }

  public static void unsubscribe(Runnable runnable) {
    tickEndSubscribers.remove(runnable);
  }
}
