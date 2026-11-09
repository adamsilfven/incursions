# Incursions 1.0.3

Release for **Minecraft 1.21.1**, **NeoForge 21.1.259+** (within the 21.1.x series), and **Java 21**.

This update adds operator tools and makes encounters more resilient when required enemies temporarily go missing or a portal spawn area becomes blocked.

## What's new

- **Operator commands:** Use `/incursions trigger <nether|aether|undergarden|twilight> [elite]` to manually start an eligible encounter. `elite` forces an elite to be scheduled immediately.
- **Inspection and control:** `/incursions status`, `/incursions origins`, `/incursions validate`, and `/incursions stop`. These commands require permission level 2.
- **Missing-mob recovery:** A tracked required enemy may recover within 30 seconds before the encounter fails. The mod attempts to reattach the entity by UUID.
- **Obstructed spawn recovery:** A blocked origin now gets a 30-second recovery window rather than failing immediately.
- **Failure feedback:** Players nearby receive a message when an incursion ends without victory; the server log records the reason.
- **Manual trigger lookup fix:** Namespaced profile paths are resolved correctly.

Manually stopping an encounter **does not award victory loot**. Manual triggers require an eligible active portal or lodestone anchor and respect the enabled setting and server game rules.

## Existing gameplay remains intact

Nether encounters and optional Aether, Undergarden, and Twilight Forest integrations remain. The shared cooldown stays **21600 ticks (18 minutes)**, automatic trigger checks remain **100 ticks (5 seconds)** by default, and built-in wave spawn intervals remain unchanged. No enemy-pool or loot rebalance is included.

Saved configuration is **not reset by updating the JAR**. Existing datapack overrides continue to take precedence over built-in definitions. A backup-first defaults/reset helper is available under [`defaults/`](defaults/).

## Updating

1. Stop the game or server.
2. Remove the previous Incursions JAR from the active `mods` folder.
3. Install the 1.0.3 JAR and keep only one Incursions version installed.
4. Start the game or server. If you changed test-only config values earlier, review the defaults/reset instructions before resetting anything.

The mod is server-driven. Installing on the client is optional for multiplayer connections, but is needed for the pause-menu settings button.

## Verification

The source for 1.0.3 passed the repository's automated GitHub Actions build and test workflow: [build run 41935711269](https://github.com/adamsilfven/incursions/actions/runs/41935711269). That result does not imply a new in-game multiplayer test for this documentation update.
