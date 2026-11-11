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

package de.jpx3.wureta.proxy.protocol;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReplayGuard {
  private final Map<UUID, Long> seen = new ConcurrentHashMap<>();
  private final long ttlMillis;
  private final int maxEntries;

  public ReplayGuard() {
    this(NetworkLimits.MAX_CLOCK_SKEW_MILLIS, 10_000);
  }

  public ReplayGuard(long ttlMillis, int maxEntries) {
    this.ttlMillis = ttlMillis;
    this.maxEntries = maxEntries;
  }

  public boolean accept(UUID requestId, long timestamp) {
    Long previous = seen.putIfAbsent(requestId, timestamp);
    if (previous != null) {
      return false;
    }
    if (seen.size() > maxEntries) {
      cleanup(System.currentTimeMillis());
    }
    return true;
  }

  public void cleanup(long now) {
    Iterator<Map.Entry<UUID, Long>> iterator = seen.entrySet().iterator();
    while (iterator.hasNext()) {
      if (now - iterator.next().getValue() > ttlMillis) {
        iterator.remove();
      }
    }
  }

  public int size() {
    return seen.size();
  }
}
