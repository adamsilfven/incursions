# Incursions

Portals let you into other worlds. Incursions gives those worlds a chance to return the favor.

Incursions adds hostile encounters around active Nether portals and, when installed, portals from The Aether, The Undergarden, and Twilight Forest. When Stargate Journey is installed, open Stargates can also start Jaffa incursions. A lodestone surrounded by eight matching stairs can also act as an anchor. Encounters arrive in waves, sometimes with enhanced enemies, and beating them earns loot worth fighting for.

Only one incursion can run at a time. All portal types share an 18-minute cooldown after an encounter ends, so building more portals does not turn the whole thing into a loot conveyor belt. Optional dimensions stay optional; the Nether works on its own.

## Requirements

- Minecraft Java Edition 1.21.1
- NeoForge 21.1.259 or newer within the 21.1.x series
- Java 21

The Aether, The Undergarden, Twilight Forest, and Stargate Journey are optional integrations. Incursions does not require another custom mod or a particular modpack.

## Installation

Install the release JAR in the server's `mods` folder, or in your client for single-player and LAN hosting. Keep one version of Incursions installed.

The encounters run on the server. Clients can join without installing Incursions; install it on the client as well to use its pause-menu settings button. Server operators and the single-player owner can change the enable setting. Other players cannot change the server's setting.

## Operator commands (1.0.4)

Commands require operator permission level 2 and are executed on the server:

```mcfunction
/incursions trigger nether
/incursions trigger nether elite
/incursions trigger aether
/incursions trigger undergarden
/incursions trigger twilight
/incursions trigger stargate
/incursions trigger stargate elite
/incursions status
/incursions origins
/incursions validate
/incursions stop
```

Use `/incursions trigger <nether|aether|undergarden|twilight|stargate> [elite]` for any supported dimension profile. The `elite` suffix requests an immediate forced elite. A trigger works only when that profile is available and has an eligible active portal or lodestone anchor. `status` shows encounter/cooldown state, `origins` reports indexed eligible origins, and `validate` prints profile diagnostics. `stop` ends the current encounter without victory rewards. Automatic encounter scheduling and default balance are unchanged.

Stargate incursions require Stargate Journey 0.6.55 (Minecraft 1.21.1), a complete Stargate with an open wormhole, and nearby players. Either end of an open connection qualifies; closed or unassembled Stargates do not. An incursion already underway may continue after the wormhole closes naturally; breaking the gate base ends it. Jaffa arrive with their normal armor and staff weapons. The shared 18-minute cooldown and all existing profiles are unchanged.

Since 1.0.3, temporary missing required mobs and obstructed spawn areas have a 30-second recovery window, and unsuccessful encounters report a reason in chat/server logs. See [the changelog](CHANGELOG.md) and [release notes](RELEASE_NOTES.md).

## Configuration

The server creates `incursions-server.toml`. The tested loader used `config/incursions-server.toml`; also check a world's `serverconfig` folder when using a loader or setup that stores server configs there. Stop the game or server before editing files.

| Setting | Default |
| --- | --- |
| Enabled | `true` |
| Trigger check interval | `100` ticks / 5 seconds |
| Shared cooldown | `21600` ticks / 18 minutes |
| Respect `doMobSpawning` | `true` |
| Chunk sections examined per tick | `4` |
| Scaling | `PLAYER_SCALED` |
| Prevent incursion Utheric Infection | `true` |
| Debug logging | `false` |

Times assume Minecraft's normal 20 ticks per second. Trigger checks and wave spawning are separate: built-in wave spawn intervals are 40 ticks. Changing these settings does not change Minecraft's global tick rate.

Replacing the mod JAR does not reset saved configuration. The approved defaults and a backup-first Windows reset helper are under [`defaults/`](defaults/). That helper resets standard Incursions config locations, including saved worlds in the selected instance; read its instructions before running it.

Profiles, creature pools, wave schedules, and rewards can be customized with datapacks. Existing datapack overrides take priority over built-in definitions and are not removed by a config reset.

## Building

The repository includes the Gradle 8.10.2 wrapper. With a Java 21 JDK installed:

```sh
./gradlew test build
```

On Windows:

```bat
gradlew.bat test build
```

Compiled JARs are written to `build/libs/`. The build downloads its Gradle, NeoForge, Minecraft, and test dependencies, so an initial build needs internet access.

## Reporting problems

Include the Minecraft, NeoForge, and Incursions versions, your optional dimension mods, what happened, and the relevant server log. Mention any custom Incursions config or datapack overrides. A screenshot helps with visual issues; a log helps explain why the server did something.

## License

Copyright 2026 Adam Silfven. All rights reserved. See [LICENSE](LICENSE).
