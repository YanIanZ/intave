# Wureta — Intave overlap and weakness matrix

This document prevents duplicate detection work. Every client behavior is assigned to an existing
Intave owner. New parts are justified only when no existing part can own the signal.

## 1. Existing owners by signal

| Signal | Existing owner(s) | Already covers | Do not add |
| --- | --- | --- | --- |
| Movement simulation, velocity, flight, speed, step, teleport motion | `check/movement/Physics.java` + `module/dispatch/MovementDispatcher.java` | per-packet simulation, velocity branch, teleport-unsafe distance, collision/inside-block, fall distance | a new movement check |
| Timer rate / packet cadence | `check/movement/timer/PlayerTime.java` | movement-packet clock drift (`moved too frequently`), whole-client blink, 50 ms-per-packet credit | a new timer rate check |
| Lag / micro-blink | `check/movement/timer/MicroBlink.java` | gap histogram while moving, entity proximity, attack recency, entity-aligned tail | a new blink check |
| Multiple movement packets per client tick | `check/other/protocolscanner/MoveTickLimit.java` | counts packets per `CLIENT_TICK_END`, cancels 2nd+, VL 5 | a new multi-packet check |
| Rotation aim/accuracy/snap | `Heuristics` parts: `RotationStandardDeviation`, `RotationAccuracyYaw`, `RotationExact`, `AccuracyHitboxCorner`, `AccuracyLongTerm`, `RotationSnap`, `RotationModuloReset`, `RotationSensitivity` | perfect-yaw gap, SD, exact/closest yaw, hitbox-corner, long-term accuracy, snap patterns, 45°/180° resets, sensitivity GCD | another perfect-aim/rotation-accuracy part |
| Swing/attack order | `PacketOrderSwingHeuristic`, `NoSwingHeuristic`, `PreAttackHeuristic`, `AttackRequiredHeuristic` | attack without swing, swing without attack, pre-attack ratio, 1.8 attack-required | a new swing-presence part |
| Reach / out-of-sight | `check/combat/AttackRaytrace.java` | double-MDF raytrace, block constraint, entity history, item reach, hitbox margin, pending/latency feedback | a new reach check |
| Click timing / CPS | `ClickPatterns` parts (`Deviation`, `Entropy`, `Fluctuation`, `Repetitive`, `Kurtosis`, `EqualDelay`, `Bursts`) + `ClickSpeedLimiter` | interval SD/entropy/periodicity/kurtosis, bursts, 20-slot CPS | a new click-rate check |
| Placement rotation/cadence | `PlacementAnalysis` parts (`Constraint`, `SmartSpeed`, `Stability`, `Speed`, `Sneak`, `Snap`, `SharpRotation`, `BlockRotation`, `RotationSpeed`, `Facing`, `RoundedRotation`, `AngleSnap`, `RotationFlick`) | under-player cadence, steep-pitch fast place, snaps, 180° turns, rotation budget, 45° alignment, micro-pitch | a new scaffold/fast-place check |
| Placement/break validity | `check/world/InteractionRaytrace.java` + `interaction/*` | packet-held validation against next movement, block/face/air-hit, speculative undo, block trust chain | a new placement-validity check |
| Break timing / restart | `BreakSpeedLimiter` parts `CompletionDurationCheck`, `RestartCheck` | too-fast completion, STOP→START restart, ABORT reset | a new break-speed check |
| Inventory clicks | `InventoryClickAnalysis` parts `OnMoveCheck`, `NotOpenCheck`, `DelayAnalyzer`, `RegrDelayAnalyzer`, `PacketDelayAnalyzer`, `AutoTotem` | click while moving, click while closed, delay/regression, pickup→offhand | a new silent-open or auto-totem check |
| Slot changes | `ProtocolScanner/SentSlotTwice` + `BlockingHeuristic` held-item operations | same slot twice, >1 item operation per tick in some states | a new slot-change check |
| Cloud | `check/other/Cloud.java` | storage/name only; no detection behavior | a new Cloud feature without data |

## 2. Client behavior → owner → action

| Client behavior | Owner | Action | Why not a new check |
| --- | --- | --- | --- |
| Silent/server-only rotation | `AttackRaytrace` + existing rotation parts | **Extend AttackRaytrace** to snapshot yaw/pitch at attack arrival and use that snapshot in `fireRaytraceFor` | 8 rotation parts already cover aim quality; the gap is attack-time snapshot, not another accuracy metric |
| Attack-before-rotation ordering | `AttackRaytrace` (primary); `PacketOrderSwingHeuristic` only for swing/punch order | Snapshot rotation in `Attack`; if ordering remains invisible, extend `PacketOrderSwing` | Both own the relevant packet state; a new part would duplicate |
| Perfect hitbox-corner aim | `RotationExact`, `RotationAccuracyYaw`, `AccuracyHitboxCorner`, `RotationStandardDeviation` | No new work; tune only if evidence shows a miss | Five parts already overlap this signal |
| Criticals micro-position packets | `MoveTickLimit` (extra movement packets) + `Physics` continuity | **Measure first:** replay a 26.2 criticals sequence; if `MoveTickLimit` cancels it, no new check; otherwise extend `Physics` | Adding a micro-move check now would double-flag `MoveTickLimit` |
| Mace √500 / teleport bursts | `Physics` + `MovementDispatcher` | Extend `Physics` continuity/burst shape; keep `MovementDispatcher` >50-block rejection | Movement simulation owns impossible motion |
| TickBase timer bursts | `PlayerTime` | **Measure first:** test charge/spend cadence against the existing 50 ms/packet clock; add a window only if it passes | `PlayerTime` already flags excess movement packets; a new rate check would overlap |
| Blink / FakeLag / Backtrack / selective lag | `MicroBlink` | Extend `MicroBlink` with KeepAlive/action continuity and flush correlation; keep existing entity/attack gates | `MicroBlink` already owns lag-vs-combat correlation |
| onGround spoofing | `MovementDispatcher` single-packet compare + `Physics` | Extend the existing compare to N-tick consistency; no new part | The compare already builds a `Physics` violation and rewrites the flag |
| Reach / closest hitbox | `AttackRaytrace` | Verify `applicable-thresholds.reach` and closest-point measurement; no new check | `AttackRaytrace` already owns reach and item range |
| AutoTotem | `InventoryClickAnalysis/AutoTotem` | No new work | Already keys pickup→offhand <100 ms |
| Silent-open inventory | `InventoryClickAnalysis/NotOpenCheck` | No new work; verify `forceInventoryOnClickOpen` and 26.2 achievement support | Already flags click while closed and cancels |
| Same-tick pick/place, hotbar/armor/offhand churn | `RegrDelayAnalyzer` (remove SWAP exclusion) or one new part under `InventoryClickAnalysis` | Extend or add **one** part; do not add a second silent-open check | `RegrDelayAnalyzer` currently skips SWAP/QUICK_CRAFT; `NotOpenCheck` owns silent-open |
| Auto-drop cadence | `InventoryClickAnalysis` | Add one part (or extend `RegrDelayAnalyzer`) after the churn part | No existing part tracks drop spam |
| Flat-pitch fast place | `PlacementAnalysis/Speed` | Extend `Speed` beyond the under-player restriction | `Speed`/`Sneak` already own placement cadence |
| Sneak-spoof placement | `PlacementAnalysis/SmartSpeed` | Dispatch the existing `sneakVL`; do not add `SneakAndPlace` | `SmartSpeed` already tracks sneak↔place deltas |
| Rotation-callback placement | `InteractionRaytrace` | No new work; it already holds packets and validates against the next movement | Already owns the placement raytrace |
| PacketMine / ABORT restart | `BreakSpeedLimiter/RestartCheck` | Stamp `blockBreakTick` on ABORT; extend `CompletionDurationCheck` per-tick progression | Both own break timing |
| Slot churn / AutoSword / AutoTool | `SentSlotTwice` + `BlockingHeuristic` | Extend to distinct-slot churn per tick; no new part | `SentSlotTwice` owns same-slot; `BlockingHeuristic` owns per-tick item ops |
| 26.2 click timing (human/constant) | `ClickPatterns` | **Fix the `makeDetection` ≥1.13 gate**; reuse existing parts | All seven parts already exist; they are disabled on modern clients |
| Triggerbot crosshair-enter→attack | `PreAttackHeuristic` / accuracy parts | Measure the existing pre-attack counters; extend only if a gap is proven | `PreAttack` already owns swing/attack-on-entity timing |
| Teleport-rotation refusal / NoRotate | `ProtocolScanner/TeleportRotationRefusal` | **Implemented** (one part; pure tracker/assessment helpers) | Genuine gap; no rotation-accuracy part validates server rotation-set confirmation |
| Multi-account / Swarm | `Cloud` | Cloud-side experiment; no new top-level check | Cloud has no detection behavior yet |
| Anti-AC packet canceller / NoSlotSet | `ProtocolScanner` | Add protocol invariants only after the teleport-rotation part | Same owner |

## 3. Do-not-build list (duplicates)

Do not add any of these; they already have an owner:

1. A new perfect-aim / rotation-accuracy / hitbox-corner check.
2. A new reach or out-of-sight check.
3. A new movement simulation / flight / speed / step check.
4. A new timer-rate check without first testing `PlayerTime`.
5. A new blink check without first extending `MicroBlink`.
6. A new silent-open inventory check.
7. A new auto-totem check.
8. A new scaffold / rotation-callback placement check.
9. A new swing-presence check.
10. A new CPS check.
11. A new break-speed check.
12. A new slot-change check.

## 4. Weaknesses / dead code to fix first

These are higher-value than new checks and avoid overlap:

| File | Weakness | Action |
| --- | --- | --- |
| `check/combat/ClickPatterns.java:54-57` | `makeDetection` returns for protocol ≥1.13, disabling all click-timing parts on 26.2 | Re-enable with trust/threshold gates; keep 1.8 behavior |
| `clickpatterns/EqualDelay.java:56` | flag commented out | Enable and validate against tick-aligned constant clicks |
| `inventoryclickanalysis/DelayAnalyzer.java:93-98` | violation built but never processed | Process the violation |
| `placementanalysis/Snap.java:155-163` | aligned-snap violation built but `return` before processing | Process or remove |
| `placementanalysis/Facing.java:74-77` | repeated-fault violation commented | Process or remove |
| `placementanalysis/SmartSpeed.java` | `sneakVL` never dispatched | Dispatch it or remove the field |
| `placementanalysis/Constraint.java:71-74` | empty flag body | Implement or remove the part from registration |
| `placementanalysis/RoundedRotation.java:70-74` | debug-only condition | Implement or remove |
| `heuristics/other/PacketPlayerActionToggleHeuristic.java:91-102` | flag commented; part inert | Implement or remove |
| `heuristics/other/AttackReduceIgnoreHeuristic` | never registered | Remove or wire with evidence |
| `heuristics/other/CivbreakHeuristic.java:41-55` | pre-1.14 only; TODO 1.14+ | Update for 26.2 or remove |
| `breakspeedlimiter/RestartCheck.java:132-136` | ABORT clears state without stamping `blockBreakTick` | Stamp on ABORT |
| `breakspeedlimiter/CompletionDurationCheck.java:59-84` | per-tick damage loop commented | Restore per-tick progression |
| `inventoryclickanalysis/PacketDelayAnalyzer.java` | inert broadcast | Implement or remove |
| `protocolscanner/SkinBlinker.java:47-49` | returns for ≥1.20.2 | Update or remove |
| `movement/Timer.java:37-39` | `tolerance`/`block-pulse-blink` dead keys; `blink-limit:5` raised to 60 | Wire or remove |
| `check/combat/AttackRaytrace.java:891-915` | raytrace uses end-of-tick rotation, not attack-time rotation | Snapshot in `Attack` |
| `check/movement/timer/PlayerTime.java:139` | 50 ms credit per movement packet; extra 26.2 packets can look like timer speed | Verify against `MoveTickLimit`; do not double-flag |
| `protocolscanner/MoveTickLimit.java` | cancels 2nd+ movement packet; criticals micro-packets may be blocked here | Test criticals; avoid a second micro-move check |

## 5. Genuine gaps (new part allowed)

Only these have no existing owner:

1. **Teleport-rotation refusal / server-state rejection** → **implemented** as
   `ProtocolScanner/TeleportRotationRefusal` (one part, no top-level check); pure state machine in
   `TeleportRotationTracker`.
2. **Same-tick inventory pairing / hotbar/armor/offhand churn** → one new part under
   `InventoryClickAnalysis` (or extend `RegrDelayAnalyzer` after removing its SWAP exclusion).
3. **Auto-drop cadence** → same owner as (2), not a separate check.
4. **Attack-time rotation snapshot** → extension of `AttackRaytrace`, not a new part.
5. **Selective lag with KeepAlive/action continuity** → extension of `MicroBlink`, not a new part.
6. **N-tick onGround consistency** → extension of the existing `MovementDispatcher`/`Physics` compare.

## 6. Revised execution order

1. **Fix existing gates/dead code first** — especially `ClickPatterns` 1.13 gate and the dead
   placement/inventory flags. This yields detection without new overlap.
2. **AttackRaytrace attack-time rotation snapshot** — closes attack-before-rotation without a new
   Heuristics part.
3. **Measure `MoveTickLimit` vs criticals** — only extend `Physics` if the packet is not already
   cancelled/flagged.
4. **Measure `PlayerTime` vs TickBase** — only add a window if the existing clock misses it.
5. **Extend `MicroBlink`** for KeepAlive/action continuity.
6. **Extend `MovementDispatcher`/`Physics`** for N-tick onGround.
7. **Extend `RestartCheck`/`CompletionDurationCheck`** for ABORT and per-tick progression.
8. **Fix placement dead parts + flat-pitch `Speed` + `SmartSpeed.sneakVL`**.
9. **One inventory churn/same-tick part**; `NotOpenCheck` stays the silent-open owner.
10. **One `ProtocolScanner` teleport-rotation part** — done (`TeleportRotationRefusal`).
11. **Cloud multi-account experiment** only after the above.
