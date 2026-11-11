# Wureta Network branch

This branch is the Wureta Network workspace on top of the SourbyAPI integration
(`feature/sourbyapi-parity`). It does not change check registration or default enablement.

## Documents

- [`CLIENT-THREAT-MODEL.md`](CLIENT-THREAT-MODEL.md) — Meteor Client, Wurst7, and LiquidBounce
  behavior mapped to Intave's checks.
- [`DETECTION-BACKLOG.md`](DETECTION-BACKLOG.md) — prioritized P0/P1/P2 detection work with files,
  signals, tests, and acceptance criteria.
- [`REA.md`](REA.md) — how to complete artifact-level REA/JEB analysis when the JEB provider is
  available.

## Current state

- Branch base: `feature/sourbyapi-parity` (`2c2142dd`), which includes:
  - SourbyAPI `AuroraScheduler` default with Folia fallback.
  - Detection parity gate (12 checks / 53 active parts).
  - Java 8 bytecode and bundled-JAR guards.
  - Build 57 Aurora and Folia forced test runs green.
- No production detection code has been added on this branch yet. The first work item is P0-1
  (packet-order/rotation state machine).

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

1. P0-1 packet-order/rotation state machine.
2. P0-2 instantaneous timer.
3. P0-3 selective lag.
4. P0-4 criticals/teleport continuity.
5. P0-5 onGround consistency.
6. P1 inventory, placement, break, reach, slot churn.
