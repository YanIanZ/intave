# Intave

Enterprise anticheat plugin for Minecraft servers (Spigot/Paper/Folia), written in Java and built with Gradle.
Source-available under the PolyForm Perimeter License 1.0.0 (see `LICENSE.md`); competitive redistribution is forbidden.
Supports Minecraft 1.8.8 through 26.3 through ProtocolLib/PacketEvents and ViaVersion adapters.

## Build and test

The build requires a JDK 25 toolchain (Gradle provisions it through the foojay resolver) and Git.

- `./gradlew build` — compile, run unit tests, and create `build/libs/Intave.jar` (minimal) plus `build/libs/Intave-bundled.jar` (shadow JAR).
- `./gradlew test` — JUnit 5 unit tests under `src/test/java` (runs sequentially inside each JVM).
- `./gradlew testShadedJar` — library, native-library, certificate, cloud, and report tests against the bundled JAR.
- `./gradlew testProtocolResources` — protocol-version and reflection-mapping tests against the bundled JAR.
- `./gradlew testCoverage` — unit tests plus the JaCoCo HTML/XML/CSV report.
- `./gradlew verifyJava8Bytecode verifyBundledJar` — fail if a main class is not major 52, or if
  SourbyAPI/GPL classes leak into the bundled JAR.
- `./gradlew testPaper1_21_4` — boot a real Paper 1.21.4 server with the plugin and run the in-server self-tests.
  Every configured version has a task (`testPaper1_8_8`, `testPaper26_3`, ...); `testAllPaperVersions` runs all of them.
- `./gradlew runPaper1_21_4` — development server with the plugin and ViaVersion/ViaBackwards, offline mode.
  Also available: `runFolia26_1_2`, `runLeaf1_21_8`, `runAuthTest1_8_8`, `runAuthTest1_20_1`, `runGommeTest1_8_8`.
- Benchmarks: `benchmarkPhysics`, `benchmarkPhysicsMemory`, `benchmarkReplacementCache`, `benchmarkWorldHeight`,
  `benchmarkCopyOnWriteIdentityMap`, `benchmarkCopyOnWriteHashMap`. Pass arguments with
  `-PbenchmarkPhysics.args="..."`.

Useful Gradle properties: `test.maxParallelForks` (positive integer) overrides forked test JVMs.
`org.gradle.parallel`, build caching, and configure-on-demand are enabled in `gradle.properties`.
CI (`.github/workflows/tests.yml`) builds the Paper version matrix and runs `testPaper<version>` for each entry.

## Java level

`compileJava` targets source/target 1.8 even though the toolchain runs on JDK 25.
Do not use `var`, records, text blocks, switch expressions, pattern matching, or post-8 APIs in `src/main`.
Tests and benchmarks (`src/test`, `src/bench`) compile at the JDK 25 language level and do use newer syntax.

## Architecture

- `de.jpx3.intave.IntavePlugin` — Bukkit entry point. Boot proceeds through `BootSegment` stages.
- `de.jpx3.intave.check` — the only place for detection logic. `Check` is a detection algorithm or cluster,
  `CheckPart` / `PlayerCheckPart` are subordinate detections. A check named `X` owns the `check/x/` subpackage;
  `CheckService` handles lifecycle and linkage.
- `de.jpx3.intave.module` — dynamic active modules. `ModuleLoader` registers them in boot order and `ModulePool`
  owns them; groups include `dispatch`, `tracker`, `feedback`, `mitigate`, `violation`, `linker`, `player`, `test`.
  Use `Modules.find(...)` to access a loaded module. `@HighOrderService` marks legacy services instantiated directly
  by the plugin.
- Static passive services live in `block`, `klass`, `resource`, `packet`, `entity`, `world`, `share`, `adapter`,
  `version`, `config`, `access`, `cloud`, `report`, `command`, and `diagnostic`; typically one endpoint class exposes
  each service.
- `de.jpx3.intave.user` — per-player state via `UserRepository.userOf(player)`. Typed metadata is reached through
  `user.meta()` (for example `user.meta().movement()`).
- `check/movement/physics` — movement simulation engine: simulators, environments, search, evaluation, and
  configuration. It is covered end-to-end by recorded movement scenarios.
- `player/collider` — simple/complex colliders (`v7Collider`, `v8Collider`, `v14Collider`, ...) and simulation results.
- `packet` — packet pipeline and converters; `module/linker` connects checks to `PacketSubscription` and
  `BukkitEventSubscription` events.
- `executor` — `Synchronizer` for main/region-thread work (Folia-aware), `BackgroundExecutors` and `Tasks` for async work.
- `block` — block cache, variants, shapes, collision, and fluids. Prefer `VolatileBlockAccess` and friends; see
  `docs/BLOCK_SYSTEM.md`.
- `platform` / SourbyAPI — SourbyCraft 26.2 is admitted natively through `folia-supported: true`.
  `AuroraScheduler` (SourbyAPI Build 55+) is the default scheduler on SourbyCraft; it is accessed
  through the reflection-only `SourbyCraftBridge`, with the Folia scheduler as fallback. Force the
  fallback with `-Dintave.sourbycraft.scheduler=folia`. Never import `dev.iyanz.*` in `src/main`;
  SourbyAPI is GPL-3.0-only and must not be shaded or vendored.
- `src/main/java/de/jpx3/intave/test` — test doubles (`FakePlayerFactory`, `FakeWorldFactory`,
  `UserFactory.createTestUserFor`, `MockEmptyInventory`) and the in-server integration framework
  (`IntegrationTests`, `@Test`, `@Before`, `@After`).

## Conventions

- Follow the Google Java Style Guide with 2-space visual indentation (`docs/CODESTYLE.md`, `docs/CodeStyle.xml`).
  Some files use tab characters that must render at width 2; preserve the file's existing style.
- Most files start with the Intave PolyForm Perimeter copyright header (year 2026). Copy it for new files.
- Do not introduce class names containing `Utils`, `Handler`, or `Manager` (PR checklist).
- Do not create a new top-level `Check` subclass without maintainer approval. Heuristic sub-checks must explain in the
  PR why bypassing them is difficult.
- Keep checks in `check`; detection logic does not belong in modules or commands.
- Never block the server thread. Use `Synchronizer`/`Tasks` for tick-bound work and `BackgroundExecutors` for async work.
  Feedback transactions (`user.tickFeedback(...)`) run only on the main thread.
- Go through the existing version abstractions (`MinecraftVersion`, `klass`, `adapter`) instead of hardcoding NMS names.
- Add tests for logic changes. For physics bugs, add a `.ptr` recording under
  `src/test/resources/physics_test_runs/` so the simulator test suite replays it. Call
  `MinecraftVersion.setCurrent(...)` before exercising version-dependent code in unit tests.
- **Detection parity is a hard invariant.** Do not remove, rename, or default-disable a check or
  active check-part. The parity gate compares the registered inventory against
  `src/test/resources/detection_parity/baseline-units.txt`; update the baseline only in a reviewed
  change that explains why detection is intentionally changing. See
  `docs/architecture/SOURBYAPI-INTEGRATION.md` and
  `docs/superpowers/plans/2026-11-11-sourbyapi-parity.md`.
- Branch names: `feature/...`, `bugfix/...`, `refactor/...`, `hotfix/...`; PRs target `dev` and are squash-merged.
  See `docs/BRANCHES.md` and `docs/CONTRIBUTING.md`.

## Documentation

- `docs/STRUCTURE.md` — check/module/service organization.
- `docs/BLOCK_SYSTEM.md` — block types, variants, shapes, and patches.
- `docs/CHEATSHEET.md` — quick access patterns (block access, transactions).
- `docs/CODESTYLE.md` — style deviations and field placement.
- `docs/CONTRIBUTING.md`, `docs/BRANCHES.md` — contribution and branch workflow.
- Public check list: <https://docs.intave.ac/mechanics/checks-01-overview.html>.
