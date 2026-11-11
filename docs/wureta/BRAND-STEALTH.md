# Wureta brand and stealth profile

The Wureta branch ships as **`WuretaCore`**, a neutral server-core brand, instead of exposing the
Intave name to players and casual client fingerprinting. This is operational branding, not a claim
of cryptographic invisibility.

## Build profile

Set in `gradle.properties`:

| Property | Wureta value | Effect |
| --- | --- | --- |
| `brandName` | `WuretaCore` | `plugin.yml` name, data folder, jar names, console prefix |
| `brandCommand` | `wureta` | Bukkit command root; old `/intave` and `/iac` are not registered |
| `brandAliases` | `wcore` | Command alias |
| `brandVersion` | `1.0.0` | `plugin.yml` version and displayed version |
| `brandAuthors` | `Wureta Network` | `plugin.yml` authors |
| `brandDescription` | `Core services for Wureta Network` | `plugin.yml` description |
| `brandPrefix` | `&8[&7Wureta&8]&7 ` | Chat/actionbar/console prefix |
| `brandStealth` | `true` | Disables custom-client/debug channels and version chatter |
| `brandChannelNamespace` | `wureta` | Channel/attribute namespace when stealth is off |

`IntaveBuildConfig` exposes `BRAND_NAME`, `BRAND_COMMAND`, `BRAND_PREFIX`,
`BRAND_CHANNEL_NAMESPACE`, and `BRAND_STEALTH` to runtime code.

## What changes for a client

- Operators and the console see `WuretaCore`, not `Intave`.
- The command root is `/wureta` (alias `/wcore`); `/intave` and `/iac` are not registered.
- Chat/actionbar/console prefixes use `Wureta`.
- `customclient` and `movdebug` channels are not registered in stealth mode.
- The attribute modifier key uses the `wureta` namespace.
- Version/update notices are suppressed; the plugin does not announce an outdated/unlisted build.
- Non-permitted players cannot tab-complete or invoke the command; chat attempts are redirected.
- With `command.hide-plugin-list: true`, non-permitted players running `/plugins`, `/pl`,
  `/bukkit:plugins`, or `/paper:plugins` get a filtered list that omits `WuretaCore`; operators with
  `intave.command` still see the real list.
- The distribution JARs are `WuretaCore.jar` and `WuretaCore-bundled.jar`.

## What this does not hide

- Java package/class names (`de.jpx3.intave.*`), source-level strings, and the source repository.
  A server administrator can inspect the JAR; a remote client normally cannot.
- The console and server operators still see `WuretaCore`; the player-facing `/plugins` filter only
  removes it from the command output, not from the server filesystem or plugin manager.
- `/help` and the client command tree may still mention `/wureta`; only tab-completion for managed
  commands is filtered.
- Packet timing, transaction/keepalive behavior, and detection responses can still fingerprint an
  anticheat to a determined researcher.
- Outbound cloud/domain traffic is server-side and outside the client's view, but visible to a
  network operator.
- Other plugins (`ProtocolLib`, `ViaVersion`) remain visible and can hint at the server stack.
- Only `/plugins` and `/pl` are filtered. `/version`, `/about`, `/help`, and other server commands
  are untouched; filtering them is a separate policy decision.
- The filter is a Bukkit command listener, so it also covers 1.19+ signed command packets that the
  chat-packet filter cannot see. It does not stop a player from reading the server jar or using
  `/paper dump` if that command is permitted.

## License and attribution

The PolyForm Perimeter license and the copyright headers remain in the source and distribution.
Rebranding the runtime name for the Wureta server does not remove the license, notices, or
attribution. Do not strip copyright/license files when building a private Wureta jar.

## Reverting

Set `brandStealth=false` and restore the upstream `brandName`, `brandCommand`, `brandPrefix`,
`brandVersion`, `brandAuthors`, and `brandDescription` values. The original Intave command names,
prefix, channels, and update notices return.
