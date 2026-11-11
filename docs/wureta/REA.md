# REA artifact analysis — Wureta clients

## Status

The source repositories were cloned and analyzed completely with repository tools. REA was selected
for artifact-level analysis, but its Java decompiler provider (JEB) is not running on this host:

```
inspect_jeb_client -> capability_unavailable (unsupported_provider)
"Start a JEB client serving MCP (default http://127.0.0.1:8425/mcp)
 or set REA_JEB_MCP_URL to the running endpoint."
```

This is a provider availability limitation, not a claim that the artifacts were analyzed by JEB.
The three repositories are complete source, which the REA workflow classifies as ordinary source
analysis rather than a shipped-binary investigation.

## Completing the REA pass

Prerequisites:

- JEB installed and serving MCP at `http://127.0.0.1:8425/mcp` (or `REA_JEB_MCP_URL` set).
- A built or released JAR for each client. The current checkouts do not contain release assets:
  - Meteor Client: build with `./gradlew build` or obtain the matching Modrinth artifact.
  - Wurst7: build with `./gradlew build` or obtain the matching release.
  - LiquidBounce: build with `./gradlew build` or obtain the matching release.

Suggested sequence per JAR:

1. `open_jeb_project` with the JAR path.
2. `list_jeb_units` filtered to the module/hack packages.
3. `decompile_jeb_item` for the high-value classes named in `CLIENT-THREAT-MODEL.md`.
4. `inspect_artifact` for the archive inventory and class hashes.
5. Compare the release JAR inventory with the source checkout (`compare_artifacts`) and record
   whether the shipped artifact matches the analyzed commit.

Focus classes:

- Meteor: `KillAura`, `Criticals`, `Velocity`, `Blink`, `Timer`, `Scaffold`, `InvUtils`, `Rotations`.
- Wurst7: `KillauraHack`, `CriticalsHack`, `MaceDmgHack`, `BlinkHack`, `NukerHack`,
  `ScaffoldWalkHack`, `AutoTotemHack`.
- LiquidBounce: `ModuleTickBase`, `ModuleFakeLag`, `ModuleBacktrack`, `ModuleKillAura`,
  `ModuleMaceKill`, `ModuleScaffold`, `ModuleChestStealer`.

## Evidence to retain

- JAR SHA-256 and source commit.
- JEB project/unit paths for each decompiled class.
- Exact method bodies or pseudocode for the signal paths (rotation order, packet writes, timer
  balance, inventory clicks).
- Any mismatch between source and shipped JAR.

## What the source pass already established

- 26.2 attack packets carry no hit position; rotation evidence must come from movement history.
- TickBase-style timer bursts can keep an average rate near 1.0.
- Selective lag keeps actions/KeepAlive flowing while positions are delayed.
- Criticals/MaceKill use sub-tick vertical offsets and teleport bursts.
- Inventory and scaffold bypasses rely on same-tick state transitions and rotation callbacks.
- Wurst `ReachHack` and client-side `AntiKnockback` are not server-observable.
