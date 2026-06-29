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
| `RotationAccelerationHeuristic` | `rotation-acceleration` | linear-aim / aimbot | Robotically constant yaw *acceleration* — a steady speed ramp with near-zero jerk (low sd, non-trivial mean), which neither constant-speed nor geometric easing describes; ships at `0` (observe) | all |
| `AimSmoothingHeuristic` | `aim-smoothing` | aim-smoothing aimbot | Per-tick ease ratio toward the target stays robotically constant (low CV) while decelerating in; ships at `0` (observe) | all |
| `RotationLinearityHeuristic` | `rotation-linearity` | linear-interpolation aimbot | Per-tick `(Δyaw, Δpitch)` steps are collinear (\|r\| → 1) — a robotically straight aim path; ships at `0` (observe) | all |
| `RotationEntropyHeuristic` | `rotation-entropy` | aimbot (ML-style) | Rotation stream is robotically repetitive — normalised Shannon entropy of the step distribution too low to be human motor noise; ships at `0` (observe) | all |
| `RotationJitterHeuristic` | `rotation-jitter` | aimbot (anti-smoothness evasion) | Added aim jitter is statistically artificial — lag-1 autocorrelation of signed yaw deltas near zero/negative (white noise) where human tremor is autocorrelated; complements the smoothness tells; ships at `0` (observe) | all |
| `FailRotationHeuristic` | `fail-rotation` | aim-assist (anti-accuracy evasion) | A robotically tight aim (residual to target ~0) punctuated by deliberate, bounded, self-reverting "miss" pulses of uniform magnitude — the fingerprint of a fail-rotation processor (e.g. LiquidBounce) added to beat the accuracy/jitter/entropy tells; the pulses ride a baseline far tighter than human motor noise; ships at `0` (observe) | all |
| `PacketInventoryHeuristic` | `inventory-rotations` | inventory-aura / auto-item | Rotation sent while inventory open; open+close within one tick | all |
| `BlockingHeuristic` | `blocking` | 1.8 block-hit | Illegitimate sword block/unblock timing | **1.8 only** |
| `NoSwingHeuristic` | `no-swing` | no-swing aura | Attack lands in a tick with no arm-animation | all |
| `PacketOrderSwingHeuristic` | `swing-order` | packet-mimic aura | Attack packet arrives in a tick with no preceding swing | flying-packet clients |
| `PacketPlayerActionToggleHeuristic` | `sprint-toggles` | w-tap / sprint-reset bots | Multiple sprint/sneak toggles within one movement tick | all |
| `ToolSwitchHeuristic` | `tool-switch` | auto-tool | Automated held-slot swap mid block-break | all |
| `FastSwapHeuristic` | `fast-swap` | auto-swap / weapon-combo macro | In-combat weapon swaps faster than one per tick (mace/trident/spear-aware); ships at `0` (observe) | all |
| `MaceFallDistanceHeuristic` | `mace-fall-distance` | mace fall-distance spoof | Smash hit whose server fall distance needs more airtime than terminal velocity allows (`fall / 4.0 > ticks since ground`) | **1.21+** |
| `MultiAuraHeuristic` | `multi-aura` | switch-aura / multi-target aura | Attacks land on *distinct* entities within a single tick (sustained, lag-gated) — hard invariant, **enforced** (`4`) | all |
| `CrystalAuraHeuristic` | `crystal-aura` | crystal-aura | Detonates an end crystal ≤2 ticks after it spawns — super-human reaction (sustained); ships at `0` (observe) | 1.9+ |
| `AnchorBedAuraHeuristic` | `anchor-bed-aura` | anchor/bed-aura (Nether/End PvP) | Runs the place→detonate cycle of a respawn anchor or bed faster than a human can place and re-aim (only where the block explodes — bed in Nether/End, anchor in Overworld/End); sustained; ships at `0` (observe) | 1.16+ |
| `SpearAttackSpeedHeuristic` | `spear-attack-speed` | spear auto-attack | Spear (heavy weapon) hits land closer than its cooldown allows for a full-power attack (sustained); ships at `0` (observe) | 1.9+ |
| `HeavyHitterAttackSpeedHeuristic` | `heavy-attack-speed` | mace/trident auto-attack | Mace (1.21) or trident hits land closer than a heavy weapon's cooldown allows (sustained, conservative interval); the modern heavy-hitters the spear check misses; ships at `0` (observe) | 1.9+ |
| `AttackWhileConsumingHeuristic` | `attack-while-consuming` | kill-aura | Sustained entity attacks while the hand is still consuming food/drink — the first attack should interrupt the consume, so a run during one continuous use is impossible; hard invariant, **enforced** (`5`) | all |
| `AttackWhileBowDrawHeuristic` | `attack-while-bow-draw` | kill-aura / bow-aura | Sustained melee attacks while a bow/crossbow is being drawn — the draw occupies the hand (a click would release the shot), so a melee run during one continuous draw is impossible; hard invariant, **enforced** (`5`) | all |
| `AttackWhileInventoryOpenHeuristic` | `attack-while-inventory` | kill-aura / inventory-aura | Sustained attacks while a container GUI is open — vanilla routes the mouse to the screen, so attacking entities through an open inventory is impossible; distinct from inventory-*rotations*; hard invariant, **enforced** (`4`) | all |
| `InventoryCloseAttackHeuristic` | `inventory-close-attack` | kill-aura / inventory-aura (anti-detection) | Sustained run of attacks landing within one tick of a container `CLOSE_WINDOW` — the signature of a client that closes (and reopens) the GUI around each attack so the server never sees it open at attack time (e.g. LiquidBounce's `simulateInventoryClosing`), the countermeasure to `attack-while-inventory`; a deliberate human close-then-attack takes far longer than a tick; hard invariant, sustained-gated, **enforced** (`4`) | all |
| `BaritoneHeuristic` | `baritone` | pathfinding bot (Baritone) | Attacks land while the player is auto-pathing with the yaw heading-locked through turns (the movement-domain `Pathfinder`/`HeadingLock` tell — see below); a human breaks bot-perfect travel to fight, so attacking *during* it is the combination; records to the ledger to corroborate; ships at `0` (observe) | all |
| `CriticalsHeuristic` | `criticals` | packet / spoofed criticals | Attack lands with the client claiming to be airborne (`!lastClaimedOnGround`) while intave's **collision-validated** ground truth says firmly grounded, with no real vertical motion or accumulated fall — the fall state Wurst `Criticals` PACKET mode / LiquidBounce packet criticals fabricate without leaving the ground. Closes the inverse of the NoFall case `MovementDispatcher` already handles; sustained-gated, strips the crit (`AttackNerfStrategy.CRITICALS`) on a confirmed run; ships at `0` (observe + nerf) | all |
| `CivbreakHeuristic` | *(mitigation only)* | civbreak fast-break | Drops rogue `STOP_DESTROY_BLOCK` packets | **< 1.14** |
| `ImpossibleComboHeuristic` | `impossible-combo` | definitive cheat verdict (meta) | ≥2 *distinct physical-impossibility* tells coincide (multi-aura, attack-while-consuming/-bow-draw/-inventory, mace-fall-distance) — a legit player trips none, so two at once is certain; zero-FP by construction, ships **enforced** (`15`) | all |
| `CorroborationHeuristic` | `corroboration` | multi-tell cheats (meta) | ≥3 *distinct* heuristics agree (breadth gate), then fuses their **confidence-weighted** evidence — strong/broad agreement escalates fast, weak/broad barely moves (decaying, graded) | all |
| `GhostClientHeuristic` | `ghost-client` | ghost/cheat clients e.g. Vape (meta) | ≥4 *distinct base* heuristics agree, then fuses their **confidence-weighted** module coverage + **cross-domain** breadth (read-only movement/packet/world VLs) (cheat client running several modules); client brand folded in for attribution | all |

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

### Online statistics — `RollingStatistics`

Many heuristics ask "how uniform is this sequence of measurements?" (turn speeds, aim step-ratios,
inter-click intervals). `RollingStatistics` is the engine's reusable accumulator for that: it folds
each sample into a running mean, variance, standard deviation and **coefficient of variation** using
Welford's numerically-stable, constant-memory online algorithm. Checks `accept(...)` samples as
packets arrive and read the derived statistic when their window is full — no growing `List`, no
second pass, and no copy-pasted loop. `RotationConstantSpeedHeuristic` and `AimSmoothingHeuristic`
both build on it; new "how robotic is this stream?" checks should too rather than hand-rolling a
variance loop. By convention `coefficientOfVariation()` returns `Double.MAX_VALUE` for a
non-positive mean, so a *low* value unambiguously means "robotically uniform".

`RollingCorrelation` is its two-variable companion: it folds `(x, y)` pairs into an online Welford
co-moment and reports the Pearson `correlation()` of the stream — "do these two measurements move
together on a straight line?". `RotationLinearityHeuristic` uses it to spot collinear
`(Δyaw, Δpitch)` aim paths. It reports `0` for the degenerate near-zero-variance case, so a *high*
magnitude unambiguously means "robotically collinear".

### Cross-heuristic corroboration — `ConfidenceLedger`

Every flag is recorded in a per-player `ConfidenceLedger` shared across all heuristics (keyed by
class in the user metadata pool, so it is created lazily and released automatically). Because
independent detectors agreeing is far stronger evidence than one noisy detector repeating, the
ledger exposes how many *distinct* heuristics flagged a player within a recent window
(`corroboratingHeuristics`), the **confidence-weighted** agreement among them
(`weightedCorroboration` — the basis of the meta-detectors' evidence fusion), and the combined,
time-decayed `aggregateConfidence`; when more than one heuristic corroborates, that breadth is
surfaced on the violation for verbose triage. The ledger is an
**additive** intelligence layer — by itself it changes no violation level, so existing tuning is
preserved while new checks can consult corroboration to scale their own confidence.

The ledger's first consumer is `CorroborationHeuristic` (config key `corroboration`): it flags only
when several *distinct* heuristics agree on the same player within the window (a breadth gate), then
fuses their **confidence-weighted** evidence via `ConfidenceLedger#weightedCorroboration` — the
summed, time-decayed confidence of the agreeing detectors — rather than a raw count. It reinforces by
how *strongly* they agreed, above the baseline the minimum breadth contributes at trivial confidence,
so weak-but-broad agreement barely moves the decaying `ConfidenceBuffer` (strictly more conservative
than a count, where false positives live) while strong, broad agreement escalates quickly. Because it
requires independent detectors to corroborate, it is inherently low-false-positive, and it flags with
a graded confidence that scales with the fused weight.

### Ghost / cheat-client identification — `GhostClientHeuristic`

A "ghost client" (e.g. Vape) is an external cheat client built to evade screenshare and look
vanilla. A server-side anticheat cannot inspect its process, so it is **never caught by one magic
signature** — it is caught by the *aggregate* of its modules' behavioural tells. A cheat client runs
several modules at once (kill-aura, reach, velocity, auto-clicker) and each one leaks into a
different heuristic, so several *independent* detectors fire where a borderline-legitimate player
trips at most one.

`GhostClientHeuristic` (config `ghost-client`) consumes the ledger to turn that breadth into an
explicit verdict: when at least **four distinct base heuristics** agree within the window (the
corroboration and ghost meta-detectors themselves excluded, so the count reflects genuine module
coverage), it fuses their **confidence-weighted** module coverage (`weightedCorroboration` with both
meta-detectors excluded) in a decaying buffer — weak-but-broad leakage barely moves it, strong/broad
coverage escalates fast — and concludes the player is running a cheat *client*. The breadth is read
**across domains**: the player's current violation level on the non-combat checks (movement/`Physics`,
packet/`ProtocolScanner`, world, …) is consulted **read-only** from the shared per-player VL the
violation processor already maintains, and each tripping module folds in as cross-domain corroboration.
It never lowers the combat gate — a pure-combat flag is unchanged — it only sharpens an already-reached
verdict when a true multi-module client (aim + movement + packet) leaks across domains. The
`minecraft:brand` Intave already records is folded in for **attribution**: a ghost spoofing a
`vanilla`/blank brand while clearly cheating raises the confidence and is surfaced on the violation
(`brand=… (claims vanilla)`). The brand never triggers on its own, so honest Forge/Lunar/Badlion
clients are not penalised — it only colours a verdict the behavioural breadth already reached. It is
stricter than `corroboration` (four *base* tells vs three of any), making it a very low-false-positive
"this is a cheat client" signal; lower it to `0` to identify/log without adding violation level.

### ML-style detection — rotation entropy

Modern ML anticheats (e.g. the open-source MX project, which trains a Bi-LSTM on rotation/movement
history) ultimately key on one property: a human's aim is *high-entropy* — driven by irregular motor
noise — while an aimbot's computed rotation stream is regular and **low-entropy**.
`RotationEntropyHeuristic` (config `rotation-entropy`) brings that feature into the engine *without* a
model or training data: while the player tracks a moving target it quantises each rotation step
magnitude, and over a window measures the normalised Shannon entropy of the distribution; a value too
low to be human motor noise flags. It is inherently hard to bypass — reproducing human entropy means
reproducing human motor noise — and complements the moment-based rotation tells (snap, constant-speed,
smoothing, linearity) with an information-theoretic one. Intave also has a separate cloud ML pipeline
(`module/nayoro`) that records labelled combat samples for off-server classification; this heuristic
is the lightweight, on-premise, explainable analogue.

## Bedrock (Geyser/Floodgate) players

Players who join from Bedrock Edition through Geyser/Floodgate use a different input model — touch
and controller schemes with built-in aim assist — and their actions reach the server only after
Geyser translates them. Java-style aim/rotation/packet-cadence analysis does not apply to that
translated input, so the engine **exempts Bedrock players from the classic heuristics**:
`ClassicHeuristic#flag` early-returns for them (no violation level, no corroboration recording). The
deterministic checks outside this engine — reach, movement simulation, ray-tracing — still guard
Bedrock players against cheating.

Detection uses `BedrockPlayers#isBedrock`, which queries the Floodgate API **reflectively** (no
compile- or load-time dependency): it reports `false` when Floodgate is absent, so Bedrock cannot be
identified in a Geyser-only setup without Floodgate. This complements the `TrustFactor.BYPASS` that
Floodgate players already receive and applies even where that trust bypass is reconfigured.

## Pathfinding bot (Baritone) detection

Baritone is a pathfinding **bot**, not a combat cheat, so the combat heuristics above never see it on
their own. It is caught in how it *travels*, by a dedicated movement-domain check —
[`Pathfinder`](../src/main/java/de/jpx3/intave/check/movement/pathfinder/Pathfinder.java) (config key
`pathfinder`).

Its detector,
[`HeadingLock`](../src/main/java/de/jpx3/intave/check/movement/pathfinder/HeadingLock.java), targets
Baritone's most stable, server-observable signature: with `antiCheatCompatibility` enabled (its
default) Baritone forces the transmitted yaw to face the direction it is walking so it never sprints
sideways, and it steers along computed routes. The angular **residual** between the yaw and the heading
implied by the horizontal motion therefore stays near zero — *even while the bot is turning*. A human
walking straight has a small residual too, so the discriminator is the residual staying pinned **through
turns**: over a sprint window the check requires the cumulative yaw change to clear a threshold (the
player genuinely curved) while the mean residual and its spread both stay within a few degrees — a real
player over-/under-shoots when they curve, a bot tracks the route exactly. The pure geometry lives in
[`BaritoneMovementMath`](../src/main/java/de/jpx3/intave/check/movement/pathfinder/BaritoneMovementMath.java)
and is unit-tested.

Because it reads only motion and yaw, it is **version-independent** and covers every Baritone branch
(1.13.2 → 1.21.11) without per-version gating. The `Pathfinder` violation level it accumulates is folded
into `GhostClientHeuristic`'s cross-domain breadth automatically (a cheat client that *also* auto-paths
trips it alongside its combat tells), and each robotic window stamps the player so the combat-domain
[`BaritoneHeuristic`](../src/main/java/de/jpx3/intave/check/combat/heuristics/other/BaritoneHeuristic.java)
(config `baritone`) can fuse "bot-pathing while fighting" into the ledger. The check ships notify/log-only
(no kick); raise an action in `check.pathfinder.thresholds` after confirming no false positives.

> Considered and rejected: a block-break/place **cadence** sub-check. Vanilla block-break time is
> deterministic per block/tool, so a human repeatedly mining one block type is just as periodic as a
> bot — cadence uniformity is not a discriminator and would be false-positive-prone.

## LiquidBounce bypass resistance

A few heuristics above were added specifically to close evasion paths in current cheat clients
(LiquidBounce ships Intave-targeted modules):

* **`inventory-close-attack`** answers `simulateInventoryClosing` (closing the container a tick before
  each attack to dodge `attack-while-inventory`).
* **`fail-rotation`** answers the fail-rotation processor (injected uniform "misses" to beat the
  accuracy / jitter / entropy tells).
* **Silent-aim with `RotationTiming.ON_TICK`** (rotate → attack → rotate-back inside one tick) is
  already covered by the existing layers and needs no new detector: a *pure* silent rotation that never
  faces the target fails the reach **ray-trace** (a simulation check, angle-aware), which forces the
  client to briefly face the target; that brief snap-then-revert is caught by `rotation-reset`
  (`RotationModuloResetHeuristic`) on a sustained target and by `pre-attack` (`PreAttackHeuristic`),
  which sees no genuine cursor-on-target lead-in over its window. A dedicated attack-anchored sandwich
  detector was evaluated and skipped as redundant and false-positive-prone.

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
