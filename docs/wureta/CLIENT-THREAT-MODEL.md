# Wureta Network — client threat model

- **Branch:** `Wureta` (based on `feature/sourbyapi-parity`)
- **Target server:** Wureta Network, Minecraft 26.2 on SourbyCraft 26.2 (SourbyAPI)
- **Anticheat:** Intave 14.9.6+ with the SourbyAPI scheduler and detection parity gate
- **Analysis date:** 2026-11-11

## 1. Scope

This document maps the behavior of three open-source clients to Intave's detection surface:

| Client | Repository | Commit analyzed | Target in checkout |
| --- | --- | --- | --- |
| Meteor Client | `meteorDevelopment/meteor-client` | `81448b4` | MC 26.3 |
| Wurst7 | `wurst-imperium/wurst7` | `52364e5` | MC 26.3 |
| LiquidBounce | `ccbluex/liquidbounce` | `121f283` | MC 26.2–26.4 |

The goal is not to fingerprint client brands. It is to identify **server-observable behavior** that
Intave must validate, and to prevent detection regressions while adding SourbyAPI support.

## 2. Method and evidence

- Complete source repositories were cloned and analyzed with repository tools.
- REA was selected for artifact-level analysis, but its Java decompiler provider (JEB) is not running
  on this host, so REA could not open the JVM artifacts. The repositories are complete source, which
  the REA workflow explicitly treats as ordinary source analysis rather than a shipped-binary
  investigation. This is a limitation of this pass, not a claim that the artifacts were analyzed by
  JEB.
- Meteor and Wurst7 checkouts currently target MC 26.3. Their 26.2 builds can differ in packet
  semantics; every finding that depends on 26.3-only packets is marked below. A 26.2 artifact or
  branch must be re-checked before shipping a detection that depends on it.
- Facts below are observed in the listed source files. Detection verdicts are inferences against
  Intave's check set and must be validated with recordings and tests on 26.2.

## 3. Client profiles

### 3.1 Meteor Client

**Inventory:** 29 combat, 31 movement, 31 player, 24 world, 16 misc, 38 render modules
(`systems/modules/Modules.java`). Render modules are largely irrelevant server-side.

**High-value behavior:**

- **Silent rotation engine** (`utils/player/Rotations.java`): the serverbound movement packet's
  yaw/pitch is overwritten only for that packet and then restored; some modules emit standalone
  rotation packets. Callback modules (Scaffold, CrystalAura, PacketMine) send rotation before the
  action, while KillAura attacks in `TickEvent.Pre` using the *previous* tick's rotation, producing
  an attack-before-rotation ordering.
- **KillAura** (`combat/KillAura.java`): default range 4.5 measured to the closest point of the
  target AABB; walls-range 3.5 with a client-side raycast; TPS-synced attack delay.
- **Criticals** (`combat/Criticals.java`): Packet mode injects `+0.0625/+0/+1.1e-5/0` position
  packets per attack; Mace mode injects a fake `+1.501` drop; Jump mode holds the attack and punch
  packets until the jump apex.
- **Velocity** (`movement/Velocity.java`): rewrites the incoming `SetEntityMotion` packet client-side;
  the client never applies knockback, so its movement is internally consistent. Only a
  physics-vs-knockback expectation check can see it.
- **NoSlow/NoFall/AntiHunger** (`mixin/LocalPlayerMixin.java`, `movement/NoFall.java`,
  `player/AutoHunger.java`): remove item/block slowdown client-side and forge `onGround` and tiny
  position packets.
- **Blink** (`movement/Blink.java`): buffers all movement packets while actions continue, then dumps
  the buffer as a burst.
- **Timer** (`world/Timer.java`): multiplies the client delta tracker; burst modules can temporarily
  raise the rate.
- **Scaffold** (`movement/Scaffold.java`): rotation-callback placement, air-place radius 4.5–6,
  forced sneak key state in packets.
- **Inventory** (`utils/player/InvUtils.java`, `combat/AutoTotem.java`, `AutoArmor`, `ChestSwap`):
  same-tick pick/place pairs, hotbar swaps, armor/offhand clicks on container 0 without opening a
  screen.
- **Anti-anticheat:** NoRotate reverts server-set rotation; PacketCanceller drops arbitrary packets;
  AntiHunger spoofs ground; Swarm coordinates multiple accounts.

### 3.2 Wurst7

**Inventory:** 156 hack classes registered by reflection (`hack/HackList.java`): 26 blocks,
33 movement, 24 combat, 37 render, 9 items, 11 other, 7 chat, 7 fun.

**High-value behavior:**

- **Killaura** (`hacks/KillauraHack.java`): `faceVectorPacket` in PreMotion, attack in HandleInput;
  Gaussian attack-speed jitter to defeat fixed-interval checks; range 5–6 and optional LOS.
- **Rotation faking** (`RotationFaker.java`, `mixin/LocalPlayerMixin.java`): client camera is restored
  after `sendPosition`, so the server sees a spoofed rotation while the camera does not move.
- **Criticals** (`hacks/CriticalsHack.java`): four extra position packets with
  `+0.0625/0/+1.1e-5/0` vertical offsets; **MaceDMG** sends `+√500` with `onGround=false`.
- **AntiKnockback** (`mixin/antiknockback/EntityMixin.java`): scales `lerpMotion` client-side only;
  the server still applies full knockback. This is **not server-detectable** as a velocity anomaly.
- **Reach** (`mixin/reach/PlayerMixin.java`): changes client interaction range only; the server sees
  vanilla range. Killaura's 5–6 m range is the server-visible signal.
- **NoFall/Jesus/AntiCactus** (`hacks/*`): forge `onGround` or offset `Pos` packets while keeping
  plausible positions.
- **AutoTotem/AutoArmor/AutoSteal/AutoDrop** (`hacks/*`): pickup→offhand, quick-move armor swaps,
  chest-steal daemon with 100 ms delay, unguarded throw clicks.
- **ScaffoldWalk/AutoBuild** (`hacks/*`): rotation + `rightClickBlock`, fast-place delay ~4 ticks,
  server-facing options.
- **Nuker/AutoMine/FastBreak**: destroy-delay 0, ABORT/restart loops, activation chance
  randomization.
- **Anti-anticheat:** FaceTarget SERVER/CLIENT/SPAM/OFF, AttackSwing CLIENT/SERVER/OFF, speed
  randomization, NoCheat+-tuned caps, client-brand spoofing.

### 3.3 LiquidBounce

**Inventory:** roughly 240 modules: movement 38, player 30, combat 27, world 25, exploit 25,
misc 28, fun 7, render ~60. Supports MC 26.2–26.4.

**High-value behavior:**

- **TickBase** (`combat/ModuleTickBase.kt`, `utils/client/Timer.kt`): charges timer balance slowly
  while an enemy is near and spends it in 5× bursts; average rate stays near 1.0. Modes: TimerBoost,
  HoldFirst, BurstFirst.
- **FakeLag/Backtrack** (`combat/ModuleFakeLag.kt`, `combat/backtrack/ModuleBacktrack.kt`):
  `BlinkManager` queues outgoing or incoming packets; flushes on attack/use/teleport/damage; Backtrack
  attacks stale hitboxes.
- **Velocity modes** (`combat/velocity/mode/*`): Intave-specific legit self-damp/jump-reset; Grim
  mode cancels knockback, places a block, freezes, and cancels own position packets; VelocityLag
  delays knockback but explicitly never queues KeepAlive.
- **KillAura** (`combat/killaura/ModuleKillAura.kt`): rotation manager with smooth/AI processors;
  human log-normal click timing or a deliberately constant technique; auto-block fake shield.
- **Criticals/MaceKill/TpAura** (`combat/criticals/`, `ModuleMaceKill.kt`, `tpaura/`): position
  spikes, 10-block warp hops, burst teleports of ≤5 packets/tick with fall-height shaping.
- **Speed/NoSlow** (`movement/.../intave/*`): Intave-specific modes tuned to Intave 14, including
  duplicate UseItemOn while blocking and early release.
- **Scaffold/Nuker/Inventory** (`world/scaffold/`, `world/nuker/`, `player/cheststealer/`):
  movement-prediction placement, instant START/STOP bursts, randomized inventory delays, silent
  open via ViaFabricPlus.
- **26.2-specific:** `PositionPacketSeparator.kt` inserts `ClientTickEnd` before extra position
  packets only on ≥26.3; on 26.2 multiple position packets per tick go out un-separated.
- **Anti-anticheat:** `AntiBotIntave20220728` filters Intave spawn-trap bots by ping inconsistency;
  NoRotateSet/NoSlotSet reject server state; SpooferClient spoofs brand; auto-report helper.

## 4. Cross-client threat clusters

| Cluster | Client modules | Server-observable signal | Intave status |
| --- | --- | --- | --- |
| Silent rotation / aim | Meteor Rotations+KillAura, Wurst RotationFaker+Killaura, LB RotationManager+KillAura | rotation-only packets, attack-before-rotation order, exact hitbox-center bearing, no intermediate rotations, hold ticks | **Partial** — Heuristics covers snap/exact but not order/context |
| Clicking | Meteor AutoClicker, Wurst Killaura/TriggerBot, LB Clicker/HumanClickTiming | interval entropy, constant-period technique, triggerbot crosshair-enter→attack latency | **Partial** — ClickPatterns/ClickSpeedLimiter cover fixed rates, not human/triggerbot |
| Movement | Meteor Flight/NoSlow/NoFall/Step, Wurst Flight/Speed/Step/NoFall/Jesus, LB Speed/Fly/NoFall/NoSlow | position/velocity vs simulation, onGround contradictions, input-slowdown mismatch | **Covered/Partial** — Physics simulates, but onGround spoofs need per-packet checks |
| Timer / TickBase | Meteor Timer, Wurst Timer, LB TickBase | inter-move interval distribution, burst clusters after slow charge, average rate ≈ 1.0 | **Partial** — Timer catches sustained rate, not bursts |
| Blink / lag queue | Meteor Blink, Wurst Blink, LB FakeLag/Backtrack/VelocityLag | position gaps while actions/keepalives continue; flush correlated with combat; stale-hitbox attacks | **Gap/Partial** — no dedicated selective-lag detector |
| Criticals / teleport | Meteor Criticals/Mace, Wurst Criticals/MaceDMG, LB Criticals/MaceKill/TpAura | sub-tick vertical offsets, √500 constant, teleport bursts, fall-distance legitimacy | **Partial/Gap** — Physics catches impossible motion, but micro-offsets and teleport chains are missed |
| Reach / hitbox | Meteor KillAura, Wurst Killaura/Reach, LB KillAura | eye→server hitbox closest point, range > vanilla, attack without valid rotation | **Partial** — AttackRaytrace needs closest-point measurement and 26.2 rotation source |
| Placement / scaffold | Meteor Scaffold, Wurst ScaffoldWalk/AutoBuild, LB Scaffold | rotation-callback place, air-place, fast-place cadence, sneak spoof, placement while not looking | **Partial** — PlacementAnalysis covers rotation/facing, not fast-place/air-place |
| Break / nuker | Meteor PacketMine/Nuker, Wurst Nuker/FastBreak, LB InstantNuker | START+STOP same tick, ABORT restart, skip damage progression, reach/LOS | **Partial** — BreakSpeedLimiter validates duration, not per-tick progression |
| Inventory | Meteor AutoTotem/Armor/ChestSwap, Wurst AutoTotem/Armor/Steal, LB cheststealer/invcleaner | same-tick click pairs, silent open, cursor-state mismatch, hotbar churn | **Partial** — interval checks defeated by randomized delays; need state machine |
| Protocol / anti-AC | Meteor PacketCanceller/NoRotate, Wurst packet cancel, LB NoRotateSet/NoSlotSet | dropped move packets, teleport rotation refusal, slot desync, brand spoof | **Gap** — ProtocolScanner lacks these invariants |
| Multi-account | Meteor Swarm | mirrored targets/actions, shared IP/session patterns | **Gap** — Cloud has no explicit multi-account features |

## 5. Highest-risk false negatives on 26.2

1. **Attack-before-rotation / held attack packets.** 26.2 attack packets carry no hit position;
   raytrace evidence must come from the movement-rotation history. A state machine is required.
2. **Timer bursts.** Average rate checks miss charge/spend TickBase patterns; instantaneous
   inter-move intervals are needed.
3. **Selective lag.** Blink/FakeLag/Backtrack keep actions or KeepAlive flowing while position
   packets are delayed; a position-gap + action-continuity detector is needed.
4. **Criticals/MaceKill micro-moves.** Sub-tick vertical offsets and teleport bursts can look like
   legal small corrections to a per-tick simulation.
5. **Silent-open inventory and same-tick click pairs.** Delay-based checks do not see the state
   machine violation.
6. **Fast-place / air-place.** Rotation-callback placement can be look-legal while exceeding range,
   cadence, or sneak constraints.
7. **Break progression.** Same-tick START/STOP and ABORT restarts can bypass duration-only checks.

## 6. 26.2 protocol notes

- Attack is split into `ServerboundAttackPacket(int entityId)` and `ServerboundPunchPacket()`; attack
  packets carry no hit position or flags. Intave must derive aim from movement rotation history.
- `ServerboundMovePlayerPacket.Pos(x,y,z,onGround,horizontalCollision)` carries a collision flag that
  clients use when forging packets.
- Multiple position packets can arrive between client tick ends on 26.2; `ClientTickEnd` separation
  is a 26.3 requirement.
- Block break/place use prediction sequences (`startPrediction`, `ClientboundBlockChangedAckPacket`);
  sequence monotonicity is a protocol-level signal.
- `ResourceLocation` was renamed to `Identifier`; ProtocolLib 5.4.0 lacks the mapping. Intave's
  `ProtocolLibraryAdapter` warns and continues, and `ExplosionReader` falls back safely.
- 26.2 still sends swing packets for attacks; 26.3 makes punch imply the swing. Do not carry 26.3
  swing assumptions into Wureta without a 26.2 check.
- Teleport confirmation semantics differ between 26.2 and 26.3; verify before changing
  `ProtocolMetadata.teleportAcceptIncludesPositionAndRotation()`.

## 7. Anti-detection features to expect

- Client-side-only changes that are **not server-detectable**: Wurst `ReachHack` (client interaction
  range), Wurst `AntiKnockback` `lerpMotion` scaling, pure ESP/render modules. Do not spend detection
  budget on these.
- Server-state rejection: NoRotate/NoSlotSet/NoRotateSet, packet cancellers, slot desync.
- Statistical camouflage: Gaussian/attack-speed jitter, human log-normal click timing, random armor
  order, random fast-break activation, randomized inventory delays.
- Trap avoidance: `AntiBotIntave20220728` filters Intave bot traps by ping inconsistency; trap bots
  need realistic latency history.
- Multi-account: Swarm coordination; Cloud should eventually model cross-account action correlation.

## 8. Open questions

1. Which exact 26.2 client builds will Wureta players use? Meteor/Wurst checkouts target 26.3; confirm
   the 26.2 packet semantics before weighting order-based checks.
2. Does Wureta run SourbyCraft 26.2 with the Aurora scheduler default and the parity gate enabled?
3. Are there server-specific check thresholds, punishment policy, or disabled checks? This branch
   currently assumes all 12 checks remain registered and default-enabled.
4. Is multi-account detection in scope for the first Wureta iteration, or deferred to Cloud?
