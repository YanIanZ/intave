# Wureta — non-overlapping detection backlog

Read [`OVERLAP-MATRIX.md`](OVERLAP-MATRIX.md) before starting. The matrix assigns every client
behavior to an existing Intave owner and lists the "do not build" duplicates.

## Principles

1. **One owner per signal.** Extend the existing check/part named in the matrix; do not add a second
   part for the same packet or state.
2. **Fix dead code before adding checks.** Several existing parts already implement the signal but
   never dispatch a violation (see matrix §4). Fixing those is higher value and zero overlap.
3. **Measure before building.** Criticals/TickBase may already be covered by `MoveTickLimit` /
   `PlayerTime`; replay a 26.2 sequence and record the result before writing a new detector.
4. **No new top-level checks.** New parts go under an existing `Check` (Heuristics, ProtocolScanner,
   InventoryClickAnalysis, etc.).
5. **Parity gate stays green.** Do not remove/rename/default-disable a check or active part; update
   `baseline-units.txt` only in a reviewed change.

## P0 — fix existing detection first

### P0-1 Enable click-timing checks on modern clients

- **Owner:** `src/main/java/de/jpx3/intave/check/combat/ClickPatterns.java`.
- **Weakness:** `makeDetection` returns for protocol ≥1.13 (`:54-57`), so `Deviation`, `Entropy`,
  `Fluctuation`, `Repetitive`, and `Kurtosis` are inactive on 26.2. `EqualDelay` also has its flag
  commented (`:56`).
- **Action:** replace the blanket 1.13 return with a trust-factor/threshold gate; enable `EqualDelay`
  against tick-aligned constant clicks. Keep `Bursts` behavior (it already bypasses the gate).
- **Test:** replay a human log-normal click sample and a constant-period sample; assert separation.
  Add a 26.2 auto-clicker recording.
- **Acceptance:** modern-client human clicks do not flag; constant/low-entropy timing flags.
- **Overlap guard:** do not add a new click-timing part; reuse the seven existing parts.

### P0-2 Attack-time rotation snapshot in AttackRaytrace

- **Owner:** `src/main/java/de/jpx3/intave/check/combat/AttackRaytrace.java`.
- **Weakness:** `fireRaytraceFor` uses `movementData.rotationYaw/lastRotationYaw` at processing time
  (`:891-915`), so an attack that arrives before its rotation packet is validated against the
  end-of-tick rotation. Meteor KillAura attacks in `TickEvent.Pre`, before the rotation packet.
- **Action:** store yaw/pitch/lastYaw/lastPitch in `Attack` at packet arrival; use the snapshot in
  `fireRaytraceFor` for queued attacks. Keep the existing reach/hitbox logic untouched.
- **Test:** synthetic `ATTACK, ROT` vs `ROT, ATTACK`; assert the first is flagged and vanilla
  strafing/aim is not. Add a `.ptr` recording.
- **Acceptance:** attack-before-rotation flagged; no new perfect-aim part.
- **Overlap guard:** the eight existing rotation parts own aim quality; this item only fixes the
  snapshot. Do not add another rotation-accuracy part.

### P0-3 Verify criticals coverage before extending Physics

- **Owners:** `check/other/protocolscanner/MoveTickLimit.java` and `check/movement/Physics.java`.
- **Question:** on 26.2, `MoveTickLimit` cancels the 2nd+ movement packet per `CLIENT_TICK_END` and
  flags VL 5. Criticals micro-offsets are extra position packets. Does the existing check already
  block/flag them?
- **Action:** replay a 26.2 criticals sequence. If `MoveTickLimit` already cancels/flags it, add no
  new check; document the result. If it does not, extend `Physics` continuity (sub-tick vertical
  offset / `+√500` shape), not a new check.
- **Test:** criticals packet sequence; vanilla jump/crit; mace +√500; assert only the cheats flag.
- **Acceptance:** no duplicate flag path; documented decision.
- **Overlap guard:** do not add a second multi-move-packet check.

### P0-4 Verify TickBase coverage before extending PlayerTime

- **Owner:** `check/movement/timer/PlayerTime.java`.
- **Question:** `PlayerTime` credits 50 ms per movement packet (`:139`) and flags when the clock
  drifts (`:147-170`). Does a 0.9× charge / 5× burst pattern already cross that threshold?
- **Action:** replay a TickBase-like packet cadence. If the existing clock misses it, add a
  short-window rate/distribution check to `PlayerTime`; do not add a new timer check.
- **Test:** steady 1.0×, 0.9× charge, 5× 3-tick burst, legitimate lag/jitter; assert separation.
- **Acceptance:** burst flagged; steady/jitter clean.
- **Overlap guard:** `PlayerTime` and `MicroBlink` own timer/lag; no new top-level Timer part.

### P0-5 Extend MicroBlink for selective lag

- **Owner:** `check/movement/timer/MicroBlink.java`.
- **Weakness:** `MicroBlink` correlates position gaps with entity proximity and attack recency, but
  not with KeepAlive/action continuity or flush bursts. LiquidBounce `VelocityLag` explicitly never
  queues KeepAlive; FakeLag/Backtrack flush on combat events.
- **Action:** add KeepAlive/action continuity and flush-correlation signals to the existing
  histogram/chi² model. Keep the existing horizontal-movement gate and attack recency.
- **Test:** delayed position + immediate KeepAlive/attack; vanilla lag spike with delayed KeepAlive;
  assert separation.
- **Acceptance:** selective lag flagged; normal latency clean.
- **Overlap guard:** `MicroBlink` is the owner; do not add a new blink part.

### P0-6 N-tick onGround consistency

- **Owners:** `module/dispatch/MovementDispatcher.java` (existing single-packet compare) and
  `check/movement/Physics.java`.
- **Weakness:** the dispatcher compares one packet's `onGround` against the simulated state and
  builds a `Physics` violation, but there is no multi-tick consistency check. AntiHunger/Jesus/
  AntiCactus forge plausible sequences.
- **Action:** extend the existing compare with an N-tick contradiction counter; keep the existing
  fall-distance/rewrite behavior.
- **Test:** grounded → spoofed airborne sequence; vanilla jump/landing; assert only spoof flags.
- **Acceptance:** spoof flagged; vanilla clean.
- **Overlap guard:** no new onGround part; the dispatcher/Physics already own the signal.

## P1 — fix dead placement/break/inventory parts

### P1-1 Placement dead parts and flat-pitch cadence

- **Owners:** `placementanalysis/Snap.java`, `Facing.java`, `SmartSpeed.java`, `Constraint.java`,
  `RoundedRotation.java`, `Speed.java`.
- **Action:** process the built-but-dropped violations in `Snap`/`Facing`; dispatch `sneakVL` in
  `SmartSpeed`; implement or remove `Constraint`/`RoundedRotation`; extend `Speed` to flat-pitch
  placement (currently under-player only). Do not enable `SneakAndPlace`/`PacketOrder` as duplicates
  of `SmartSpeed`.
- **Test:** scaffold placement sequence; normal bridging; sneak-tap; assert separation.
- **Acceptance:** dead code either works or is removed; no duplicate part added.

### P1-2 Break restart and per-tick progression

- **Owners:** `breakspeedlimiter/RestartCheck.java`, `CompletionDurationCheck.java`.
- **Action:** stamp `blockBreakTick` on ABORT; restore the per-tick damage progression loop that is
  currently commented; keep the existing STOP-too-fast logic.
- **Test:** ABORT→START, same-tick START/STOP, PacketMine loop; vanilla mining.
- **Acceptance:** restart/progression cheats flagged; vanilla clean.

### P1-3 One inventory churn/same-tick part

- **Owner:** `check/other/InventoryClickAnalysis` (existing `RegrDelayAnalyzer` or one new part).
- **Existing coverage:** `NotOpenCheck` owns silent-open; `AutoTotem` owns pickup→offhand;
  `RegrDelayAnalyzer` owns close-time speed/regression but skips SWAP/QUICK_CRAFT.
- **Action:** remove the SWAP/QUICK_CRAFT exclusion or add **one** part for same-tick pick/place
  pairing, hotbar/armor/offhand churn, and auto-drop. Do not add a second silent-open check.
- **Test:** same-tick pick/place, armor swap without open screen, normal chest looting; assert
  separation.
- **Acceptance:** churn flagged; normal looting clean.

### P1-4 Slot churn extension

- **Owners:** `ProtocolScanner/SentSlotTwice` and `Heuristics/BlockingHeuristic`.
- **Action:** extend `SentSlotTwice` to distinct-slot churn per tick (not only same-slot twice);
  extend `BlockingHeuristic`'s held-item-operation count to 1.9+ combat instead of the stationary
  gate.
- **Test:** AutoSword/AutoTool alternation; normal scroll/swap.
- **Acceptance:** churn flagged; normal clean.

### P1-5 Reach config verification (no new check)

- **Owner:** `AttackRaytrace`.
- **Action:** verify `applicable-thresholds.reach` and closest-point measurement against 26.2 item
  reach. Meteor default KillAura range 4.5 may be at the vanilla boundary; adjust config/tests, do
  not add a new reach check.
- **Test:** 3.0/4.5/6.0 reach cases; closest-point vs center distance.
- **Acceptance:** only > vanilla reach flags.

## P2 — genuine gaps and cleanup

### P2-1 Teleport-rotation refusal (ProtocolScanner)

- **Gap:** `TeleportController.confirmTeleport` validates position but not rotation; NoRotate clients
  revert server-set rotation. No part owns this.
- **Action:** add one `ProtocolScanner` part that compares the confirmed rotation against the
  server-set rotation and the client's prior rotation.
- **Test:** normal teleport; NoRotate-style refusal; assert only refusal flags.
- **Overlap guard:** this is a genuine gap; do not extend rotation-accuracy parts.

### P2-2 Triggerbot measurement

- **Owner:** `PreAttackHeuristic` / accuracy parts.
- **Action:** measure crosshair-enter→attack latency; only extend `PreAttack` if a gap is proven.
  Do not add a new triggerbot part before the measurement.

### P2-3 Cloud multi-account

- **Owner:** `Cloud` (feature pipeline), no new top-level check.
- **Action:** experiment with Swarm-style mirrored targets/actions; require a Cloud-side evaluation
  before enforcement.

### P2-4 Dead-code cleanup

Finish or remove: `PacketPlayerActionToggle`, `AttackReduceIgnoreHeuristic`, `Civbreak` 1.14+ TODO,
`PacketDelayAnalyzer`, `SkinBlinker` ≥1.20.2, `Timer` dead keys, `Constraint`, `RoundedRotation`.
See matrix §4 for the exact files.

## Verification for every item

- `./gradlew test verifyJava8Bytecode verifyBundledJar` green.
- Parity baseline unchanged unless the change intentionally adds/removes a part.
- Movement/combat changes: a replayable `.ptr` recording and a unit test.
- Scheduler-touching changes: Build 57 Aurora and Folia forced suites green.
- Document the "before" behavior: which existing part already flagged the sample, and why the new
  work does not duplicate it.
