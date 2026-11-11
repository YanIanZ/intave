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
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final int maxPerSecond;

  public RateLimiter(int maxPerSecond) {
    this.maxPerSecond = maxPerSecond;
  }

  public boolean allow(String source, long now) {
    long second = now / 1000L;
    Window window = windows.get(source);
    if (window == null) {
      Window created = new Window(second);
      Window existing = windows.putIfAbsent(source, created);
      window = existing == null ? created : existing;
    }
    synchronized (window) {
      if (window.second != second) {
        window.second = second;
        window.count = 0;
      }
      return ++window.count <= maxPerSecond;
    }
  }

  public void cleanup(long now) {
    long second = now / 1000L;
    Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
    while (iterator.hasNext()) {
      if (second - iterator.next().getValue().second > 5) {
        iterator.remove();
      }
    }
  }

  private static final class Window {
    long second;
    int count;

    Window(long second) {
      this.second = second;
    }
  }
}
