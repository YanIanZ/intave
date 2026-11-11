# TeleportRotationRefusal — compatibility and false-positive risk

Scope: `ProtocolScanner/TeleportRotationRefusal` on Wureta Network (SourbyCraft 26.2).
This is a detection-only part: it never cancels packets and never teleports the player.

The decision is now layered: rotation adoption, confirm/revert sequence, combat before adoption,
and refusal history are combined by `AntiBypassEngine`. A single weak exact-revert signal without
sequence/combat/history corroboration is observation-only. See `ANTI-BYPASS-LAYERS.md`.

## 1. Compatibility matrix

| Environment | Behavior | Status |
| --- | --- | --- |
| SourbyCraft 26.2, Java client | Accept is id-only; the confirm `POSITION_LOOK` carries the server rotation; NoRotate reverts in the following movement packet. | **Tested** (Aurora + Folia Build 57 forced suites green) |
| SourbyCraft 26.2, 26.3 client via Via | Accept may carry position/rotation, but `TeleportAcceptReader.positionAndRotation()` is null when the server is <26.3; the part arms and watches movement. | Design path; not separately live-tested |
| 26.3 server, 26.3 client | Combined accept; accept listener runs at `LOWEST` before `TeleportController` `NORMAL` consumes the queue. It accepts the server rotation and watches the next movement packet. | Design path; **not live-tested on 26.3** |
| 26.3 server, 26.2 client | Accept is id-only; movement watcher handles it. | Design path |
| 1.8.8 | No `TELEPORT_ACCEPT` packet; the part never arms. | **No false positive; false negative by design** |
| Geyser/Bedrock | Floodgate players resolve to `TrustFactor.BYPASS`; the part skips them explicitly. | Skip guard added |
| Spectator / camera entity | `AbilityMetadata.ignoringMovementPackets()` is true; skipped. | Skip guard added |
| Vehicle / respawn screen | `MovementMetadata.isInVehicle()` / `inRespawnScreen`; skipped and state reset. | Skip guard |
| Inventory open | Container screens do not rotate the vanilla camera; skipped while `InventoryMetadata.inventoryOpen()` is true. | Skip guard added |
| Just joined | `User.justJoined()`; skipped to avoid join/teleport races. | Skip guard added |
| Relative rotation teleport (`Y_ROT`/`X_ROT`) | Skipped: the expected rotation is resolved against the client view being verified, and `Rotation.filtered` has a pre-existing axis mapping issue. | Explicit skip |
| Multiple pending teleports | Matching teleport id is selected; unmatched accepts do not clear an in-flight assessment. | Implemented |
| Teleport resend | New accept re-arms from the current queue; the tracker timeout clears stale state. | Implemented |
| Folia / SourbyCraft region threads | Packet listeners read volatile metadata only; no lock is held across violation processing. | Build 57 tested |

## 2. False-positive scenarios

| Scenario | Risk | Mitigation / residual risk |
| --- | --- | --- |
| Legit player immediately turns back to the pre-teleport view | Low | Requires a yaw+pitch distance ≤ **0.05°**, server delta ≥ **30°**, and **2** refusals within **30 s**. Accidental exact return is very unlikely. |
| Plugin teleports with the same rotation the player already had | None | `min-server-delta` 30° prevents arming. |
| Plugin teleports with relative rotation / zero-delta setback | None | Relative-rotation teleports are skipped. |
| Player has a container open during the teleport | Medium if metadata is stale | `inventoryOpen()` guard; if the server state says closed while the client screen is open, a legitimate old view could be classified as refusal. |
| Spectator / camera entity | Medium if `hasViewEntity` updates late | `ignoringMovementPackets()` guard; a camera change immediately before the teleport could still race. |
| Respawn / death | Low | `PlayerRespawnEvent` reset + `inRespawnScreen` guard; a teleport before the event could arm briefly. |
| Vehicle / mounted | Low | `isInVehicle()` guard; `VEHICLE_MOVE` has no rotation-bearing `LOOK`/`POSITION_LOOK`. |
| Geyser/Bedrock rotation handling | Low | BYPASS trust factor skip; if a Bedrock player is not BYPASS, treat as a residual risk. |
| Client-side "restore view" or cinematic/freecam feature | Inherent | Only an exact return to the pre-teleport view is detected; a third view is INCONCLUSIVE and resets. A client feature that deliberately keeps the old server rotation is indistinguishable from the cheat. |
| Plugin sends absolute 0/0 rotation while the player had a different view | Low/Medium | This is a legitimate refusal-shaped event; a player who does not move the mouse would be flagged. Server plugin behavior should be audited; the check cannot know the plugin's intent. |
| Player uses the same fixed look angle repeatedly in a minigame | Very low | The server delta and exact-return requirements still apply; if the server intentionally teleports to a fixed angle different from the player's fixed angle, the player must return to the old angle twice within 30 s. |

## 3. False-negative scenarios

| Scenario | Status |
| --- | --- |
| Client sends only `POSITION`/`FLYING` after the teleport (no rotation) | Not detectable; the part waits, then times out. |
| Relative-rotation teleport | Skipped by design. |
| Multiple pending teleports with an id-less entry | Matching may fail or select the wrong entry; state is cleared on timeout. |
| `isAllowed()` false at accept (resend transaction not yet acknowledged) | Part does not arm; a later accept may arm. |
| 1.8.8 | No `TELEPORT_ACCEPT`; no arm. |
| Client nudges more than 0.05° from the old view | Not flagged; the exact-revert signal is intentionally strict. |
| Third-view packet between adoption and revert | Tracker resets on INCONCLUSIVE; a client that wiggles through a third angle evades. |
| Revert after 20 rotation packets / 5 s | Timeout; not flagged. |

## 4. Real risk assessment

- **Java 26.2, normal server plugins:** low false-positive risk. The exact 0.05° revert and 30° server
  delta make accidental matches unlikely; the two-refusal/30 s window limits accumulation.
- **Mixed clients (Bedrock/Geyser), inventory screens, spectator/camera, 26.3:** medium residual risk
  until those paths are live-tested. The new skip guards reduce the most likely cases, but metadata
  timing (camera/inventory update order) can still race.
- **Server plugin behavior:** a plugin that sends an absolute rotation different from the player's view
  while the player is not controlling the camera (inventory/cinematic) is the strongest remaining
  false-positive source.
- **Detection value:** high confidence when it fires on Java clients; it is not a complete NoRotate
  detector because position-only clients and relative rotations are invisible.

## 5. Configuration

```yaml
check:
  protocolscanner:
    teleport-rotation-vl: 10
    teleport-rotation-refusals: 2
    teleport-rotation-epsilon: 0.05
    teleport-rotation-min-delta: 30.0
    teleport-rotation-timeout: 20
    teleport-rotation-cooldown: 1000
    teleport-rotation-refusal-window: 30000
    teleport-rotation-observe-only: false
```

- `teleport-rotation-vl: 0` disables the part without removing the check from the parity baseline.
- Raise `epsilon` only with a recorded false-negative test; lower it only if a real false positive is
  reproduced.
- Raise `refusals` or lower `refusal-window` if Wureta sees repeated borderline teleports.

## 6. Verification evidence

- `TeleportRotationAssessmentTest` (10 tests) and `TeleportRotationTrackerTest` (12 tests).
- `TeleportRotationRefusalTests`: in-server synthetic packet test (accept, combat action, server-view
  confirm, revert) asserting the layered decision.
- `AntiBypassEngineTest` and `TeleportBypassLayersTest` for policy/layer aggregation.
- Full unit suite, `testShadedJar`, `testProtocolResources`, `verifyJava8Bytecode`, `verifyBundledJar`.
- Build 57 SourbyCraft: Aurora and Folia forced suites both
  `All integration tests completed successfully.`
- Parity baseline: 12 checks / 54 active parts.
- Observation telemetry: `SUSPICIOUS` decisions and coverage gaps go to the `DEBUG_TELEPORT`
  verbose channel; violations include the same evidence summary.
- **Not yet covered:** a live 26.3 server, a real Bedrock/inventory/camera teleport scenario, and a
  production false-positive sample.
