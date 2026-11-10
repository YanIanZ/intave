# ADR: SourbyAPI Integration and Detection Parity

- **Status:** Proposed (owner approval required before implementation)
- **Date:** 2026-11-11
- **Owners:** PM, Chief Architect, Folia/Region Lead, Parity Auditor
- **Supersedes:** `feature/folia-support` (retire; do not merge)
- **Target:** SourbyCraft 26.2, Build 57 (`v26.2-r57`, tag commit `e65839e4`)

## Decision

1. **Retire `feature/folia-support`.** Do not merge or rebase it. Master already contains a newer and
   stricter Folia scheduler; the branch is 295 commits behind and its unique work is superseded by
   master commit `8678cb8f` ("feat: add Folia server support").
2. **Add SourbyAPI support as an optional, reflection-only bridge** in the Intave main source set.
   The first integration has no compile-time or runtime dependency on SourbyAPI and never loads
   SourbyCraft classes on non-SourbyCraft servers.
3. **Use the SourbyAPI `AuroraScheduler` by default on SourbyCraft Build 55+**, with a hard fallback
   to the Folia scheduler when SourbyAPI or the scheduler contract is unavailable. Operators can
   force the Folia path with `-Dintave.sourbycraft.scheduler=folia`.
4. **Land a detection-parity gate before touching scheduling.** The gate fails if any registered
   check or active check-part disappears, is renamed, or loses its default enablement.
5. **Do not vendor, shade, or copy SourbyAPI.** SourbyAPI is GPL-3.0-only; Intave is PolyForm
   Perimeter. If a typed adapter is needed later, use the unmodified pinned `compileOnly` artifact.

## Context and evidence

### `feature/folia-support` is obsolete

| Fact | Evidence |
| --- | --- |
| Branch is 12 commits ahead / 295 behind master and conflicts in 181 files | `git rev-list --left-right --count origin/master...origin/feature/folia-support`; `git merge-tree` |
| Master has a newer scheduler with region scheduling, one-shot cleanup, and delay validation | `src/main/java/de/jpx3/intave/executor/task/*`, `Synchronizer.java` |
| Branch lacks `startRegionSync`; rejects `delay=0` periodic tasks; leaks one-shot tasks; breaks `equals/hashCode` | Branch `executor/task/{TaskScheduler,FoliaTaskScheduler,DelayedTask,PeriodicTask}.java` |
| Every branch-only change is either already on master in better form or a regression | Folia Lead review; `git cherry -v` |

**Verdict:** retire. The branch's scheduler code must not be merged. Any still-valuable call-site
conversion (user/region-scoped `Synchronizer` use) is already present on master or is covered by
Task T5 below.

### Master detection inventory

- 12 registered top-level checks in `CheckService.setup()` (`CheckService.java:62-73`):
  Physics, InteractionRaytrace, Heuristics, Cloud, AttackRaytrace, ClickPatterns, ClickSpeedLimiter,
  Timer, BreakSpeedLimiter, ProtocolScanner, PlacementAnalysis, InventoryClickAnalysis.
- 53 active check-parts (plus declared-but-not-attached parts that are intentionally dead).
- `feature/folia-support` has 11 checks / 52 active parts; it predates `Cloud` and
  `MoveTickLimit`, so it is **not** a source of detection parity.
- Missing configuration silently disables a check: `CheckConfiguration.boolBy("enabled")` returns
  `false` when the key is absent (`CheckConfiguration.java:101-103,122-123`). The parity gate must
  therefore assert default enablement as well as registration.

### SourbyCraft / SourbyAPI facts

- Intave declares `folia-supported: true` (`build.gradle.kts:193`). SourbyCraft admits Folia-supported
  plugins natively; native admission is keyed on `folia-supported`/`canvas-supported`, not on
  `api-version` (`sourbyapi/paper-patches/files/src/main/java/org/bukkit/plugin/PluginDescriptionFile.java.patch`;
  `docs/architecture/aurora-plugin-bridge.md`). `api-version: 1.13` stays for legacy server support
  and does **not** force the Aurora Bridge.
- SourbyCraft keeps Folia's `io.papermc.paper.threadedregions.RegionizedServer`
  (`sourbycraft-server/minecraft-patches/sources/io/papermc/paper/threadedregions/RegionizedServer.java.patch`),
  so master's `Tasks.isFoliaServer()` already selects `FoliaTaskScheduler` on SourbyCraft.
- `AuroraScheduler` (Build 55+) is a facade over the same global/region/entity/async schedulers. It
  adds no queue or thread. Its concrete value is the entity `retired` callback, ownership queries,
  and a stable service contract (`sourbyapi/src/main/java/dev/iyanz/sourbycraft/api/scheduler/`).
- Artifacts (verified from the `v26.2-r57` release):
  - `sourbyapi-57.zip` SHA-256 `51049834fc0579a7efe561c0ee4a24ca975c633fa970be598f1e3b24410b37c5`
  - `sourbyapi-57.jar` SHA-256 `96528eba1598ad520639923e53fe7c0afe32ffaf1734cc4e40ce7f465146049c`
  - API classes are classfile major 69 (Java 25).
  - The full local checkout at `/Volumes/Ext/Sourby/SourbyCraft` is Build 54 (`sourbyBuild=54`) and its
    `sourbyapi-26.2-DEV.jar` predates `AuroraScheduler`; Build 57 verification must use the release
    artifacts above, not that checkout's API jar.
- `javac 25 -source 8 -target 8` can read major-69 classfiles (verified locally), but the resulting
  Java 8 class must not reference those classes at runtime on legacy servers. Reflection avoids the
  classloading and GPL-linkage surface entirely.

## Target architecture

```
Tasks
 ├─ ServerPlatform.current()
 │   ├─ SourbyCraftBridge.isSourbyCraft()  -> SOURBYCRAFT
 │   ├─ RegionizedServer present           -> FOLIA
 │   └─ otherwise                          -> BUKKIT
 └─ scheduler selection
     ├─ SOURBYCRAFT + AuroraScheduler available (default)
     │      -> AuroraTaskScheduler (reflection)
     │      (opt out: -Dintave.sourbycraft.scheduler=folia)
     ├─ SOURBYCRAFT without AuroraScheduler   -> FoliaTaskScheduler
     ├─ FOLIA                                  -> FoliaTaskScheduler
     └─ BUKKIT                                 -> BukkitTaskScheduler

Detection gate
 baseline-units.txt  <->  DetectionManifestTest   (CI, no server)
```

### New packages and responsibilities

| File | Responsibility |
| --- | --- |
| `src/main/java/de/jpx3/intave/platform/ServerPlatform.java` | Cached platform enum and detection order. |
| `src/main/java/de/jpx3/intave/platform/SourbyCraftBridge.java` | Reflection-only probe: `isSourbyCraft()`, `findScheduler()`, `buildNumber()`. Fails closed. |
| `src/main/java/de/jpx3/intave/executor/task/AuroraTaskScheduler.java` | `TaskScheduler` implementation over a reflected `AuroraScheduler` object. No SourbyAPI imports. |
| `src/main/java/de/jpx3/intave/executor/task/Tasks.java` | Selects the scheduler; exposes `platform()`/`schedulerName()` for diagnostics. |
| `src/main/java/de/jpx3/intave/command/stages/DiagnosticsStage.java` | `environment` output adds platform/build/scheduler. |
| `src/test/java/de/jpx3/intave/parity/*` | Source-based detection manifest and parity gate. |
| `src/test/resources/detection_parity/baseline-units.txt` | Reviewed baseline of registered checks and active parts. |

### Scheduler mapping

| Intave `TaskScheduler` | `AuroraScheduler` method | Notes |
| --- | --- | --- |
| `startSync` | `runGlobal` / `runGlobalLater` / `runGlobalTimer` | Same global region semantics as `FoliaTaskScheduler`. |
| `startUserSync` | `runFor` / `runForLater` / `runForTimer` | Pass a `retired` callback to remove stale handles when a player leaves. |
| `startRegionSync` | `runAt` / `runAtLater` / `runAtTimer` (World, chunkX, chunkZ) | Use the chunk overload; do not allocate a `Location`. |
| `startAsync` | `runAsync` / `runAsyncLater` / `runAsyncTimer` | Convert ticks to `Duration.ofMillis(ticks * 50L)`. |
| `stop` | `TaskHandle.cancel()` | Keep Intave's `Task -> handle` registry. |
| `stopAll` | iterate registry and `cancel()` | No batch-cancel in the API. |

AuroraScheduler's `RegionTask`/`EntityTask` markers are for the Aurora Bridge only. Intave is native,
so it must not use them.

## Detection-parity gate

The gate is a JUnit test that runs in plain `./gradlew test`:

- `CHECK|<simple-name>|<always-enabled>` for every registered top-level check.
- `PART|<parent>|<part>|ACTIVE` for every attached part.
- `PART|<parent>|<part>|DECLARED` for a declared part that is intentionally not attached
  (e.g. `PacketLoss`, `JumpAndPlace`, `SneakAndPlace`, `PacketOrder`).
- The test parses `CheckService.java` and the `check/**` sources with comments stripped, then
  compares the set to `baseline-units.txt`.
- Removing or renaming a unit, or flipping `ACTIVE` to `DECLARED`, fails CI. Adding a unit also
  fails until the baseline is consciously updated (`-Dintave.parity.update=true` writes the file
  and prints the diff).
- A second assertion checks that every non-always check has `check.<key>.enabled: true` in
  `src/main/resources/advanced.yml`, so a missing key cannot silently disable detection.

## Consequences

### Positive

- No new dependency, no shading, no Java 8 runtime risk.
- Intave uses SourbyAPI's stable scheduler contract on SourbyCraft while keeping the Folia path as a
  one-property fallback.
- Detection parity becomes a hard CI invariant instead of a review convention.
- The GPL boundary remains one-way: Intave calls a server-provided service reflectively.

### Negative / risks

- Reflection method names can drift across SourbyCraft builds; the bridge must fail closed and log.
- The adapter cannot be type-checked at compile time. Contract tests and a Build 57 boot test are
  mandatory before `aurora` becomes the default.
- The current master has scheduler bugs (M2: `user.player()` evaluated at schedule time; M3:
  `PeriodicEntityCoverageSelector` can double-schedule). They are tracked as T5 and must land before
  the Aurora default changes.
- SourbyCraft's region ownership checks are stricter than Bukkit's; `Synchronizer.synchronize(Runnable)`
  (global) is not region-correct for entity work and must be deprecated/migrated.

## Follow-ups (not in the first PR)

1. SourbyMetrics snapshot in `/intave diagnostics` (read-only, any thread).
2. AWF world events and `AuroraWorlds.listWorlds()` for dynamic-world cache maintenance.
3. `AuroraProfiles` profile-switch events (only when MultiProfile is enabled).
4. A `runSourbyCraft26_2` development task with a pinned server jar and SHA-256.
5. Promote `aurora` to default only after a Build 57 boot + parity evidence.

## Rejected alternatives

| Alternative | Why rejected |
| --- | --- |
| Merge/rebase `feature/folia-support` | Regresses master's scheduler (delay-0 periodic rejection, one-shot leaks, broken equality). |
| Hard `compileOnly` SourbyAPI in the main source set | Java 8 runtime linkage risk, GPL linkage surface, artifact/CI complexity, no compile-time guarantee that only adapter classes use it. |
| Separate `src/sourbyapi/java` source set in the first PR | Requires an artifact and build wiring; not needed for the reflection adapter. Revisit only if typed API use grows. |
| Vendor a SourbyAPI stub or jar | GPL-3.0-only; do not copy into a PolyForm project. |
| Depend on the Aurora Bridge for scheduling | Bridge is off by default and cannot make a plugin region-aware; Intave is native. |
