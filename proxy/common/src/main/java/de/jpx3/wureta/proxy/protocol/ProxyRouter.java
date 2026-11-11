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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class ProxyRouter {
  private static final long FRESH_CACHE_MILLIS = 5000L;

  private final ProxyConfig config;
  private final ProxyPlatform platform;
  private final StorageCache cache;
  private final PendingRequests pending;
  private final ReplayGuard replay;
  private final RateLimiter rateLimiter;
  private final ChunkAssembler assembler = new ChunkAssembler();
  private final byte[] secret;
  private long lastWarning;

  public ProxyRouter(ProxyConfig config, ProxyPlatform platform) {
    this(config, platform, null);
  }

  public ProxyRouter(ProxyConfig config, ProxyPlatform platform, Path storageDirectory) {
    this.config = config;
    this.platform = platform;
    this.cache = new StorageCache(
      config.maxCacheEntries(),
      config.maxCacheBytes(),
      config.cacheTtlMillis(),
      storageDirectory,
      config.diskTtlMillis()
    );
    this.pending = new PendingRequests();
    this.replay = new ReplayGuard(config.replayTtlMillis(), 20_000);
    this.rateLimiter = new RateLimiter(config.maxMessagesPerSecond());
    this.secret = config.secretBytes();
  }

  public void handle(String sourceServer, byte[] data) {
    if (sourceServer == null || sourceServer.isEmpty() || data == null || data.length == 0) {
      return;
    }
    long now = System.currentTimeMillis();
    if (!rateLimiter.allow(sourceServer, now)) {
      debug("Dropped rate-limited message from " + sourceServer);
      return;
    }

    byte[] frameBytes;
    try {
      frameBytes = assembler.accept(data);
    } catch (ProtocolException exception) {
      warnOnce("Dropped invalid chunk from " + sourceServer + ": " + exception.getMessage());
      return;
    }
    if (frameBytes == null) {
      return;
    }

    NetworkFrame frame;
    try {
      frame = FrameCodec.decode(frameBytes, secret);
    } catch (ProtocolException exception) {
      warnOnce("Dropped invalid frame from " + sourceServer + ": " + exception.getMessage());
      return;
    }
    if (!replay.accept(frame.requestId(), frame.timestamp())) {
      return;
    }

    switch (frame.type()) {
      case ALERT:
      case TRUST:
      case SANCTION:
        broadcast(sourceServer, frame, now);
        break;
      case DIRECT:
        direct(sourceServer, frame, now);
        break;
      case STORE:
        receiveStore(sourceServer, frame, now);
        break;
      case REQUEST:
        receiveRequest(sourceServer, frame, now);
        break;
      case RESPONSE:
        receiveResponse(sourceServer, frame, now);
        break;
      case PING:
        sendPong(sourceServer, frame, now);
        break;
      case PONG:
        debug("PONG from " + sourceServer + ": " + new String(frame.payload(), StandardCharsets.UTF_8));
        break;
      default:
        debug("Ignored unsupported frame type " + frame.type());
        break;
    }
  }

  public void tick() {
    long now = System.currentTimeMillis();
    cache.cleanup(now);
    assembler.cleanup(now);
    replay.cleanup(now);
    rateLimiter.cleanup(now);
    pending.expire(now, request -> {
      byte[] payload = request.fallback == null ? new byte[0] : request.fallback;
      sendResponse(request.server, request.requestId, request.playerId, payload, now);
    });
  }

  private void broadcast(String sourceServer, NetworkFrame frame, long now) {
    NetworkFrame forwarded = frame
      .withSource(sourceServer)
      .withTimestamp(now);
    broadcastFrame(sourceServer, forwarded);
    debug("Broadcast " + frame.type() + " from " + sourceServer);
  }

  private void direct(String sourceServer, NetworkFrame frame, long now) {
    if (frame.target().isEmpty()) {
      debug("Dropped DIRECT frame without target from " + sourceServer);
      return;
    }
    sendFrame(frame.target(), frame.withSource(sourceServer).withTimestamp(now));
    debug("Routed DIRECT frame from " + sourceServer + " to " + frame.target());
  }

  private void receiveStore(String sourceServer, NetworkFrame frame, long now) {
    cache.put(frame.playerId(), frame.payload(), now);
    int delivered = fulfillPending(frame.playerId(), frame.payload(), now);
    debug(
      "Cached storage for " + frame.playerId() + " from " + sourceServer +
        " (" + frame.payload().length + " bytes, " + delivered + " pending)"
    );
  }

  private void receiveRequest(String sourceServer, NetworkFrame frame, long now) {
    UUID playerId = frame.playerId();
    byte[] cached = cache.get(playerId, now);
    if (cached != null && cache.getFresh(playerId, now, FRESH_CACHE_MILLIS) != null) {
      sendResponse(sourceServer, frame.requestId(), playerId, cached, now);
      debug("Answered storage request for " + playerId + " from fresh cache");
      return;
    }
    // A stale autosave is only a fallback. Ask the live backend first so a server switch
    // cannot silently discard the newest violation levels.
    pending.add(
      playerId,
      new PendingRequests.Pending(
        sourceServer,
        frame.requestId(),
        playerId,
        now + config.pendingTimeoutMillis(),
        cached
      )
    );
    // A STORE can land between the first cache lookup and pending registration. Re-check before
    // asking the backends so a fresh save is not replaced by our stale fallback at timeout.
    byte[] arrivedFresh = cache.getFresh(playerId, now, FRESH_CACHE_MILLIS);
    if (arrivedFresh != null) {
      pending.remove(playerId, frame.requestId());
      sendResponse(sourceServer, frame.requestId(), playerId, arrivedFresh, now);
      debug("Answered storage request for " + playerId + " from cache arrived during request");
      return;
    }
    broadcastFrame(sourceServer, frame.withSource(sourceServer).withTimestamp(now));
    debug(
      "Forwarded storage request for " + playerId + " from " + sourceServer +
        (cached == null ? "" : " (stale cache fallback)")
    );
  }

  private void receiveResponse(String sourceServer, NetworkFrame frame, long now) {
    cache.put(frame.playerId(), frame.payload(), now);
    int delivered = fulfillPending(frame.playerId(), frame.payload(), now);
    if (delivered == 0 && !frame.target().isEmpty()) {
      sendFrame(frame.target(), frame.withSource(sourceServer).withTimestamp(now));
    }
    debug("Relayed storage response for " + frame.playerId() + " from " + sourceServer);
  }

  private int fulfillPending(UUID playerId, byte[] payload, long now) {
    List<PendingRequests.Pending> requests = pending.remove(playerId);
    int delivered = 0;
    for (PendingRequests.Pending request : requests) {
      if (now >= request.deadline) {
        continue;
      }
      sendResponse(request.server, request.requestId, playerId, payload, now);
      delivered++;
    }
    return delivered;
  }

  private void sendResponse(
    String targetServer, UUID requestId, UUID playerId, byte[] payload, long now
  ) {
    if (targetServer == null || targetServer.isEmpty()) {
      return;
    }
    NetworkFrame response = new NetworkFrame(
      FrameType.RESPONSE, requestId, playerId, "proxy", targetServer, now, payload
    );
    sendFrame(targetServer, response);
  }

  private void sendPong(String sourceServer, NetworkFrame frame, long now) {
    String status = "servers=" + platform.serverNames().size() +
      ",cache=" + cache.size() +
      ",cacheBytes=" + cache.bytes() +
      ",pending=" + pending.size();
    NetworkFrame pong = new NetworkFrame(
      FrameType.PONG,
      frame.requestId(),
      new UUID(0L, 0L),
      "proxy",
      sourceServer,
      now,
      status.getBytes(StandardCharsets.UTF_8)
    );
    sendFrame(sourceServer, pong);
  }

  private void sendFrame(String targetServer, NetworkFrame frame) {
    byte[] encoded = FrameCodec.encode(frame, secret);
    for (byte[] chunk : ChunkCodec.chunk(encoded, NetworkLimits.MAX_CHUNK_BYTES)) {
      platform.sendToServer(targetServer, chunk);
    }
  }

  private void broadcastFrame(String sourceServer, NetworkFrame frame) {
    byte[] encoded = FrameCodec.encode(frame, secret);
    for (byte[] chunk : ChunkCodec.chunk(encoded, NetworkLimits.MAX_CHUNK_BYTES)) {
      platform.broadcastExcept(sourceServer, chunk);
    }
  }

  public int cacheSize() {
    return cache.size();
  }

  public int pendingSize() {
    return pending.size();
  }

  private void warnOnce(String message) {
    long now = System.currentTimeMillis();
    if (now - lastWarning < 60_000L) {
      return;
    }
    lastWarning = now;
    platform.warn("[WuretaProxy] " + message);
  }

  private void debug(String message) {
    if (config.debug()) {
      platform.log("[WuretaProxy] " + message);
    }
  }
}
