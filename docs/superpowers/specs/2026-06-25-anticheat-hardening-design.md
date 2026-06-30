# Anticheat hardening — LiquidBounce bypass resistance + Baritone detection

Date: 2026-06-25
Branch: `claude/anticheat-hardening` (off `master`)

## Goal

Harden Intave's on-premise combat heuristics so the current LiquidBounce
release cannot trivially bypass them, and add detection of the Baritone
pathfinding bot across the Minecraft versions Baritone ships branches for
(1.13.2 → 1.21.11).

Source material analysed in-repo:

* `../baritone` (branch `1.21.11`, mod `1.17.0`; branches per MC version).
* `../LiquidBounce` (Fabric client; contains Intave-specific bypass modules
  `VelocityIntave`, `PhaseIntave`, `IntaveHeavyAntiBotMode`, plus the rotation
  engine and KillAura evasion options).

## Design principles (unchanged from the engine)

* New **statistical** tells ship at violation level `0` (observe / corroborate
  only) so they feed `corroboration` / `ghost-client` and verbose output
  without adding VL until tuned per server.
* New **hard-invariant** tells (sustained, physically-impossible-when-sustained)
  ship **enforced** with a positive VL.
* Every new tell reuses the engine helpers — `RollingStatistics`,
  `RollingCorrelation`, `ConfidenceBuffer`, `ConfidenceLedger` — rather than
  hand-rolling accumulators, and flags through `ClassicHeuristic#flag(...)` so
  Bedrock exemption and ledger recording are automatic.
* Bedrock players stay exempt for the combat heuristics (engine handles it).

## Part A — LiquidBounce bypass resistance (combat heuristics)

### A1 — `InventoryCloseAttackHeuristic`  (config `inventory-close-attack`, **enforce VL 4**)

LiquidBounce KillAura `simulateInventoryClosing` sends a `CLOSE_WINDOW`
packet ~1 tick before each attack and reopens afterwards, so the server believes
no container is open at attack time and `attack-while-inventory` never sees it.

Detection: a `CLOSE_WINDOW` immediately followed (same tick / ≤ ~1 tick, ~75ms)
by an `ATTACK_ENTITY`, **sustained** (decaying streak via `ConfidenceBuffer`).
A human can close then attack once; doing it before every attack in a run is
automation. Graded confidence scales with the streak. Ships enforced at VL 4 to
match the `attack-while-inventory` invariant family.

### A2 — `FailRotationHeuristic`  (config `fail-rotation`, **observe 0**)

LiquidBounce `FailRotationProcessor` injects deliberate "misses" at a fixed
rate (~3%/tick) as a backwards-blend toward the previous rotation (5–10°
horizontal pulse over 1–4 ticks), then snaps back to the target — purpose-built
to raise `rotation-jitter` / `rotation-entropy` and lower `attack-accuracy`.

Detection: while tracking a moving target with otherwise-tight aim, count
"pulse-then-correct" excursions — a large single-window residual spike that is
immediately corrected back to a small residual. Genuine human error is
continuous; injected fails are discrete, clean, similar-magnitude pulses.
Flags via the ledger at graded confidence. Statistical → observe.

### A3 — Silent-aim ON_TICK sandwich (evaluated → already covered, no new code)

LiquidBounce KillAura `RotationTiming.ON_TICK` sends rotation → attack →
rotation-back inside one tick. On review this is already covered across existing
layers, so a new detector would be redundant and raise false-positive risk
(failing the "only if FP-clean" bar):

* A *pure* silent rotation that never faces the target fails the reach
  **ray-trace** (a simulation check, angle-aware), forcing the client to briefly
  face the target.
* That brief snap-then-revert is caught by `rotation-reset`
  (`RotationModuloResetHeuristic`) on a sustained target, and by `pre-attack`
  (`PreAttackHeuristic`), which sees no genuine cursor-on-target lead-in over its
  window.

Documented in `docs/HEURISTICS.md` ("LiquidBounce bypass resistance"). A
dedicated attack-anchored sandwich detector remains a possible follow-up only if
servers report ON_TICK slipping all three layers.

## Part B — Baritone detection (movement check + combat heuristic)

Server-observable, version-stable Baritone signatures (hold 1.13.2 → 1.21.11):

* `antiCheatCompatibility=true` (default) locks the transmitted `yaw` to the
  travel heading so the bot never sprints sideways. A human decouples view from
  movement constantly (looks around, strafe-walks).
* Pathing follows block-centre lanes; turns happen at block boundaries.
* `blockBreakSpeed=6` / `rightClickSpeed=4` give robotically periodic break/place
  packet intervals.
* `randomLooking` adds always-on micro-jitter (`0.01°`; `randomLooking113=2.0°`
  at 10% on 1.13).

### B1 — `Pathfinder` check  (new `check/movement/pathfinder/`, config `pathfinder`, **observe**)

Standalone movement-domain `Check` registered in `CheckService`. Detector:

* **`HeadingLock`** — over a sustained sprint window, the residual between the
  transmitted `yaw` and the movement heading `atan2(-motionX, motionZ)` stays near
  zero with low spread **through turns** (cumulative yaw change must clear a
  threshold so straight-line auto-runners are not flagged). Pure geometry in
  `BaritoneMovementMath` (unit-tested), accumulation via `RollingStatistics`.

> An `ActionCadence` (break/place periodicity) sub-check was considered and
> **dropped**: vanilla block-break time is deterministic per block/tool, so a
> human mining one block type is as periodic as a bot — not a discriminator,
> false-positive-prone.

Raises VL on the `Pathfinder` check through the normal `Violation` pipeline, so
`GhostClientHeuristic#crossDomainTells` folds it in automatically once VL ≥ 5
(no extra wiring). Catches pure-pathing Baritone that never fights.

### B2 — `BaritoneHeuristic`  (new combat heuristic, config `baritone`, **observe 0**)

On an attack packet, if the player simultaneously exhibits robotic heading-lock
(bot-pathing-while-fighting), record to the `ConfidenceLedger` so it corroborates
into `corroboration` / `ghost-client`. Combat-domain expression of the same
signal; complements B1 for a Baritone user who also fights.

## Version coverage

All Part A/B signals are packet/motion based, so they work across Intave's
supported span (1.7 → 26.2) and every Baritone branch (1.13.2 → 1.21.11). Real
client key inputs are read via `MovementMetadata.input` / `clientForwardKey`
(STEER_VEHICLE; ≥ 1.21.3) and inferred from motion below that. The 1.13
`randomLooking113` larger-amplitude jitter is documented for tuning.

## Testing

Intave's check tests are pure-logic unit tests (see
`check/combat/heuristics/RollingStatisticsTest`, `RollingCorrelationTest`,
`ConfidenceBufferTest`). New detection math (heading residual, cadence
uniformity, fail-pulse scoring) is extracted into pure, unit-tested helpers;
the heuristic/check classes wire those helpers to packets. Full suite via
`./gradlew test`, compile via `./gradlew compileJava`.

## Config keys added (`advanced.yml` → `check.heuristics.classic`)

* `inventory-close-attack: 4`  (enforce)
* `fail-rotation: 0`  (observe)
* `baritone: 0`  (observe)

New top-level check (`advanced.yml` → `check.pathfinder`): `enabled` +
`thresholds`, modelled on the `timer` block.

## Docs

`docs/HEURISTICS.md` gains rows for `inventory-close-attack`, `fail-rotation`,
`baritone`, and a short Baritone-detection section describing the movement check.
