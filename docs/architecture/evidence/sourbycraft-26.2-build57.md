> **Update (2026-11-11):** the 26.2 key-mapping gap described below is resolved by
> ProtocolLib 5.5.0-SNAPSHOT. This document records the original 5.4.0 evidence; the Locate
> fallback remains for servers that still run 5.4.0.
>
# SourbyCraft 26.2 Build 57 boot evidence

- **Date:** 2026-11-11
- **Server:** SourbyCraft 26.2 Build 57, tag `v26.2-r57`, commit `e65839e4`
- **Server artifact SHA-256:** `c744fd275608de01b41b52ea6a98d77d80dedc63da2b89567c0fd6a80e646b6c`
- **Intave artifact:** `Intave-bundled.jar` from `feature/sourbyapi-parity` (`90fc32fd`),
  SHA-256 `2ca742323e3afc4706f67d8ba87fcd5242c94a44f51e32c0d5a8b772edcda8d3`
- **Java:** 25.0.2
- **ProtocolLib:** 5.4.0 (SourbyCraft's pinned auto-provisioned build)
- **ViaVersion / ViaBackwards:** 5.10.0

## Result

Intave loads **natively** (`folia-supported: true`), not through the Aurora Bridge:

```
Native / Bridged: 4 / 0
Native  Intave
Native  ProtocolLib
Native  ViaBackwards
Native  ViaVersion
[Intave] Intave booted successfully
```

`/intave diagnostics environment` reports:

```
Platform is SOURBYCRAFT (57), scheduler aurora
```

With `-Dintave.sourbycraft.scheduler=folia`, the same build reports:

```
Platform is SOURBYCRAFT (57), scheduler folia
```

Both boots reached `Done (...)` and shut down cleanly (exit 0).

## Compatibility fixes verified in T9

1. **ProtocolLib 5.4.0 / 26.2 key rename:** `ExplosionReader.setSilentDefaults()` now catches the
   unavailable `getSoundEffects()` registry path (`ResourceLocation` → `Identifier`) and keeps the
   motion/particle silencing. `ProtocolLibraryAdapter` already logs a warning and continues instead
   of rejecting ProtocolLib.
2. **Scoreboard self-test thread affinity:** `ReaderTests.testNativeScoreboardPackets` now skips
   itself when the current thread is not `RegionizedServer.isGlobalTickThread()`. SourbyCraft
   requires the global tick thread to create a scoreboard; the test no longer fails the in-server
   suite on a region thread.
3. **Tick-end hook:** `MinecraftServer` no longer exposes the legacy `List` tickable field on
   SourbyCraft. Intave now logs a warning and schedules its tick-end subscribers through the global
   tick scheduler as a fallback. There are currently no subscribers, so this does not change
   detection.

## Forced in-server test runs

Both schedulers were tested with `-Dintave.test.success=shutdown`:

```
# Aurora (default)
Platform is SOURBYCRAFT (57), scheduler aurora
[Intave] All integration tests completed successfully.
[Intave] Shutting down server due to test success

# Folia fallback
Platform is SOURBYCRAFT (57), scheduler folia
[Intave] All integration tests completed successfully.
[Intave] Shutting down server due to test success
```

## What this proves

- SourbyAPI detection, reflection bridge, `AuroraScheduler` selection, and fallback selection work
  end-to-end on Build 57.
- Intave is admitted natively.
- Both scheduler paths pass the in-server integration suite with no failed `PR::` tests.
- `DetectionManifestTest` remains green (12 checks / 53 active parts).

## Reproduce

```bash
# 1. Build the plugin
./gradlew shadowJar

# 2. Download the server (verified by SourbyCraft's published .sha256)
gh release download v26.2-r57 -R YanIanZ/SourbyCraft \
  -p SourbyCraft-26.2-REL.jar -p SourbyCraft-26.2-REL.jar.sha256

# 3. Run with a disposable server directory containing eula.txt and plugins/Intave-bundled.jar
java -Xms1G -Xmx2G -Dcom.mojang.eula.agree=true -jar SourbyCraft-26.2-REL.jar --nogui

# 4. In the console
plugins
intave diagnostics environment
```

Force the Folia path with `-Dintave.sourbycraft.scheduler=folia` or
`JAVA_TOOL_OPTIONS=-Dintave.sourbycraft.scheduler=folia`.
