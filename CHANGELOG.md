# Changelog

## 1.0.3

Release focus: encounter reliability and operator controls for Minecraft 1.21.1 / NeoForge 21.1.259+.

### Added
- Operator-only `/incursions` commands (permission level 2) for manual triggering, stopping, status, origin diagnostics, and profile validation.
- Manual triggers for Nether, Aether, Undergarden, and Twilight Forest profiles, including an `elite` variant.
- Failure feedback in chat and server logs when an encounter ends without victory.

### Fixed
- Required enemies that temporarily disappear or unload now have a 30-second recovery window; the encounter attempts to recover the tracked mob by UUID before failing.
- Obstructed origin spawning areas now receive a 30-second recovery window before an encounter fails.
- Forced-elite manual encounters schedule the elite without the normal elite delay.
- Manual triggers correctly look up namespaced profile paths (for example, `incursions:profiles/nether`).

### Preserved behavior
- The shared 18-minute cooldown, default trigger scan interval of 100 ticks (5 seconds), and default wave intervals are unchanged.
- Encounter pools, optional dimension integrations, scaling, and victory rewards are unchanged.
- Manual stops are non-victory endings and do not grant victory rewards.
- Manual triggers still require an eligible active portal or anchor and respect server rules.

Version 1.0.3 source was built and tested successfully by GitHub Actions (run [41935711269](https://github.com/adamsilfven/incursions/actions/runs/41935711269)). This is automated build/test verification, not a claim of a new hands-on multiplayer test for this publication.

## 1.0.1

- Promotes 1.0.1-rc.3 to the release version.
- Retains the pause-menu enable/disable control and opening chat announcements.
- Retains the candidate's encounter behavior, dimension integrations, rewards, and gameplay assets.
- Supplies the approved default configuration and a separate reset helper for existing test installations.

The compiled gameplay classes and resources are unchanged from 1.0.1-rc.3. Release preparation checked archive integrity, metadata, configuration defaults, JSON syntax, and byte preservation. No new live client, dedicated-server, or multiplayer session was run for this promotion.
