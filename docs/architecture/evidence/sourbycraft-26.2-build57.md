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

## Known failures (pre-existing 26.2 compatibility gaps, not scheduler regressions)

1. **Aurora boot:** the in-server self-test `PR::native-scoreboard` fails with
   `IllegalStateException: Cannot create new scoreboard async`. The test creates a Bukkit
   scoreboard from a global-region task; SourbyCraft requires the global tick thread for that call.
   The plugin remains enabled and detection is not disabled.
2. **Folia fallback boot:** the in-server self-test `PR::silent-explosion` fails with
   `Unable to find resources.MinecraftKey (resources.ResourceLocation, MinecraftKey)`. ProtocolLib
   5.4.0 predates Minecraft 26.2's `ResourceLocation` → `Identifier` rename. Intave's
   `ProtocolLibraryAdapter` now logs a warning and continues on 26.2, but ProtocolLib's own
   `WrappedRegistry` path is still unavailable.
3. **Tick-end hook:** `MinecraftServer` no longer exposes the legacy `List` tickable field on
   SourbyCraft. Intave now logs a warning and schedules its tick-end subscribers through the global
   tick scheduler as a fallback. There are currently no subscribers, so this does not change
   detection.

## What this proves

- SourbyAPI detection, reflection bridge, `AuroraScheduler` selection, and fallback selection work
  end-to-end on Build 57.
- Intave is admitted natively.
- The remaining failures are ProtocolLib/26.2 and test-harness thread-affinity issues, not check
  registration or removal. `DetectionManifestTest` remains green (12 checks / 53 active parts).

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
