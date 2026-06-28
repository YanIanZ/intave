# LiquidBounce coverage matrix

Grounded audit of every LiquidBounce module category against this Intave fork. Each verdict is
sourced from actual code (`file:line`); nothing is assumed. The goal is honest coverage with a low
false-positive rate — not a per-module detector for things the engine already proves or that are not
server-observable at all.

**Audit method.** Each vector was checked by reading the Intave check that owns it and confirming
whether the specific LiquidBounce technique can beat it. Movement is validated by a **full per-tick
vanilla physics replay** (`Physics` → `PredictiveSimulationProcessor` → `BaseSimulator` →
`SimulationEvaluator`), so most movement cheats are caught generically rather than by signature.

Legend: **COVERED** (engine defeats it, cited) · **COVERED+** (added in this work) ·
**NOT-DETECTABLE** (client-only, no server signal) · **GAP** (real, addressed below) ·
**RESIDUAL** (known hard limit, documented).

## Key reality check

LiquidBounce's `*Intave14` / `*Intave` bypass modules target **Intave 14.8–14.9** (the old public
build). This fork's movement engine is a full simulator with tolerances `0.0007` horizontal /
`0.00001` vertical (`SimulationEvaluator.java:346,45`), so those exploits — which exploited 14.8's
weaker threshold checks — produce motion that diverges from the replay and flag. Several are *also*
covered by dedicated code that literally names the exploit (e.g. `InvalidRelease`).

## Movement

| LB module(s) | Verdict | Owned by | Why the bypass loses |
| --- | --- | --- | --- |
| Speed / BHop, `SpeedIntave14/Fast` | COVERED | `Physics`/`SimulationEvaluator.java:346,508,544-552` | Per-tick replay includes the legit sprint-jump boost and recomputes friction per candidate; +0.003 / 1.02–1.04 boosts exceed the 0.0007 horizontal tolerance → VL. Sprint state is not trusted (search tries both). |
| Fly / Glide, `FlyVulcan/Grim/…` | COVERED | `BaseSimulator.java:750-766`, `SimulationEvaluator.java:257-262` | Gravity + 0.98 drag replayed every tick; sustained lift diverges → ×5000 vertical multiplier. |
| Step | COVERED | `Simulator.java:72-74`, colliders cap at 0.6 | Step is part of the authoritative collision replay; >0.6 climb the collider never produced → `differenceY` flags. |
| NoFall | COVERED | `MovementDispatcher.java:680-699` | `onGround` is server-authoritative (from simulated collider); a spoofed `onGround=true` midair flags **and** is rewritten before fall-damage logic. |
| NoSlow (motion), `NoSlowIntave` | COVERED | item-use slowdown in the replay (`PredictiveSimulationProcessor` handActive search) | Moving faster than the slowed prediction diverges. |
| NoSlow blocking, `NoSlowBlockIntave14` (hit→block-center spoof) | COVERED | `RaytraceEvaluation.java:63-149` | `facingCheckFailed()` compresses the hit-vec to vanilla 1/16 and compares to the sent facing (±0.01); a block-**center** hit ≠ the real face raytrace → `wrongBlockFace`/`positionMismatch`. |
| NoSlow consume, `NoSlowConsumeIntave14` (RELEASE spam) | COVERED | `InvalidRelease.java:31-41` | Flags `RELEASE_USE_ITEM` with face ≠ DOWN; the comment cites this exact exploit. |
| NoWeb, `NoWebIntave14` (jump-strafe oscillation) | COVERED | `WebPhysics.java:26-35`, `SimulationEvaluator.java:169-186` | Web drag replayed; upward motion in web after 2 ticks held to 0.00001 tolerance → escape bursts flag. |
| Phase / Clip / NoClip, `PhaseIntave` | COVERED | collision replay rejects intersecting positions; `CivbreakHeuristic` drops rogue `STOP_DESTROY_BLOCK` (<1.14) | The −0.0052/tick Y drift intersects a solid block → collision flags. |
| Jesus / LiquidWalk | COVERED (verify live) | water physics in `BaseSimulator` | Standing on the water surface without the replayed sink diverges vertically. No dedicated check — confirm under live test. |
| Spider / wall-climb | COVERED (verify live) | collider (no upward Y without ladder/vine) | Upward motion against a wall the collider never produced → `differenceY`. Confirm live. |
| LongJump / HighJump | COVERED | jump-motion baseline in `MovementCharacteristics` | Y velocity above the computed jump motion diverges. |
| Timer, `TimerRange`, timer 1.002 | COVERED | `Timer`/`timer/Balance.java`, `PlayerTime` | Nanosecond packet-balance accumulates; sustained >1.0 trips the balance overflow. |
| Step/Jesus/Spider per-AC modes (Vulcan/Grim/NCP/Hypixel) | N/A | — | Target **other** anticheats; the underlying vector is covered above. |

## Combat

| LB module(s) | Verdict | Owned by |
| --- | --- | --- |
| KillAura / Aimbot / AutoClicker | COVERED | heuristics engine (rotation + accuracy + click-pattern + meta) |
| Velocity / anti-KB, `VelocityIntave` (0.6 reduce, JumpReset) | COVERED | knockback folded into `baseMotion`; the replay flags a client that doesn't take the predicted KB |
| Reach / Hitbox / failSwing range | COVERED | `AttackRaytrace.java` (eye→hitbox raytrace vs vanilla reach) |
| `simulateInventoryClosing` | COVERED+ | `InventoryCloseAttackHeuristic` (this work, PR #3) |
| FailRotationProcessor | COVERED+ | `FailRotationHeuristic` (this work, PR #3) |
| attack-while-eat/-bow/-inventory | COVERED | `AttackWhile*` heuristics |
| `MultiActions` (place-while-break, break-while-use) | **COVERED+ (GAP closed)** | **`MultiActionHeuristic` (this work)** — see below |
| LinearAngleSmooth | COVERED | `RotationConstantSpeedHeuristic` / `RotationLinearityHeuristic` (observe-0; corroborate) |
| Sigmoid/InterpolationAngleSmooth | WEAK | `AimSmoothing`/`RotationAcceleration`/`RotationEntropy` (observe-0; rely on corroboration) |
| AccelerationAngleSmooth + injected error | WEAK | entropy backstop; injected 0.1° degrades the acceleration heuristic, sits under jitter's 1.5° gate |
| `ShortStopRotationProcessor` (micro-pauses) | RESIDUAL | `ConstantSpeed`/`Entropy` stay tolerant; 4 sequence-based tells lose sensitivity (no FP-safe fix) — see below |
| AiAngleSmooth (ML, human-like) | **RESIDUAL** | `RotationEntropyHeuristic` is a soft backstop; cross-domain `GhostClient` catches multi-module users |
| GCD `normalize()` | RESIDUAL | `RotationSensitivityHeuristic` exists but ships disabled (`-1`); GCD-align is a direct counter |

## World / interaction

| LB module(s) | Verdict | Owned by |
| --- | --- | --- |
| Scaffold (incl. ON_TICK rotation, godbridge, towers) | COVERED | `PlacementAnalysis` sub-checks (`Snap`, `SharpRotation`, `RotationSpeed`, `AngleSnap`, `RotationFlick`, `CursorStability`, `PacketOrder`, `Speed`, `SmartSpeed`) |
| GhostHand (interact through walls) | COVERED | `InteractionRaytrace` occlusion / line-of-sight |
| FastBreak / FastPlace / Nuker / AutoMine | COVERED | `BreakSpeedLimiter` (break-time floor) + `InteractionRaytrace` |
| AutoTotem / AutoArmor / offhand | COVERED | `InventoryClickAnalysis` (`AutoTotem`, click-timing, not-open clicks) |
| FastUse / FastBow / FastConsume | COVERED | `FastUse` (`FastBow`, `FastConsume`) |
| Disabler / ServerCrasher (malformed packets) | PARTIAL | `ProtocolScanner` catches several malformed-packet classes; pure crash exploits are a server/proxy concern, not an AC vector |

## Player

| LB module(s) | Verdict | Owned by |
| --- | --- | --- |
| Blink | COVERED | `Timer` / `MicroBlink` (packet hold/release) |
| NoFall (all modes) | COVERED | see Movement·NoFall |
| ChestStealer / InventoryCleaner | PARTIAL | `InventoryClickAnalysis` timing; mostly a QoL automation, low cheat value |
| AutoFish / AutoRespawn / AntiAFK | NOT-DETECTABLE / low-value | server-legitimate packet rates; not a movement/combat advantage |

## Not server-detectable (client-only render/UI)

ESP, Tracers, NameTags, FreeCam, NoInterpolation, BlockTracer, Xray-render, HUD/ClickGUI,
Fullbright, etc. A server-side anticheat has **no signal** for these — claiming to "detect" them
would be hallucination. (Wallhack/X-ray *behaviour* is only catchable indirectly, e.g. mining toward
unseen ores — out of scope here.)

## Real gaps & how they are handled

1. **`MultiActions` (place-while-break / break-while-use)** — *was a true gap*. `inBreakProcess` is
   tracked (`AttackMetadata:37`) but no invariant flagged the overlap. **Closed** by
   `MultiActionHeuristic` (config `multi-action: 4`, enforced, sustained-gated, creative-exempt): a
   block place while `inBreakProcess`, or a `START_DESTROY_BLOCK` while `inventory.handActive()`.
   Hard invariant (vanilla = one hand action per tick), so false positives need a sustained run.

2. **`ShortStopRotationProcessor` (micro-pauses) — RESIDUAL, no FP-safe code fix.** Verified against
   the actual code (correcting an earlier over-generalisation):
   * `RotationConstantSpeedHeuristic` does **not** reset on a still tick — it skips the sample and
     keeps the accumulator (`RotationConstantSpeedHeuristic.java:78-80`); CV is order-independent, so
     it already catches ShortStop-fragmented constant-velocity aim.
   * `RotationEntropyHeuristic` skips idle ticks without resetting → also tolerant.
   * `AimSmoothing`, `RotationAcceleration`, `RotationLinearity`, `RotationJitter` **do** reset on a
     still tick (`*.java` ~ lines 91-95, 88, 98) — and they *must*: they consume **contiguous**
     samples (step-ratios, accelerations, the (Δyaw,Δpitch) path, lag-1 autocorrelation). Skipping a
     pause and pairing non-adjacent ticks would corrupt those statistics and *introduce* false
     positives. So a naive "still-gap tolerance" is **not** a safe fix for these four.

   Net: ShortStop lowers the sensitivity of those four behavioural tells but cannot fragment
   `ConstantSpeed` or `Entropy`, and the enforcing rotation heuristics + cross-domain `GhostClient`
   remain. There is no false-positive-safe code change here, so none is made — this is a documented
   residual, not an open fix.

3. **AiAngleSmooth (ML) / GCD normalize — RESIDUAL.** An ML rotator trained to emit human-like aim,
   plus GCD alignment, can stay under the statistical rotation tells; this is an industry-wide hard
   limit of server-side aim detection, not a fork-specific bug. The honest mitigations already in
   place: `RotationEntropyHeuristic` (information-theoretic backstop) and, decisively, the
   cross-domain `GhostClientHeuristic` — a real cheat client runs several modules, so its movement /
   reach / velocity / packet tells corroborate even when its aim is clean. We deliberately do **not**
   enable `RotationSensitivity` (GCD) — it false-positives on client/proxy rounding.

## What this work added

* `MultiActionHeuristic` — closes the multi-action invariant gap (FP-safe, enforced).
* This matrix — the grounded "cover everything" deliverable.
* (PR #3, prior branch) `InventoryCloseAttackHeuristic`, `FailRotationHeuristic`, Baritone detection.

Everything else in LiquidBounce is either already defeated by the simulation/raytrace/heuristic
engine (cited above) or not server-observable. No redundant per-exploit detectors were added, to keep
the false-positive rate low.
