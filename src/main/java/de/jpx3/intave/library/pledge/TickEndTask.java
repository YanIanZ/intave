package de.jpx3.intave.library.pledge;


import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unchecked")
public final class TickEndTask {
  private static List<Object> RUNNABLES;
  private static Class<?> RUNNABLE_CLASS;
  private static Object MC_SERVER;
  private static Method ADD_TICKABLE;

  static {
    List<Object> runnables = null;
    Class<?> runnableClass = null;
    Object mcServer = null;
    Method addTickable = null;
    try {
      Server server = Bukkit.getServer();
      mcServer = server.getClass().getMethod("getServer").invoke(server);
      try {
        // Paper 1.20.1+ exposes a public tick-end queue. This is the preferred path on
        // modern Paper/SourbyCraft because the field names are remapped or removed.
        addTickable = mcServer.getClass().getMethod("addTickable", Runnable.class);
      } catch (NoSuchMethodException ignored) {
        // Older servers expose the tickable list instead.
      }
      if (addTickable == null) {
        Field field = getTickableField(mcServer.getClass());
        runnables = (List<Object>) field.get(mcServer);
        runnableClass = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
      }
    } catch (Throwable ignored) {
      // Region-threaded servers no longer expose MinecraftServer's legacy tickable list.
    }
    TickEndTask.MC_SERVER = mcServer;
    TickEndTask.ADD_TICKABLE = addTickable;
    TickEndTask.RUNNABLES = runnables;
    TickEndTask.RUNNABLE_CLASS = runnableClass;
  }

  public static boolean isSupported() {
    return ADD_TICKABLE != null || (RUNNABLES != null && RUNNABLE_CLASS != null);
  }

  public TickEndTask(Runnable runnable) {
    this.runnable = runnable;
  }


  private static Field getTickableField(Class<?> clazz) throws NoSuchFieldException {
    Field firstList = null;
    for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (!List.class.isAssignableFrom(field.getType())) {
          continue;
        }
        if (firstList == null) {
          firstList = field;
        }
        Class<?> elementType = listElementType(field);
        if (elementType != null && elementType.isInterface()) {
          field.setAccessible(true);
          return field;
        }
      }
    }
    if (firstList != null) {
      firstList.setAccessible(true);
      return firstList;
    }
    throw new NoSuchFieldException("Could not find a tickable list in " + clazz.getName());
  }

  private static Class<?> listElementType(Field field) {
    if (!(field.getGenericType() instanceof ParameterizedType)) {
      return null;
    }
    java.lang.reflect.Type argument = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
    if (argument instanceof Class) {
      return (Class<?>) argument;
    }
    if (argument instanceof ParameterizedType) {
      java.lang.reflect.Type raw = ((ParameterizedType) argument).getRawType();
      return raw instanceof Class ? (Class<?>) raw : null;
    }
    return null;
  }

  private final Runnable runnable;
  private volatile Object registeredObject;
  private volatile AtomicBoolean cancelGuard;

  // guard against double start and double cancel, repeats during shutdown must stay quiet
  private synchronized TickEndTask start() {
    if (this.registeredObject != null) {
      throw new IllegalStateException("Already registered!");
    }

    if (ADD_TICKABLE != null) {
      AtomicBoolean guard = new AtomicBoolean(true);
      Runnable guarded = () -> {
        if (guard.get()) {
          this.runnable.run();
        }
      };
      try {
        ADD_TICKABLE.invoke(MC_SERVER, guarded);
      } catch (ReflectiveOperationException | RuntimeException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        throw new IllegalStateException("Unable to register the tick-end task: " + cause, exception);
      }
      this.cancelGuard = guard;
      this.registeredObject = guarded;
      return this;
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
    AtomicBoolean guard = this.cancelGuard;
    if (guard != null) {
      guard.set(false);
      this.cancelGuard = null;
      this.registeredObject = null;
      return;
    }
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
