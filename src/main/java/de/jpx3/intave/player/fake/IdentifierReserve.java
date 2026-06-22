package de.jpx3.intave.player.fake;

import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Entity Ids can only be aquired sync, but we need our ids async
 * So we reserve us a bunch of ids so we can use them later
 */
public final class IdentifierReserve {
  private static final Field ENTITY_COUNT_FIELD = resolveEntityCountField();
  private static final int REQUIRED_ID_POOL_SIZE = 25;

  /**
   * Resolves the static entity-id counter used to hand out unique entity ids.
   *
   * <p>The counter has moved across versions:
   * <ul>
   *   <li>&le;1.20: a plain {@code static int entityCount} on {@code Entity};</li>
   *   <li>1.21 – 26.1.2: a {@code static AtomicInteger ENTITY_COUNTER} on {@code Entity};</li>
   *   <li>26.2+: the same {@code ENTITY_COUNTER} relocated to {@code ServerLevel}
   *       (locate key {@code WorldServer}).</li>
   * </ul>
   *
   * <p>We first try the mapped {@code Entity.entityCount} field; if that misses (as on 26.2, where
   * the field left {@code Entity}) we fall back to a structural search of the candidate classes:
   * the counter is the static {@link AtomicInteger} (modern) — or a NON-final static {@code int}
   * (legacy) — so it can be found by type regardless of where it lives or how it is named. Final
   * {@code int} constants such as {@code CURRENT_LEVEL} are deliberately never selected.
   */
  private static Field resolveEntityCountField() {
    try {
      return Lookup.serverField("Entity", "entityCount");
    } catch (RuntimeException primaryFailure) {
      Field structural = locateCounterByType();
      if (structural != null) {
        return structural;
      }
      throw primaryFailure;
    }
  }

  private static Field locateCounterByType() {
    // "Entity" covers 1.21–26.1.2 (and legacy); "WorldServer" (ServerLevel) covers 26.2+, where the
    // counter was relocated. Both keys are resolved through the version-aware locate system.
    Field counter = staticCounterIn("Entity");
    if (counter == null) {
      counter = staticCounterIn("WorldServer");
    }
    if (counter != null && !counter.isAccessible()) {
      counter.setAccessible(true);
    }
    return counter;
  }

  private static Field staticCounterIn(String classKey) {
    Class<?> owner;
    try {
      owner = Lookup.serverClass(classKey);
    } catch (RuntimeException unresolved) {
      return null;
    }
    // An AtomicInteger reference may be final (the object itself is mutated); a primitive counter
    // must be a NON-final static int, never a compile-time constant.
    Field atomicCounter = firstStaticField(owner, AtomicInteger.class, false);
    return atomicCounter != null ? atomicCounter : firstStaticField(owner, int.class, true);
  }

  private static Field firstStaticField(Class<?> owner, Class<?> type, boolean requireNonFinal) {
    for (Field field : owner.getDeclaredFields()) {
      int modifiers = field.getModifiers();
      if (!Modifier.isStatic(modifiers) || field.getType() != type) {
        continue;
      }
      if (requireNonFinal && Modifier.isFinal(modifiers)) {
        continue;
      }
      return field;
    }
    return null;
  }

  private static final Queue<Integer> availableIds = new ConcurrentLinkedDeque<>();

  public static void setup() {
    refreshIfRequired();
  }

  public static int acquireNew() {
    refreshIfRequired();
    Integer poll = availableIds.poll();
    return poll != null ? poll : reserveEntityId();
  }

  private static void refreshIfRequired() {
    if (availableIds.size() < REQUIRED_ID_POOL_SIZE) {
      if (Bukkit.isPrimaryThread()) {
        refillEntityIds();
      } else {
        Synchronizer.synchronize(IdentifierReserve::refillEntityIds);
      }
    }
  }

  private static void refillEntityIds() {
    int missing = (REQUIRED_ID_POOL_SIZE - availableIds.size());
    if (missing > 0) {
      Arrays.stream(reserveEntityIds(missing)).forEach(availableIds::add);
    }
  }

  private static int[] reserveEntityIds(int amount) {
    return IntStream.range(0, amount).map(i -> reserveEntityId()).toArray();
  }

  private static final boolean ATOMIC_INTEGER_FIELD = ENTITY_COUNT_FIELD.getType() == AtomicInteger.class;

  private static int reserveEntityId() {
    int newId = 0;
    try {
      if (ATOMIC_INTEGER_FIELD) {
        AtomicInteger atomicInteger = (AtomicInteger) ENTITY_COUNT_FIELD.get(null);
        newId = atomicInteger.getAndIncrement();
      } else {
        newId = ENTITY_COUNT_FIELD.getInt(null);
        ENTITY_COUNT_FIELD.setInt(null, newId + 1);
      }
    } catch (IllegalAccessException exception) {
      exception.printStackTrace();
    }
    return newId;
  }
}