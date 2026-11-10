package de.jpx3.intave.library.pledge;


import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.List;

@SuppressWarnings("unchecked")
public final class TickEndTask {
  private static List<Object> RUNNABLES;
  private static Class<?> RUNNABLE_CLASS;

  static {
    List<Object> runnables = null;
    Class<?> runnableClass = null;
    try {
      Server server = Bukkit.getServer();
      Object mcServer = server.getClass().getMethod("getServer").invoke(server);
      Field field = getFieldByType(mcServer.getClass().getSuperclass(), List.class);
      runnables = (List<Object>) field.get(mcServer);
      runnableClass = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
    } catch (Throwable ignored) {
      // Region-threaded servers no longer expose MinecraftServer's legacy tickable list.
    }
    TickEndTask.RUNNABLES = runnables;
    TickEndTask.RUNNABLE_CLASS = runnableClass;
  }

  public static boolean isSupported() {
    return RUNNABLES != null && RUNNABLE_CLASS != null;
  }

  public TickEndTask(Runnable runnable) {
    this.runnable = runnable;
  }


  public static Field getFieldByType(Class<?> clazz, Class<?> type) throws NoSuchFieldException {
    for (Field field : clazz.getDeclaredFields()) {
      Class<?> foundType = field.getType();
      if (type.isAssignableFrom(foundType)) {
        field.setAccessible(true);
        return field;
      }
    }

    throw new NoSuchFieldException("Could not find field in class " + clazz.getName() + " with type " + type.getName());
  }

  private final Runnable runnable;
  private volatile Object registeredObject;

  // guard against double start and double cancel, repeats during shutdown must stay quiet
  private synchronized TickEndTask start() {
    if (this.registeredObject != null) {
      throw new IllegalStateException("Already registered!");
    }

    // hack to add runnable to tickables
    if (!Runnable.class.isAssignableFrom(TickEndTask.RUNNABLE_CLASS)) {
      Object handle = new Object();
      this.registeredObject = Proxy.newProxyInstance(
        TickEndTask.RUNNABLE_CLASS.getClassLoader(),
        new Class[]{TickEndTask.RUNNABLE_CLASS},
        (proxy, method, args) -> {
          Class<?> declaring = method.getDeclaringClass();
          if (declaring.equals(Object.class)) {
            return method.invoke(handle, args);
          } else {
            this.runnable.run();
            return null;
          }
        }
      );
    } else {
      this.registeredObject = this.runnable;
    }

    TickEndTask.RUNNABLES.add(this.registeredObject);
    return this;
  }

  // repeated cancel is a no-op so a second shutdown pass stays quiet
  public synchronized void cancel() {
    Object handle = this.registeredObject;
    if (handle == null) {
      return;
    }
    try {
      TickEndTask.RUNNABLES.remove(handle);
    } finally {
      // clear even if the server list is already gone
      this.registeredObject = null;
    }
  }

  public static TickEndTask create(Runnable runnable) {
    if (!isSupported()) {
      throw new IllegalStateException("Tick end is not supported on this server");
    }
    return new TickEndTask(runnable).start();
  }
}
