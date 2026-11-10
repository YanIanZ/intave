# SourbyAPI Integration and Detection-Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional SourbyAPI 26.2 integration to Intave without changing any check, and make
detection parity a hard CI invariant.

**Architecture:** Keep master's Folia scheduler as the default. Add a reflection-only platform probe
and an opt-in `AuroraTaskScheduler`; add a source-based detection manifest test that fails on any
check/check-part removal or default-disablement. Do not merge `feature/folia-support`.

**Tech Stack:** Java 8 main source, JDK 25 toolchain, Gradle 9.7.1, JUnit 5, Bukkit/Paper/Folia API,
SourbyAPI 26.2 Build 57 (runtime only, reflected).

**Spec:** `docs/architecture/SOURBYAPI-INTEGRATION.md`

## Global Constraints

- `src/main` compiles with `sourceCompatibility`/`targetCompatibility` 1.8. No `var`, records,
  text blocks, switch expressions, pattern matching, or post-8 APIs.
- No `dev.iyanz.*` import in `src/main`; the first integration is reflection-only.
- No check registration, check-part attachment, or `check.*.enabled` default may change in this
  plan except through a reviewed baseline update.
- `AuroraTaskScheduler` is the default on SourbyCraft Build 55+; the Folia scheduler is the fallback
  when SourbyAPI or the reflected contract is unavailable. Operators can force Folia with
  `-Dintave.sourbycraft.scheduler=folia`.
- Do not shade, vendor, or commit SourbyAPI binaries. SourbyAPI is GPL-3.0-only.
- Preserve the Intave PolyForm copyright header on new Java files.
- Do not commit or push without owner approval.

## Review Focus

- **Reflection drift:** a SourbyCraft build renames or removes an `AuroraScheduler` method. The
  bridge must fail closed (Folia/Bukkit fallback) and log once, never throw at plugin enable.
- **Java 8 classloading:** a main class accidentally references a `dev.iyanz` type. `jdeps`/`javap`
  guards must catch it, and the adapter must contain only `Object`/JDK/Bukkit types in signatures.
- **Parity baseline drift:** the source parser misses a multi-line `appendCheckParts(...)` or a
  commented-out wiring. The parser must strip comments and have a fixture-based self-test.
- **Scheduler lifecycle:** a one-shot task completes before its handle is stored, a player leaves
  before a user task runs, or `stopAll` runs after plugin disable. The registry must not leak or NPE.
- **SourbyCraft admission:** Intave must remain native (`folia-supported: true`) and must not be
  forced through the off-by-default Aurora Bridge.
- **Default scheduler:** Aurora when SourbyAPI exposes `findScheduler()`; Folia otherwise. A
  `-Dintave.sourbycraft.scheduler=folia` run must be behaviorally identical to master.

---

### Task 1: Detection-parity baseline and test

**Files:**
- Create: `src/test/java/de/jpx3/intave/parity/DetectionManifest.java`
- Create: `src/test/java/de/jpx3/intave/parity/DetectionManifestTest.java`
- Create: `src/test/resources/detection_parity/baseline-units.txt`

**Interfaces:**
- Produces: `DetectionManifest.collect(Path checkSourceRoot, Path checkServiceSource) -> SortedSet<String>`
- Produces: line format `CHECK|<simple-name>|<always-enabled>` and
  `PART|<parent>|<part>|ACTIVE|DECLARED`
- Consumes: nothing from other tasks.

- [ ] **Step 1: Write the manifest collector** that walks `src/main/java/de/jpx3/intave/check/**/*.java`,
      strips `//` and `/* ... */` comments, and extracts:
      - `CHECK|Name|true` for a class whose declaration contains `extends Check` and whose simple name
        is passed to `CheckService.addCheck(...)`; `false` otherwise.
      - `PART|Parent|Part|ACTIVE` for `appendCheckPart(new Part(`, any element of
        `appendCheckParts(new A(...), new B(...))`, and `appendPlayerCheckPart(Part.class)`.
      - `PART|Parent|Part|DECLARED` for a `Part` class that extends `CheckPart`/`PlayerCheckPart`
        but has no active attachment.
      Sort the result with a stable comparator.

- [ ] **Step 2: Write the baseline resource** from the current master output. Verify the counts are
      12 `CHECK` rows and 53 `PART ... ACTIVE` rows. Do not hand-write the file; generate it once with
      the collector and review the diff.

- [ ] **Step 3: Write the failing test first** before adding the baseline:
      `DetectionManifestTest.baselineMatchesCurrentCheckInventory()` asserts set equality and a
      readable diff (missing/extra lines). `defaultEnablementMatchesAdvancedYml()` asserts every
      `CHECK|...|false` row has `check.<configuration-key>.enabled: true` in
      `src/main/resources/advanced.yml`. `parserIgnoresCommentedWiring()` uses a temporary fixture
      containing commented `appendPlayerCheckPart(PacketOrder.class);` and asserts it is not `ACTIVE`.

- [ ] **Step 4: Run and verify.** Run:
      `./gradlew test --tests 'de.jpx3.intave.parity.DetectionManifestTest'`
      Expected: PASS on master. Then temporarily delete one `CHECK` line from the baseline and rerun;
      expected: FAIL with a diff. Restore the line.

- [ ] **Step 5: Commit** `test: add detection parity manifest gate`.

**Acceptance:** The test runs in plain `./gradlew test`, needs no server, and fails if a check or
active part disappears or its default enablement changes.

---

### Task 2: Platform probe and reflection bridge

**Files:**
- Create: `src/main/java/de/jpx3/intave/platform/ServerPlatform.java`
- Create: `src/main/java/de/jpx3/intave/platform/SourbyCraftBridge.java`
- Create: `src/test/java/de/jpx3/intave/platform/ServerPlatformTest.java`
- Create: `src/test/java/de/jpx3/intave/platform/SourbyCraftBridgeTest.java`

**Interfaces:**
- Produces: `ServerPlatform.current() -> ServerPlatform` with `BUKKIT`, `FOLIA`, `SOURBYCRAFT`.
- Produces: `SourbyCraftBridge.isSourbyCraft() -> boolean`,
  `findScheduler() -> Object` (nullable), `buildNumber() -> String` (`"unknown"` on failure).
- Produces: `SourbyCraftBridge.schedulerRequested() -> boolean` from
  `System.getProperty("intave.sourbycraft.scheduler", "aurora")`; `folia`/`false` force the fallback.

- [ ] **Step 1: Write the negative-path tests.** `SourbyCraftBridgeTest` asserts that with no
      SourbyAPI on the classpath, `isSourbyCraft()` is `false`, `findScheduler()` is `null`, and no
      exception escapes. `ServerPlatformTest` asserts `BUKKIT` or `FOLIA` is selected when the
      SourbyAPI class is absent.

- [ ] **Step 2: Implement the bridge.** Use
      `Class.forName("dev.iyanz.sourbycraft.api.SourbyAPI", false, SourbyCraftBridge.class.getClassLoader())`,
      then reflect `isSourbyCraft`, `findScheduler` (returns `Optional`), and `findBuild` ->
      `buildNumber`. Catch `Throwable` (including `LinkageError`) and cache only successful
      positive probes; a miss may be retried after startup. Log at debug/info once, never throw.

- [ ] **Step 3: Implement `ServerPlatform`.** Detection order: SourbyCraft brand, then
      `Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, loader)`, then
      `BUKKIT`. Cache the result.

- [ ] **Step 4: Run** `./gradlew test --tests 'de.jpx3.intave.platform.*'` and
      `./gradlew test --tests 'de.jpx3.intave.parity.*'`. Expected: PASS.

- [ ] **Step 5: Commit** `feat: add reflection-only SourbyCraft platform probe`.

**Acceptance:** No `dev.iyanz` import in main; non-SourbyCraft startup behavior is unchanged.

---

### Task 3: `AuroraTaskScheduler` reflection adapter

**Files:**
- Create: `src/main/java/de/jpx3/intave/executor/task/AuroraTaskScheduler.java`
- Create: `src/test/java/de/jpx3/intave/executor/task/AuroraTaskSchedulerTest.java`
- Create: `src/test/java/de/jpx3/intave/executor/task/TaskSchedulerContractTest.java`

**Interfaces:**
- Consumes: `TaskScheduler` (`startSync`, `startUserSync`, `startRegionSync`, `startAsync`, `stop`,
  `stopAll`) and `Task` (`delay()`, `period()`, `run()`, `name()`).
- Produces: `AuroraTaskScheduler(Object scheduler)`; throws `IllegalArgumentException` if the object
  does not expose the required `AuroraScheduler` methods.

- [ ] **Step 1: Write the contract test** with a `RecordingTaskScheduler` that records
      `startSync/startUserSync/startRegionSync/startAsync/stop/stopAll` calls. Use it as the shared
      base for `AuroraTaskSchedulerTest` and a future `FoliaTaskSchedulerTest`.

- [ ] **Step 2: Write the failing adapter test** with a `FakeAuroraScheduler` exposing the exact
      method signatures (`runGlobal`, `runGlobalLater`, `runGlobalTimer`, `runFor`, `runForLater`,
      `runForTimer`, `runAt`, `runAtLater`, `runAtTimer`, `runAsync`, `runAsyncLater`,
      `runAsyncTimer`) and a `FakeTaskHandle.cancel()`. Assert:
      - periodic tasks use the `*Timer` method with `Math.max(1, delay)`;
      - one-shot tasks use `runGlobal`/`runGlobalLater` and remove their registry entry on completion;
      - user tasks pass the player entity and a non-null retired callback;
      - region tasks pass `(world, chunkX, chunkZ)`, not a `Location`;
      - async delays are `Duration.ofMillis(ticks * 50L)`;
      - `stop` cancels exactly once; `stopAll` cancels every live handle;
      - a second `start*` on the same `Task` throws `IllegalStateException`.

- [ ] **Step 3: Implement the adapter.** Resolve `Method` objects once in the constructor from
      `scheduler.getClass()`/interfaces. Use `getMethod` with exact parameter types. Store
      `Map<Task, Object>` handles. Wrap one-shot `Runnable` in `try/finally` to remove the entry.
      Catch `InvocationTargetException` and rethrow the cause as `IllegalStateException` with the
      task name. Do not import `dev.iyanz` types; use `Object` and `java.util.function.Consumer`.

- [ ] **Step 4: Run** `./gradlew test --tests 'de.jpx3.intave.executor.task.*'`. Expected: PASS.

- [ ] **Step 5: Commit** `feat: add reflection AuroraScheduler adapter`.

**Acceptance:** The adapter has no SourbyAPI imports, no Java 8 runtime linkage, and passes the
scheduler contract test.

---

### Task 4: Wire scheduler selection, flag, and diagnostics

**Files:**
- Modify: `src/main/java/de/jpx3/intave/executor/task/Tasks.java`
- Modify: `src/main/java/de/jpx3/intave/command/stages/DiagnosticsStage.java`
- Create: `src/test/java/de/jpx3/intave/executor/task/TasksSelectionTest.java`

**Interfaces:**
- Produces: `Tasks.platform() -> ServerPlatform`, `Tasks.schedulerName() -> String`.
- Produces: package-private `Tasks.selectScheduler(boolean sourby, boolean folia, Object scheduler,
  boolean requested) -> TaskScheduler` for tests.
- Consumes: `ServerPlatform` and `SourbyCraftBridge` from Task 2; `AuroraTaskScheduler` from Task 3.

- [ ] **Step 1: Write the failing selection test.** Assert:
      - `sourby=true, scheduler=null` -> Folia (or Bukkit if `folia=false`);
      - `sourby=true, scheduler!=null, requested=true` -> `AuroraTaskScheduler` (default);
      - `sourby=true, scheduler!=null, requested=false` -> Folia (explicit opt-out);
      - `sourby=false, folia=true` -> `FoliaTaskScheduler`;
      - `sourby=false, folia=false` -> `BukkitTaskScheduler`.

- [ ] **Step 2: Refactor `Tasks`.** Replace the static initializer with `selectScheduler(...)`.
      Keep `isFoliaServer()` returning `platform() != BUKKIT` for compatibility. Keep the current
      `delayed`, `periodic`, `start*`, `stop` signatures unchanged.

- [ ] **Step 3: Add the diagnostics line.** In `DiagnosticsStage.environment`, after the Intave
      version line, send:
      `Platform is <SOURBYCRAFT|FOLIA|BUKKIT> (<build>), scheduler <aurora|folia|bukkit>`.

- [ ] **Step 4: Run** `./gradlew test --tests 'de.jpx3.intave.executor.task.*'` and
      `./gradlew test --tests 'de.jpx3.intave.parity.*'`. Expected: PASS.

- [ ] **Step 5: Commit** `feat: select Aurora scheduler only when explicitly enabled`.

**Acceptance:** Default behavior on every platform is identical to master; the flag is opt-in.

---

### Task 5: Fix known master scheduler bugs

**Files:**
- Modify: `src/main/java/de/jpx3/intave/executor/Synchronizer.java`
- Modify: `src/main/java/de/jpx3/intave/module/tracker/entity/PeriodicEntityCoverageSelector.java`
- Create: `src/test/java/de/jpx3/intave/executor/SynchronizerTest.java`
- Create: `src/test/java/de/jpx3/intave/module/tracker/entity/PeriodicEntityCoverageSelectorTest.java`

**Interfaces:**
- No public signature changes. `Synchronizer.synchronize(User, Runnable)` must fail closed if the
  player is gone at schedule time; `PeriodicEntityCoverageSelector.enableTask()` must cancel its
  previous task before scheduling a new one.

- [ ] **Step 1: Write the failing tests.** `SynchronizerTest` uses a fake `User` whose `player()`
      throws/returns null and asserts no exception escapes and no task is scheduled.
      `PeriodicEntityCoverageSelectorTest` calls `enableTask()` twice and asserts the first task is
      cancelled before the second starts.

- [ ] **Step 2: Implement the guards.** Resolve `user.player()` once inside a `try/catch` at schedule
      time; on failure, log at debug and return. In `enableTask()`, cancel the existing task before
      assigning the new one.

- [ ] **Step 3: Run** `./gradlew test --tests 'de.jpx3.intave.executor.SynchronizerTest' --tests 'de.jpx3.intave.module.tracker.entity.PeriodicEntityCoverageSelectorTest'`.
      Expected: PASS.

- [ ] **Step 4: Commit** `fix: harden user scheduler and entity coverage task lifecycle`.

**Acceptance:** No silent task loss or duplicate selector; existing scheduler tests remain green.

---

### Task 6: QA/CI guards

**Files:**
- Modify: `.github/workflows/tests.yml`
- Create: `scripts/check-java8-bytecode.sh`
- Modify: `build.gradle.kts` only if a Gradle task is preferred over the script.
- Create/extend: `src/test/java/de/jpx3/intave/executor/task/TaskSchedulerContractTest.java`

**Interfaces:**
- Produces: a CI step that fails when `build/libs/Intave.jar` contains a class with major version
  other than 52, or when `Intave-bundled.jar` contains `dev/iyanz/sourbycraft`.
- Consumes: the Task 1 and Task 3 tests.

- [ ] **Step 1: Add the bytecode guard.** `scripts/check-java8-bytecode.sh` runs `javap -v` on the
      built main classes (or `jdeps --multi-release 8`) and exits non-zero on a major version other
      than 52 or a `dev/iyanz` reference in the shaded jar. Add it after `./gradlew build` in CI.

- [ ] **Step 2: Add the shadowJar assertion.** In the release workflow, assert
      `! unzip -l build/libs/Intave-bundled.jar | grep -q 'dev/iyanz/sourbycraft'`.

- [ ] **Step 3: Add the optional SourbyCraft boot job.** Use `workflow_dispatch`/label gating, download
      `SourbyCraft-26.2-REL.jar` and `sourbyapi-57.zip` from `v26.2-r57`, verify the pinned SHA-256,
      run the server with `-Dintave.sourbycraft.scheduler=aurora`, and assert the diagnostics line.
      Do not make normal PRs depend on the private toolchain.

- [ ] **Step 4: Run** `./gradlew test`, `./gradlew testShadedJar`, `./gradlew testProtocolResources`.
      Expected: PASS locally.

- [ ] **Step 5: Commit** `ci: guard Java 8 bytecode and SourbyAPI shading`.

**Acceptance:** CI fails on Java 8 regressions and on accidental SourbyAPI shading; normal PRs remain
credential-free.

---

### Task 7: SourbyCraft 26.2 Build 57 boot evidence

**Files:**
- Create: `docs/architecture/evidence/sourbycraft-26.2-build57.md`
- Optional: `build.gradle.kts` `runSourbyCraft26_2` task (manual, pinned jar).

**Interfaces:**
- Consumes: the built `Intave-bundled.jar`, `SourbyCraft-26.2-REL.jar`, `sourbyapi-57.zip`.
- Produces: a recorded boot log, `/intave diagnostics` output, check inventory, and scheduler proof.

- [ ] **Step 1: Download and verify** the release assets. Expected SHA-256:
      `sourbyapi-57.zip` = `51049834fc0579a7efe561c0ee4a24ca975c633fa970be598f1e3b24410b37c5`.
- [ ] **Step 2: Boot** with `-Dintave.sourbycraft.scheduler=aurora`, `online-mode=false`, and a
      disposable world. Wait for `Done (`.
- [ ] **Step 3: Capture evidence:** `/plugins` shows Intave `NATIVE`; `/intave diagnostics
      environment` shows `SOURBYCRAFT (57)`, `scheduler aurora`; the check inventory matches the
      master baseline; no `IllegalStateException`, `ClassCastException`, or scheduler warnings in
      `latest.log`.
- [ ] **Step 4: Repeat** with the default `folia` scheduler and confirm identical check inventory
      and no new errors.
- [ ] **Step 5: Commit** the evidence document (not the server jar).

**Acceptance:** Build 57 boot is documented with before/after evidence; `aurora` remains opt-in until
this passes.

---

### Task 8: Documentation and release gate

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md` (development section only)
- Create: `docs/architecture/SOURBYAPI-INTEGRATION.md` (already written)
- Create: `docs/architecture/evidence/sourbycraft-26.2-build57.md` (Task 7)

- [ ] **Step 1: Add a short "SourbyCraft / SourbyAPI" section to `AGENTS.md`** with the platform
      matrix, the opt-in property, the Java 8 rule, and the parity gate command.
- [ ] **Step 2: Add a README note** that SourbyCraft 26.2 is supported natively through
      `folia-supported: true`, and that Aurora scheduling is opt-in.
- [ ] **Step 3: Run the release checklist:** `./gradlew build`, `./gradlew test`,
      `./gradlew testShadedJar`, `./gradlew testProtocolResources`, `./gradlew testCoverage`.
- [ ] **Step 4: Final review** by the Code Reviewer and Parity Auditor. Require zero check-count
      changes, zero `dev/iyanz` in the shaded jar, and a green Build 57 evidence run.
- [ ] **Step 5: Commit** `docs: document SourbyAPI integration and parity gate`.

**Acceptance:** AGENTS/README/ADR match the shipped behavior; release gate is explicit and repeatable.

---

## Execution Order and Ownership

| Task | Owner (office role) | Model | Depends on |
| --- | --- | --- | --- |
| T1 parity gate | Parity Auditor | `vultr/qwen3.8-flash-next` | — |
| T2 platform bridge | Integration Engineer | `vultr/glm-5.3-flash` | T1 |
| T3 Aurora adapter | Chief Architect | `vultr/deepseek-v4.1-flash-1017` | T2 |
| T4 wiring/diagnostics | PM | `vultr/deepseek-v4.1-flash-1017` | T2, T3 |
| T5 master bugs | Code Reviewer | `vultr/minimax-m3.1-flash` | — |
| T6 QA/CI | QA Engineer | `vultr/qwen3.8-flash-next` | T1, T3 |
| T7 Build 57 boot | Build Engineer | `vultr/glm-5.3-flash` | T3, T4, T6 |
| T8 docs/release | PM | `vultr/deepseek-v4.1-flash-1017` | T5, T6, T7 |

## Definition of Done

- [ ] `feature/folia-support` is retired (branch archived or deleted after owner approval); no merge.
- [ ] `DetectionManifestTest` green on master and on the integration branch; baseline diff reviewed.
- [ ] No `dev.iyanz` import in `src/main`; `scripts/check-java8-bytecode.sh` green.
- [ ] Default scheduler selection is byte-for-byte behaviorally identical to master on Bukkit and on
      the explicit `-Dintave.sourbycraft.scheduler=folia` path.
- [ ] Aurora adapter is the default on Build 57 and has a hard Folia fallback; Build 57 evidence
      recorded.
- [ ] `./gradlew test`, `testShadedJar`, `testProtocolResources`, `testCoverage` green.
- [ ] AGENTS/README/ADR describe the support matrix and the opt-in flag.
