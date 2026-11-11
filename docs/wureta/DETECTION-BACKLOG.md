# Wureta Network — detection backlog

Derived from `CLIENT-THREAT-MODEL.md`. Every item must preserve the detection parity gate
(`DetectionManifestTest`) and pass the SourbyCraft 26.2 boot suite before it is considered done.

## Branch rules

- Base: `Wureta` from `feature/sourbyapi-parity`.
- Worktree: `/Volumes/Ext/Sourby/intave-wureta`.
- Do not remove, rename, or default-disable a check or active check-part. Update
  `src/test/resources/detection_parity/baseline-units.txt` only in a reviewed change.
- Every movement/combat change needs a replayable `.ptr` recording under
  `src/test/resources/physics_test_runs/` and a unit test.
- Run `./gradlew test verifyJava8Bytecode verifyBundledJar` for every change.
- Run the Build 57 forced suite for scheduler-touching changes:
  `JAVA_TOOL_OPTIONS='-Dintave.test.success=shutdown'` and the `folia` variant.

## P0 — ship first

### P0-1 Packet-order and rotation-context state machine

- **Signal:** attack/use packet whose hit is only valid against a rotation that arrives later in the
  same server tick; held attack+punch pairs (Criticals Jump); exact hitbox-center bearing without
  intermediate rotations.
- **Clients:** Meteor `combat/KillAura.java`, `combat/Criticals.java`, `utils/player/Rotations.java`;
  Wurst `hacks/ClickAuraHack.java`, `RotationFaker.java`; LiquidBounce `ModuleKillAura.kt`,
  `RotationManager.kt`.
- **Location:** `src/main/java/de/jpx3/intave/check/combat/Heuristics.java` plus a new part under
  `check/combat/heuristics/other/`; reuse the existing packet-order subscriptions.
- **Test:** synthetic packet sequence `ATTACK, ROT` vs `ROT, ATTACK`; a `.ptr` recording with held
  criticals. Assert the first is flagged and the second is not.
- **Acceptance:** no false positive on vanilla strafing/aim; Meteor callback-style and Wurst
  ClickAura sequences flagged; parity baseline unchanged.

### P0-2 Instantaneous timer / TickBase detector

- **Signal:** inter-move-packet interval distribution; slow charge followed by 5× bursts; average
  rate near 1.0 but short-window rate above the physical ceiling.
- **Clients:** Meteor `world/Timer.java`; Wurst `mixin/timer/DeltaTrackerTimerMixin.java`;
  LiquidBounce `ModuleTickBase.kt`, `utils/client/Timer.kt`.
- **Location:** `src/main/java/de/jpx3/intave/check/movement/Timer.java` /
  `check/movement/timer/PlayerTime.java`; add a burst-window part, keep the existing long-term
  balance.
- **Test:** synthetic move sequence 20 ticks at 0.9× then 3 ticks at 5×; assert burst flag and no
  flag on a steady 1.0× baseline. Add a `.ptr` recording.
- **Acceptance:** steady 1.0, jitter, and legitimate lag do not flag; burst is flagged within N ticks.

### P0-3 Selective-lag / blink / backtrack detector

- **Signal:** position-packet gaps > threshold while actions (attack/use/click) or KeepAlive continue;
  flush burst after a gap; attacks against a stale hitbox position.
- **Clients:** Meteor `movement/Blink.java`; Wurst `hacks/BlinkHack.java`; LiquidBounce
  `ModuleFakeLag.kt`, `ModuleBacktrack.kt`, `BlinkManager.kt`, `VelocityLag`.
- **Location:** `check/other/ProtocolScanner.java` or a new part under `check/movement/` (prefer
  ProtocolScanner for packet-order evidence; Physics for position continuity).
- **Test:** fake packet queue with delayed positions + immediate KeepAlive; assert flag; a vanilla
  lag spike with delayed KeepAlive should not flag.
- **Acceptance:** no flag on normal latency/jitter; flags combat-correlated flush patterns.

### P0-4 Criticals / MaceKill / teleport micro-move continuity

- **Signal:** sub-tick vertical offsets `+0.0625 / 0 / +1.1e-5 / 0`, `+√500` spikes, multi-block
  teleport bursts with fall-height shaping.
- **Clients:** Meteor `combat/Criticals.java`; Wurst `hacks/CriticalsHack.java`,
  `hacks/MaceDmgHack.java`; LiquidBounce `combat/criticals/`, `ModuleMaceKill.kt`, `tpaura/`.
- **Location:** `check/movement/Physics.java` (continuity) and a micro-move part; use the 26.2 attack
  packet split for correlation.
- **Test:** `.ptr` criticals sequence; synthetic teleport burst; assert flag and no false positive on
  jump/fall/velocity.
- **Acceptance:** crit/mace/teleport sequences flagged; vanilla jump/crit and knockback not flagged.

### P0-5 onGround vs simulation consistency

- **Signal:** serverbound `onGround` contradicts the simulated grounded state for >N ticks while
  vertical velocity is zero; forged StatusOnly/Pos sequences.
- **Clients:** Meteor `NoFall`/`AntiHunger`; Wurst `NoFallHack`, `JesusHack`, `AntiCactusHack`;
  LiquidBounce `NoFallSpoofGround`/`NoFallPacket`.
- **Location:** `check/movement/Physics.java` or `check/other/ProtocolScanner.java`.
- **Test:** synthetic grounded → spoofed airborne sequence; assert flag; vanilla jump/fall not
  flagged.
- **Acceptance:** spoofed ground flags; legitimate jump/landing remains clean.

## P1 — next

### P1-1 Inventory state machine / silent open

- **Signal:** clicks on a container the server never opened; same-tick pick/place pairs; cursor-state
  mismatch; hotbar churn.
- **Clients:** Meteor `InvUtils`, `AutoTotem`, `AutoArmor`, `ChestSwap`; Wurst `AutoTotem`,
  `AutoArmor`, `AutoSteal`, `AutoDrop`; LiquidBounce cheststealer/invcleaner.
- **Location:** `check/other/InventoryClickAnalysis.java` and its parts.
- **Test:** open/close, click, pickup/place sequences; assert silent-open and zero-delay pair flags;
  no flag on normal chest looting.
- **Acceptance:** silent-open detected; randomized delays alone do not evade the state machine.

### P1-2 Placement fast-place / air-place / sneak spoof

- **Signal:** placement cadence ≤4 ticks at the same position; placement beyond reach; air-place;
  forced sneak state in the packet while the client is not sneaking.
- **Clients:** Meteor `Scaffold`; Wurst `ScaffoldWalkHack`, `AutoBuildHack`; LiquidBounce
  `world/scaffold/`.
- **Location:** `check/world/PlacementAnalysis.java` and `check/world/interaction/`.
- **Test:** synthetic scaffold rotation+place sequence; assert fast-place/air-place; no flag on
  normal bridging.
- **Acceptance:** scaffold patterns flagged; vanilla bridge/tower not flagged.

### P1-3 Break progression / PacketMine

- **Signal:** START+STOP destroy in one tick; ABORT/restart loops; skipped damage ticks; reach/LOS
  violation.
- **Clients:** Meteor `PacketMine`, `Nuker`, `InstantRebreak`; Wurst `NukerHack`, `FastBreakHack`;
  LiquidBounce `InstantNukerMode`.
- **Location:** `check/world/BreakSpeedLimiter.java` and its parts.
- **Test:** replay of same-tick START/STOP and ABORT loops; assert flag; vanilla mining not flagged.
- **Acceptance:** packet-mine/instant-break flagged; normal break timing preserved.

### P1-4 AttackRaytrace closest-point reach

- **Signal:** eye→server hitbox closest-point distance > vanilla reach; attack without a valid
  rotation; KillAura default 4.5/5–6 range.
- **Clients:** Meteor KillAura; Wurst Killaura; LiquidBounce KillAura.
- **Location:** `check/combat/AttackRaytrace.java`, `check/world/interaction/`.
- **Test:** vanilla 3.0, 4.5, and 6.0 reach cases; assert only > vanilla flags.
- **Acceptance:** correct closest-point measurement; no false positive on 4.5 vanilla reach.

### P1-5 Slot churn / AutoSword / AutoTool

- **Signal:** ≥2 distinct held-item-slot changes in one tick; rapid alternating slot packets.
- **Clients:** Meteor `AutoSword`, `AutoTool`; Wurst `AutoSwordHack`, `AutoToolHack`.
- **Location:** `check/other/ProtocolScanner.java` (extend `SentSlotTwice`).
- **Test:** synthetic slot-churn sequence; assert flag; normal scroll/swap not flagged.
- **Acceptance:** churn detected; legitimate single swap clean.

## P2 — later

### P2-1 Click entropy / triggerbot latency

- **Signal:** coefficient of variation below human floor over ≥60 clicks; crosshair-enter→attack
  latency ≈0 ms; constant-period technique.
- **Clients:** Meteor AutoClicker; Wurst Killaura/TriggerBot; LiquidBounce Clicker/HumanClickTiming.
- **Location:** `check/combat/ClickPatterns.java`, `ClickSpeedLimiter.java`.
- **Test:** log-normal human sample vs constant; triggerbot sequence; assert separation.

### P2-2 Rotation quantization / aim context

- **Signal:** max Δyaw, absence of sub-0.1° quantization, target-switch correlation, inventory-open
  rotations, 4-tick hold pattern.
- **Clients:** all three rotation systems.
- **Location:** `check/combat/Heuristics.java`; add features to Cloud samples.
- **Test:** replay recorded rotations; compare human vs aura distributions; assert no false positive
  on high-sensitivity humans.

### P2-3 Multi-account / swarm

- **Signal:** mirrored targets/actions, shared IP/session, synchronized attacks.
- **Clients:** Meteor Swarm.
- **Location:** Cloud feature pipeline; no new top-level check without maintainer approval.
- **Test:** synthetic two-account sequence; require a Cloud-side experiment before enforcement.

### P2-4 Anti-anticheat protocol invariants

- **Signal:** dropped move packets, teleport rotation refusal, slot desync, brand spoof.
- **Clients:** Meteor PacketCanceller/NoRotate; Wurst packet cancel; LiquidBounce NoRotateSet/
  NoSlotSet/SpooferClient.
- **Location:** `check/other/ProtocolScanner.java`.
- **Test:** synthetic drop/refusal sequences; assert protocol-state flags.
- **Acceptance:** no false positive on normal lag/reconnect; do not ban on brand alone.

## Non-goals

- Wurst `ReachHack` and client-side `AntiKnockback` `lerpMotion` scaling are **not server-visible**.
  Do not add checks for them.
- Pure ESP/render modules are out of scope unless they change packets or combat behavior.
- Do not name clients in detection logic; detect behavior, not brand.

## Server-specific inputs still needed

1. Wureta's exact SourbyCraft build and Minecraft version.
2. Check thresholds/punishment policy and any checks intentionally disabled.
3. Whether multi-account detection is in scope for the first iteration.
4. Whether Wureta uses a custom client/mod policy that changes the expected packet surface.
