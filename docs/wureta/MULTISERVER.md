# Cross-server synchronisation

WuretaCore can synchronise player storage, staff alerts, trust factors and sanctions across
Paper/Folia backends behind a Velocity or BungeeCord proxy. The proxy runs a small companion
plugin, **WuretaProxy**, and relays versioned, HMAC-signed plugin messages between backends.

This is an optional module. It is disabled until `network.enabled: true` is set on every backend.

## Topology

```text
                 ┌──────────────────────────────┐
                 │  Velocity / BungeeCord       │
                 │  WuretaProxy                 │
                 │  ├─ route broadcasts         │
                 │  ├─ cache storage blobs      │
                 │  └─ persist storage to disk  │
                 └───────┬───────────┬──────────┘
                         │           │
              ┌──────────▼──┐   ┌────▼─────────┐
              │ survival-1  │   │ lobby-1      │
              │ WuretaCore  │   │ WuretaCore   │
              └─────────────┘   └──────────────┘
```

Backends communicate only through the proxy. A backend picks any online player connection to
carry a plugin message to the proxy; the proxy identifies the real source server and forwards the
frame. The reply is delivered to the target backend's registered channel.

## Components

| Component | Location | Purpose |
| --- | --- | --- |
| `NetworkSync` | `src/main/java/de/jpx3/intave/module/network` | Backend module: transport, relays, storage gateway |
| `WuretaProxy` (Velocity) | `proxy/velocity` | Velocity 4.x companion plugin |
| `WuretaProxy` (Bungee) | `proxy/bungee` | BungeeCord/Waterfall companion plugin |
| Protocol | `proxy/common` | Frame codec, chunking, HMAC, router and disk cache |

## Build

```bash
./gradlew buildWuretaProxy
# proxy/velocity/build/libs/WuretaProxy-Velocity.jar
# proxy/bungee/build/libs/WuretaProxy-Bungee.jar

./gradlew shadowJar
# build/libs/WuretaCore-bundled.jar
```

`WuretaProxy-Velocity.jar` targets Velocity 4.x (Java 25). `WuretaProxy-Bungee.jar` targets
BungeeCord/Waterfall (Java 8 bytecode; runs on current Java).

## Setup

### 1. Proxy

Install the matching WuretaProxy jar on the proxy and start it once. It writes
`plugins/wureta-proxy/wureta-proxy.properties` (Velocity) or
`plugins/WuretaProxy/wureta-proxy.properties` (BungeeCord):

```properties
channel=wureta:sync
secret=replace-with-a-long-random-value
cache-ttl-seconds=600
disk-ttl-seconds=604800
storage-directory=storage
max-cache-entries=5000
max-cache-bytes=67108864
pending-timeout-ms=2500
replay-ttl-seconds=300
max-messages-per-second=500
debug=false
```

- `channel` must match every backend.
- `secret` must match every backend. Use a long random value; the default is empty and only
  suitable for a fully trusted private network.
- `storage-directory` is relative to the proxy data folder. It holds the encrypted player storage
  blobs. Protect it like a database directory.
- `disk-ttl-seconds` controls how long a logged-out player's storage is kept (default 7 days).

### 2. Backends

On **every** backend, edit `config.yml` (or `advanced.yml` when `config: ADVANCED`):

```yaml
network:
  enabled: true
  server-id: "survival-1"     # unique per backend
  channel: "wureta:sync"
  secret: "replace-with-the-same-value"
  sync:
    alerts: true
    trust-factor: true
    sanctions: true
    storage: true
  storage:
    request-timeout-ms: 2500
  alert-rate-limit-ms: 1500
  debug: false
```

Restart all backends. The console should show:

```text
[Wureta] Cross-server sync enabled as survival-1 on wureta:sync (alerts=true, trust=true, sanctions=true, storage=true)
```

and the proxy should show:

```text
[WuretaProxy] WuretaProxy enabled on channel wureta:sync with N backend(s)
```

### 3. Verify

```text
/wureta diagnostics network
```

This prints the server id, channel, queued/sent/received/dropped counters and pending storage
requests. It is available to players with `intave.command.diagnostics.performance`.

## What is synchronised

### Storage and violation levels

`StorageLoader` uses `ProxyStorageGateway` when no other storage gateway (for example the Intave
cloud) is installed. On quit, the backend saves the serialised `PlayerStorage` blob to the proxy.
On join, the backend requests the blob; the proxy answers from memory, then disk, or asks the old
backend for a live snapshot. This keeps short-term violation levels, playtime, heuristics, nerfers,
latency and account data across server switches.

`storage.recover-vl` in `advanced.yml` continues to control whether short-term VLs are restored.

### Staff alerts

`ViolationProcessor` publishes a rate-limited alert to other backends when it would also broadcast
a local verbose alert. Receiving backends deliver the alert to local `VIOLATION_FINE` subscribers
with the source server in the prefix. Local staff already receive the original alert; there is no
duplicate on the source server.

### Trust factors

A trust-factor change is published only when it becomes **more restrictive** (for example ORANGE to
RED). Receiving backends apply the more restrictive value to an online player. `BYPASS` is never
overridden or published. This is a "most restrictive wins" policy; clearing a red trust factor does
not propagate automatically.

### Sanctions

- A blacklist disconnect publishes a `BLOCK` sanction. Receiving backends add the address/name/UUID
  to their in-memory blacklist and disconnect the player if online.
- `User.kick` publishes a `KICK` sanction so an anticheat kick on one backend also removes the
  player from another backend.
- Received sanctions are applied directly with `kickPlayer`; they are not re-published, so there is
  no loop.

## Protocol

Channel: `wureta:sync` (configurable).

Frame types: `ALERT`, `TRUST`, `SANCTION`, `DIRECT`, `STORE`, `REQUEST`, `RESPONSE`, `PING`, `PONG`.

Frame layout:

```text
magic "WTS1" | version | type | flags | requestId | playerId | timestamp
| source | target | payloadLength | payload | HMAC-SHA256 (optional)
```

- Strings and payloads are length-limited.
- Frames larger than 30,000 bytes are split into `WTC1` chunks (up to 32 chunks / 256 KiB).
- HMAC-SHA256 covers the complete frame; the proxy verifies the backend signature, replaces the
  source with the real proxy-observed server name, re-signs and forwards.
- Timestamps outside a five-minute window are rejected.
- A replay guard drops repeated request ids.
- The proxy rate-limits each backend (default 500 messages/second).
- The proxy rejects plugin messages sent by players; only backend server connections are accepted.

## Security

- Set a long random `secret` on the proxy and every backend. Empty secrets are accepted only for
  trusted private networks; the backend refuses to start network sync with an empty secret.
- Use a private network or TLS between backends and the proxy. Plugin messages are not encrypted by
  Minecraft; the storage blob itself is compressed and AES-encrypted by `StorageIOProcessor`, but
  that is obfuscation, not a substitute for transport security.
- Protect the proxy `storage` directory. It contains player storage blobs and is writable by the
  proxy process.
- Any compromised backend can inject valid frames. Treat backend hosts as trusted.
- Do not expose the proxy plugin-message channel to untrusted clients; the proxy cancels client
  messages on the channel.

## Limitations

- The proxy disk cache is not a database cluster. A single proxy is assumed. Multiple proxies do
  not share storage.
- Logged-out storage expires after `disk-ttl-seconds` (default 7 days).
- Staff alerts are online-only; they are not persisted for offline staff.
- Trust-factor upgrades and blacklist removals are not propagated.
- Sanctions received by a backend are in-memory; a restart clears them until the source backend
  publishes again.
- Movement, combat and packet state are intentionally not transferred. Only the storage blob,
  trust factor, alerts and sanctions cross servers.
- Proxy restart loses in-memory cache and pending requests; disk storage survives.
- A backend with zero online players cannot send a frame (plugin messages need a player
  connection). Storage saves from a quit are queued until the next join if no other player is
  online.

## Test evidence

- `:wureta-proxy-common:test` covers frame signing/tampering, chunking, replay, router broadcast,
  storage request/response races, pending timeouts, cache eviction and disk persistence.
- `WuretaProxy` boots on Velocity 4.3.1 and BungeeCord 26.1 (`WuretaProxy enabled on channel
  wureta:sync`).
- Build 57 SourbyCraft boots with network disabled and enabled; `/wureta diagnostics network`
  reports the active server id and channel; all integration tests pass.
- Root unit tests, `verifyJava8Bytecode` and `verifyBundledJar` pass.

A live two-backend + real-client switch test still requires a running proxy and clients; run it on
a staging network before production.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Backend log says sync disabled | `network.enabled`, `network.secret` non-empty, config selection (`config: THIS` transfers `network.*` through `ctvs.mx`) |
| Proxy logs signature mismatch | The `secret` differs between proxy and backend |
| Storage request times out | Proxy not installed, channel mismatch, no online player to carry the message, or backend not connected to the proxy |
| Alerts arrive only locally | `network.sync.alerts`, channel/secret, proxy backend list |
| Duplicate alerts | A backend is receiving its own broadcast; check `server-id` uniqueness |
| Proxy cache grows | Lower `disk-ttl-seconds`; inspect `storage-directory` |
