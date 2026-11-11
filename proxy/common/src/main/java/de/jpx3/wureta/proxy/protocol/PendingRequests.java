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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class PendingRequests {
  private final Map<UUID, List<Pending>> pending = new HashMap<>();
  private final int maxPerPlayer;

  public PendingRequests() {
    this(16);
  }

  public PendingRequests(int maxPerPlayer) {
    this.maxPerPlayer = maxPerPlayer;
  }

  public synchronized void add(UUID playerId, Pending request) {
    List<Pending> requests = pending.get(playerId);
    if (requests == null) {
      requests = new ArrayList<>();
      pending.put(playerId, requests);
    }
    for (Pending existing : requests) {
      if (existing.requestId.equals(request.requestId)) {
        existing.deadline = request.deadline;
        if (request.fallback != null) {
          existing.fallback = request.fallback;
        }
        return;
      }
    }
    if (requests.size() >= maxPerPlayer) {
      requests.remove(0);
    }
    requests.add(request);
  }

  public synchronized List<Pending> remove(UUID playerId) {
    List<Pending> requests = pending.remove(playerId);
    return requests == null ? new ArrayList<>() : requests;
  }

  public synchronized boolean remove(UUID playerId, UUID requestId) {
    List<Pending> requests = pending.get(playerId);
    if (requests == null) {
      return false;
    }
    Iterator<Pending> iterator = requests.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().requestId.equals(requestId)) {
        iterator.remove();
        if (requests.isEmpty()) {
          pending.remove(playerId);
        }
        return true;
      }
    }
    return false;
  }

  public synchronized void expire(long now, Consumer<Pending> onExpired) {
    Iterator<Map.Entry<UUID, List<Pending>>> iterator = pending.entrySet().iterator();
    while (iterator.hasNext()) {
      List<Pending> requests = iterator.next().getValue();
      Iterator<Pending> requestIterator = requests.iterator();
      while (requestIterator.hasNext()) {
        Pending request = requestIterator.next();
        if (now >= request.deadline) {
          requestIterator.remove();
          onExpired.accept(request);
        }
      }
      if (requests.isEmpty()) {
        iterator.remove();
      }
    }
  }

  public synchronized int size() {
    int size = 0;
    for (List<Pending> requests : pending.values()) {
      size += requests.size();
    }
    return size;
  }

  public static final class Pending {
    final String server;
    final UUID requestId;
    final UUID playerId;
    byte[] fallback;
    long deadline;

    public Pending(String server, UUID requestId, UUID playerId, long deadline) {
      this(server, requestId, playerId, deadline, null);
    }

    public Pending(
      String server, UUID requestId, UUID playerId, long deadline, byte[] fallback
    ) {
      this.server = server;
      this.requestId = requestId;
      this.playerId = playerId;
      this.deadline = deadline;
      this.fallback = fallback;
    }

    public String server() {
      return server;
    }

    public UUID requestId() {
      return requestId;
    }

    public UUID playerId() {
      return playerId;
    }
  }
}
