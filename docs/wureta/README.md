# Wureta Network branch

This branch is the Wureta Network workspace on top of the SourbyAPI integration
(`feature/sourbyapi-parity`). It does not change check registration or default enablement.

## Documents

- [`CLIENT-THREAT-MODEL.md`](CLIENT-THREAT-MODEL.md) — Meteor Client, Wurst7, and LiquidBounce
  behavior mapped to Intave's checks.
- [`OVERLAP-MATRIX.md`](OVERLAP-MATRIX.md) — **read this first**: existing Intave owners, duplicate
  work to avoid, dead-code weaknesses, and genuine gaps.
- [`DETECTION-BACKLOG.md`](DETECTION-BACKLOG.md) — non-overlapping P0/P1/P2 work with files, signals,
  tests, and acceptance criteria.
- [`REA.md`](REA.md) — how to complete artifact-level REA/JEB analysis when the JEB provider is
  available.

## Current state

- Branch base: `feature/sourbyapi-parity` (`2c2142dd`), which includes:
  - SourbyAPI `AuroraScheduler` default with Folia fallback.
  - Detection parity gate (12 checks / **54 active parts** after `TeleportRotationRefusal`).
  - Java 8 bytecode and bundled-JAR guards.
  - Build 57 Aurora and Folia forced test runs green.
- `ProtocolScanner/TeleportRotationRefusal` (P2-1) is implemented: detection-only teleport
  rotation-refusal part plus pure `TeleportRotationTracker`/`TeleportRotationAssessment` helpers.
- The remaining work is the overlap-aware P0 list in `DETECTION-BACKLOG.md`; it starts by fixing
  existing click-pattern gates and the AttackRaytrace rotation snapshot rather than adding duplicate
  checks.

## Guardrails

- Do not remove, rename, or default-disable a check or active check-part.
- Do not name clients in detection logic; detect behavior.
- Do not add checks for client-side-only features (Wurst `ReachHack`, client-side
  `AntiKnockback`).
- Every detection change needs a unit test and, for movement/combat, a `.ptr` replay.
- Scheduler-touching changes need both Build 57 Aurora and Folia forced test runs.

## Server assumptions still to confirm

1. Exact SourbyCraft build and Minecraft version for Wureta.
2. Check thresholds, punishment policy, and any intentionally disabled checks.
3. Multi-account scope for the first iteration.
4. Any custom client/mod policy.

## Suggested first implementation order

1. Fix `ClickPatterns`'s ≥1.13 gate (existing parts, no new check).
2. Add the attack-time rotation snapshot to `AttackRaytrace` (no new Heuristics part).
3. Measure `MoveTickLimit` vs criticals before touching `Physics`.
4. Measure `PlayerTime` vs TickBase before touching `Timer`.
5. Extend `MicroBlink` for KeepAlive/action continuity.
6. Extend the existing `MovementDispatcher`/`Physics` onGround compare.
7. Fix placement/break dead parts, then one inventory churn part.
