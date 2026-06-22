# Combat Heuristics

This document describes Intave's **classic (on-premise) combat heuristics** — the checks under
`de.jpx3.intave.check.combat.heuristics` that are orchestrated by the
[`Heuristics`](../src/main/java/de/jpx3/intave/check/combat/Heuristics.java) check.

## Philosophy

Intave's *simulation* checks (movement physics, reach/hit-box ray-tracing, timer) **prove** that a
client broke a rule. The heuristics are different: they detect the **statistical fingerprints** that
combat cheats leave behind — patterns that are individually inconclusive but, taken together and
over time, are something a human cannot reproduce.

Because no single heuristic flag is proof on its own, every heuristic feeds one **shared, decaying
violation level**. Weak signals from independent heuristics compound until the configured
thresholds are crossed, at which point notifications, soft combat mitigations ("nerfers") and
ultimately a kick are applied. This design keeps the false-positive rate low while still reacting to
the combination of small tells that characterise modern, well-obfuscated cheats.

## Detection layers

| Heuristic class | Config key | Targets | How it detects | Version scope |
| --- | --- | --- | --- | --- |
| `AccuracyLongTermHeuristic` | `attack-accuracy` | auto-clicker / aura | Long-term swing→hit fail-rate stays implausibly low (<3% over 80 attacks) | all (1.7–26.2) |
| `AccuracyHitboxCornerHeuristic` | `attack-accuracy` | corner-locking aura | Low fail-rate kept despite large turn speed / off-centre aim | all |
| `AttackRequiredHeuristic` | `attack-required` | 1.8 aura | Swing lands on entity with no paired attack packet | **1.8 only** |
| `PreAttackHeuristic` | `pre-attack` | aura | Attacks fire without a cursor-on-target lead-in | all |
| `RotationStandardDeviationHeuristic` | `rotation-accuracy` | aim-assist | Standard deviation of aim error is too small (yaw & pitch) | all |
| `RotationAccuracyYawHeuristic` | `rotation-accuracy` | aim-assist | Multi-layer: follow precision, short/long-term accuracy, hit-box corners, windowed average | all |
| `RotationExactHeuristic` | `rotation-exact` | aimbot | Residual to the perfect angle is exactly `0` | all |
| `RotationSnapHeuristic` | `rotation-snap` | aimbot | Single-tick yaw spike framed by stillness, not mirrored by movement keys ("silent move") | all |
| `RotationSensitivityHeuristic` | `rotation-sensitivity` | aimbot | GCD of pitch deltas loses the stable step a real mouse sensitivity produces | all (**disabled by default**) |
| `RotationModuloResetHeuristic` | `rotation-reset` | silent-aim | Snap onto target then a large jump back off it | all |
| `RotationConstantSpeedHeuristic` | `rotation-constant-speed` | linear-aim / aimbot | Robotically uniform yaw velocity (low CV) while tracking; ships at `0` (observe) | all |
| `PacketInventoryHeuristic` | `inventory-rotations` | inventory-aura / auto-item | Rotation sent while inventory open; open+close within one tick | all |
| `BlockingHeuristic` | `blocking` | 1.8 block-hit | Illegitimate sword block/unblock timing | **1.8 only** |
| `NoSwingHeuristic` | `no-swing` | no-swing aura | Attack lands in a tick with no arm-animation | all |
| `PacketOrderSwingHeuristic` | `swing-order` | packet-mimic aura | Attack packet arrives in a tick with no preceding swing | flying-packet clients |
| `PacketPlayerActionToggleHeuristic` | `sprint-toggles` | w-tap / sprint-reset bots | Multiple sprint/sneak toggles within one movement tick | all |
| `ToolSwitchHeuristic` | `tool-switch` | auto-tool | Automated held-slot swap mid block-break | all |
| `CivbreakHeuristic` | *(mitigation only)* | civbreak fast-break | Drops rogue `STOP_DESTROY_BLOCK` packets | **< 1.14** |
| `CorroborationHeuristic` | `corroboration` | multi-tell cheats (meta) | ≥3 *distinct* heuristics agree within a short window (decaying, graded) | all |

> `AttackReduceIgnoreHeuristic` exists for reference (1.8 sprint-reset) but is **not registered**;
> sprint/knock-back enforcement currently lives in the movement simulation.

## Escalation model

Each heuristic's config value is the **violation-level increase** applied per flag:

* `-1` — heuristic disabled.
* `0` — heuristic runs and its combat nerfers still apply, but it adds no violation level.
* `>0` — flags add this much to the shared heuristics violation level.

The shared level then escalates through `heuristics.classic.thresholds` (defaults):

| Level | Action |
| --- | --- |
| 20 | store log + notify staff (#1) |
| 40 | notify staff (#2) |
| 50 | store log + notify + **kick** |

The cloud pipeline (`heuristics.cloud.thresholds`) escalates to a **ban** at level 50 when enabled.

## Engine internals

The classic heuristics share a small engine layer (`ClassicHeuristic` + `Heuristics`) that turns
individual observations into a confident, corroborated verdict. New checks should build on it rather
than reinventing accumulators.

### Graded confidence

`ClassicHeuristic#flag` accepts an optional confidence in `[0, 1]` that scales the violation level a
flag contributes — `flag(player, details)` is exactly `flag(player, details, 1.0)`. A check that is
only weakly suspicious can flag at, say, `0.3` and add proportionally less, instead of the
all-or-nothing model, with no per-check threshold retuning (`1.0` reproduces the prior behaviour).

### Decaying evidence — `ConfidenceBuffer`

`ConfidenceBuffer` is the engine's reusable accumulator: evidence is *added* and the stored value
*decays exponentially* with a configurable half-life. Bursts of corroborating observations raise
confidence quickly while isolated ones fade on their own — replacing the ad-hoc "+N on suspicion,
−x otherwise" counters checks used to hand-roll. `consumeIfAtLeast(threshold)` is the idiomatic
"release a flag whenever enough evidence has piled up, carrying the remainder forward".

### Cross-heuristic corroboration — `ConfidenceLedger`

Every flag is recorded in a per-player `ConfidenceLedger` shared across all heuristics (keyed by
class in the user metadata pool, so it is created lazily and released automatically). Because
independent detectors agreeing is far stronger evidence than one noisy detector repeating, the
ledger exposes how many *distinct* heuristics flagged a player within a recent window
(`corroboratingHeuristics`) plus the combined, time-decayed `aggregateConfidence`; when more than one
heuristic corroborates, that count is surfaced on the violation for verbose triage. The ledger is an
**additive** intelligence layer — by itself it changes no violation level, so existing tuning is
preserved while new checks can consult corroboration to scale their own confidence.

The ledger's first consumer is `CorroborationHeuristic` (config key `corroboration`): it flags only
when several *distinct* heuristics agree on the same player within the window, accumulated in a
decaying `ConfidenceBuffer` so isolated coincidences fade. Because it requires independent detectors
to corroborate, it is inherently low-false-positive, and it flags with a graded confidence that
scales with the breadth of agreement.

## Version support (through 26.2)

The heuristics consume rotation/attack/movement packets common to every supported protocol, so the
default suite works unchanged across the CI-validated range **1.7 through 26.1.2**, with 26.2
version/protocol recognition in place (full 26.2 runtime support is a follow-up — see below).
Version-specific behaviour is centralised:

* **Server build** — registered in
  [`MinecraftVersions`](../src/main/java/de/jpx3/intave/adapter/MinecraftVersions.java)
  (now including `VER26_1_2` and `VER26_2`) and compared with `MinecraftVersion#atOrAbove()`.
* **Client protocol** — registered in
  [`ProtocolMetadata`](../src/main/java/de/jpx3/intave/user/meta/ProtocolMetadata.java)
  (now including `VER_26_2 = 776`, alongside `VER_26_1_1 = 775`). Feature gates such as
  `sendsInputs()` (≥1.21.3) keep working for 26.2, so the rotation heuristics read **real movement
  key inputs** on modern clients instead of inferring them from motion — making the snap/silent-move
  detection more reliable on 26.x than on legacy versions.

A handful of heuristics are intentionally bound to legacy clients (see the table): the relevant
packet or mechanic — 1.8 sword blocking, the 1.8 swing/attack pairing, pre-1.14 block-break
semantics — does not exist on newer versions, where the corresponding vector is covered elsewhere
(simulation checks or `PreAttackHeuristic`). Players connecting to a 26.2 server through ViaVersion
on an older protocol are still covered by those legacy branches.

Non-finite (`NaN`/`Infinite`) rotation packets are sanitised up-front by
[`InvalidPitch`](../src/main/java/de/jpx3/intave/check/other/protocolscanner/InvalidPitch.java)
in the protocol scanner, so they can never reach — and poison — the rotation maths here.

### 26.2 runtime status

Version and protocol **recognition** for 26.2 is in place (above), and the heuristic logic itself is
packet-based and version-independent. Full 26.2 **runtime (NMS) support** is a tracked follow-up:
26.2 is, at the time of writing, an alpha (`26.2.build.30-alpha`) that renames and relocates large
parts of the server internals Intave reflects into — e.g. `ResourceLocation` was renamed to
`Identifier`, `EntityType.byString(...)` was removed in favour of registry lookups
(`BuiltInRegistries.ENTITY_TYPE`), the entity-id counter moved from `Entity` to `ServerLevel`
(already handled in `IdentifierReserve`), and entity loading moved to the `ValueInput` API. Because
those mappings keep shifting while 26.2 is in alpha, the CI self-test matrix is intentionally gated
at **26.1.2** (matching upstream); 26.2 should be re-validated and the remaining `locate` mappings
audited once it leaves alpha.

## Tuning notes

* Start from the shipped defaults; they are tuned for a low false-positive rate.
* Set a heuristic to `0` to observe its nerfers/behaviour without contributing violation level.
* `rotation-sensitivity` is shipped disabled (`-1`): GCD analysis is sensitive to client/proxy
  rounding and should be enabled and tuned per server before relying on it.
* False positives should always be reported rather than silently worked around — the goal is to fix
  the underlying detection, not to weaken it.
