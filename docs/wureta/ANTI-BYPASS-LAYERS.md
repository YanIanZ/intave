# Anti-bypass layer system

Wureta uses layered detection instead of one predicate per cheat. A bypass of one layer must still
survive the other independent layers, and the engine records coverage gaps when a layer cannot see
the event.

## 1. Why layers

The client analysis showed the same behavior can be hidden from different signals:

- silent/server-only rotations hide the camera change from the client view;
- position-only packets hide the rotation revert entirely;
- confirm/adopt ordering can remove the evidence before a single check sees it;
- attacks/placements can happen while the old view is still active;
- repeated bypass attempts can look like isolated events.

A single check sees one of those. The layer system combines independent evidence and makes the
decision explicit.

## 2. Core model

Package: `check/other/protocolscanner/bypass`.

| Class | Responsibility |
| --- | --- |
| `AntiBypassLayer<C>` | One independent evaluator; returns a result, never mutates. |
| `AntiBypassResult` | `CLEAN`, `OBSERVE`, `SUSPICIOUS`, `VIOLATION`, `UNKNOWN` + confidence + details. |
| `AntiBypassEngine<C>` | Runs all layers, aggregates by policy, reports coverage gaps. |
| `AntiBypassPolicy` | Violation confidence (0.85), suspicious confidence (0.60), corroborating layers (2). |
| `AntiBypassDecision` | Verdict, confidence, per-layer results, coverage gaps, evidence summary. |

Decision rules:

1. One `VIOLATION` with confidence ≥ 0.85 → `VIOLATION`.
2. Two or more `SUSPICIOUS`/`VIOLATION` layers ≥ 0.60 → `VIOLATION` (corroboration).
3. One `SUSPICIOUS` layer ≥ 0.60 → `SUSPICIOUS` (observation, no punishment).
4. Only `UNKNOWN`/`OBSERVE` → `OBSERVE` and record the missing layers as coverage gaps.
5. Otherwise `CLEAN`.

`UNKNOWN` is important: a layer that cannot see the event must not pretend it was clean.

## 3. Teleport layers

`TeleportRotationRefusal` uses four layers:

| Layer | Signal | Status / confidence |
| --- | --- | --- |
| `rotation_adoption` | server rotation was adopted then reverted, or exact pre-teleport view was kept | VIOLATION 0.95 / SUSPICIOUS 0.80 |
| `confirm_revert_sequence` | confirm packet carried the server rotation, next rotation packet reverted | VIOLATION 0.90 |
| `combat_before_adoption` | attacks before the client adopted the server view | SUSPICIOUS 0.80 (≥3) / 0.65 (≥1) |
| `refusal_history` | repeated refusals within the 30 s window | SUSPICIOUS 0.75 (≥3) / 0.60 (≥2) |

The tracker still enforces the two-refusal/30 s baseline. A first weak signal is observation-only;
an attack before adoption or a second refusal can corroborate it. This is the anti-bypass property:
the client must defeat rotation adoption, packet sequence, combat timing, and history at the same
time.

## 4. Bypass coverage matrix

| Bypass | Layer(s) that can still see it | Remaining gap |
| --- | --- | --- |
| Exact old rotation after adopt | adoption + sequence | none for the exact case |
| Nudge > 0.05° from old view | history / combat | exact-revert layer misses; observation only |
| Position-only packets, no rotation | combat (attack before adoption) / history | no rotation evidence if the client never attacks |
| Confirm packet only | sequence + history | tracker needs a later rotation-bearing packet |
| Third view between adoption and revert | adoption resets | deliberate ambiguity; FN |
| Relative-rotation teleport | none | skipped by design |
| Multiple pending teleports | matching by ID | id-less legacy entries may miss |
| Inventory/camera/vehicle | skip guards | no false positive, deliberate FN |
| 1.8 | none | no `TELEPORT_ACCEPT` |
| Repeated attempts | history + engine | if spaced beyond 30 s, no history corroboration |

## 5. Integration

- The layers are internal to the existing `ProtocolScanner/TeleportRotationRefusal` part; no new
  top-level check and no new parity row beyond the part itself.
- The part builds a `TeleportBypassContext` from the tracker:
  server/client deltas, adoption, revert, confirm evidence, attacks before adoption, refusals in the
  window.
- The engine returns an `AntiBypassDecision`; only `shouldFlag()` produces a `Violation`.
- `evidenceSummary()` is included in the violation details, so the ledger shows which layers fired.
- `coverageGaps` can be logged or exported for tuning; it is not a violation by itself.

## 6. Future layers

The same engine can be reused for other checks without changing the policy:

- movement: packet-rate, simulation, onGround, combat correlation;
- inventory: container state, click state machine, hotbar churn;
- placement: rotation callback, fast-place, sneak, interaction raytrace;
- click: entropy, interval shape, triggerbot latency;
- cloud: cross-account correlation.

Each new layer must be independent and must return `UNKNOWN` when its evidence is absent. Do not
reuse the same packet signal under two layer names: that is duplicate scoring, not corroboration.

## 7. Validation

- `AntiBypassEngineTest`: high-confidence violation, two-layer corroboration, one-layer suspicious,
  unknown coverage gap.
- `TeleportBypassLayersTest`: adoption+revert, sequence, combat+history, single-signal suspicious,
  clean/observe.
- `TeleportRotationAssessmentTest` and `TeleportRotationTrackerTest`: pure state.
- `TeleportRotationRefusalTests` (in-server `IntegrationTests`): synthetic teleport accept, combat
  action, server-view confirm and revert; asserts the layered decision flags.
- Full unit suite, shaded/protocol distribution tests, bytecode/bundle guards.
- Build 57 SourbyCraft Aurora + Folia forced suites must stay green.

`SUSPICIOUS` decisions and coverage gaps are sent to players with the `DEBUG_TELEPORT` verbose
channel; a violation includes the same evidence summary plus gaps in its details. This is the
observation path until the live data justifies enforcement.

## 8. Rollout

- Start with `teleport-rotation-vl: 10` (observation-friendly) and inspect `evidenceSummary()` /
  coverage gaps.
- A `SUSPICIOUS` decision should be logged, not punished; only `shouldFlag()` creates a violation.
- If a layer repeatedly produces `UNKNOWN` in production, add the missing evidence rather than
  lowering its status.
- Keep the layer weights and policy in code until the live data justifies config exposure.
