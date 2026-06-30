# Server testing guide — pending fixes & Folia

This collects everything that needs a **live server** to verify, since the changes touch the
collision/movement/mitigation runtime and cannot be exercised by the headless unit tests. Run these
before merging the branches that carry them.

## Branches under test
| Branch | Carries | Risk |
| --- | --- | --- |
| `claude/issue-fixes` | issue #104 interaction-in-block exemption + revived `currentlyInBlock` | medium — touches a movement field read by `SimulationEvaluator` leniency and the interaction check |
| `claude/folia-support` (draft PR #14) | full Folia refactor brought current with master | high — thread/scheduler model, every Bukkit-API call |
| `claude/attack-through-wall` (PR #12) | wallbang detector | low — ships VL 0 (observe) |
| `claude/multiaction-place-while-use` (PR #13) | place-while-use invariant | low — 0% FP by construction |

Test everything **together** at the end, on the Folia branch with the fixes merged in.

## Test server matrix
Spin up at minimum:
- **Spigot/Paper 1.8.8** (legacy combat + interaction path)
- **Paper 1.20.1** (mid)
- **Paper 1.21.x** and **Purpur 1.21.11** (the version in issues #104/#111/#49)
- **Folia 1.20.1** (`dev.folia:folia-api:1.20.1` — the branch targets this)

Each needs: ProtocolLib, (optionally ViaVersion), and a second client/account to fight for the combat checks.

Always run with `/intave verbose` on during a test and capture the chat output.

## What to collect for every test
1. `/intave diagnostics environment` (click chat to copy) — server fork+version, MC version, intave build.
2. `/intave verbose` output during the repro.
3. A screen recording of the repro (avoid streamable; verbose visible).
4. For a false positive: the exact check name + message + the player action that triggered it.

---

## Fix 1 — issue #104: interaction while embedded in a block

**Bug:** a plugin teleports a player inside a block → intave flags "interacting suspiciously" →
the player cannot mine out and suffocates.

**Repro:**
1. Stand the player in the open. `/setblock ~ ~ ~ stone` (or a plugin `/tp` that lands them in a wall)
   so the player's body is inside a solid block (suffocation particles).
2. Try to break the block they are stuck in.

**Pass criteria:**
- Mining the block now **works**; no "interacting suspiciously" notify while embedded.
- The moment the player is free, interaction checks behave normally again.

**Regression checks (must still hold):**
- Normal mining/placing/interacting away from blocks is unaffected.
- A **reach / through-wall** cheat is still caught — confirm a client mining a block it has no
  line of sight to (without being embedded) still flags `InteractionRaytrace`.
- No new interaction false positives on slabs/stairs/fences/glass (non-embedding blocks).

## Fix 2 — revived `currentlyInBlock` (movement leniency)

`currentlyInBlock` now actually turns true when the player's box intersects a block. This re-enables
the leniency branch in `SimulationEvaluator` (line ~387).

**What to watch:**
- **Regression:** confirm movement cheats are still caught — phase/clip into blocks, NoClip, and
  "move into block" still flag `Physics` (the leniency must not let a cheat sit inside blocks).
- **Improvement:** scenarios where a hit pushes you partly into a block, towering, or standing on a
  block edge should produce **fewer** physics false positives (relevant to issues #54, #66, #72) —
  note whether they improved.
- Watch the `Physics` VL while standing embedded vs moving normally; it should not climb abnormally.

## Folia branch (draft PR #14)

**Smoke test on a Folia server:**
1. Plugin loads with **no scheduler exceptions** in console (Folia throws on `Bukkit.getScheduler`
   misuse — the branch routes through `Tasks`/`FoliaTaskScheduler`).
2. Join, move, fight, mine, open inventory — every check still runs (compare verbose output to Paper).
3. Mitigations work: trigger a setback (e.g. a speed cheat) and confirm the lag-back applies on the
   correct region thread without errors.
4. Run for ~30 min with several players across regions; watch for `IllegalStateException`/thread
   ownership errors.

**Also re-run the full Paper matrix** on this branch — the merge changed scheduling in 97 files, so
confirm no behavior regressed vs master.

---

## Still needs server data before a safe fix

These are diagnosed but their fix depends on observing the exact runtime failure — capture the data
below and the fix can be written informed.

### Issue #79 — NoSlow prevention broken on food
- Path: `InventoryMetadata.releaseItemNextTick` → `MovementDispatcher` (~line 482) → `releaseItem(user)`.
  The slot-switch mitigation fires but the consume still completes.
- **Capture:** verbose during a NoSlow-food repro; whether `releaseItem` runs (debug), the held slot
  before/after, and whether a `PlayerItemConsumeEvent` still fires. Test per version (1.8 vs 1.20+).

### Issue #102 — spear attack range when swapping items
- Path: combat reach (`AttackRaytrace.reachDistanceOf`) + `CombatItems`/`FastSwapHeuristic` (spear is a
  heavy combat item).
- **Capture:** the replay attached to the issue; the reach value intave computes for the spear vs the
  primary weapon across a swap; whether the spear's extended range persists after swapping away.

### Issue #111 — Purpur 1.21.11 load failure
- **Capture:** the full startup stack trace from console on Purpur 1.21.11.

### Issue #104 follow-ups / other movement FPs (#52–#72, #88–#90)
- Each needs a clean repro recording + `/intave verbose` + environment string. Group by check
  (`Physics`, `InteractionRaytrace`, `AttackRaytrace`) so each can be tuned against real data.
