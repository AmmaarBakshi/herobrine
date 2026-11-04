# Herobrine: The Mirror

A Fabric mod for Minecraft **26.3** in which Herobrine haunts you, quietly studies
*how you play*, then shows up as a doppelgänger boss that fights exactly like you,
and gets better at countering you every time he comes back.

> **Status:** early development. See [Roadmap](#roadmap) for what works today.

## The experience

| Act | Name | What you feel |
|---|---|---|
| I | **Signs** | Something is off in your world: missing torches, strange tunnels, sounds. |
| II | **Sightings** | You keep seeing *someone* at the edge of view. Look at him and he's gone. |
| III | **The Mirror** | He fights like you: your weapon, your strafe, your healing habits. |
| ↺ | **Return** | Win or lose, he comes back, having learned to counter you. |

## Requirements

- Minecraft Java **26.3**
- Fabric Loader **0.20.1+**
- Fabric API **0.165.2+26.3**
- Java **25**

Install it on the **server and every client**; the mod adds its own entity and screens.

## Commands

All commands need op level 2.

| Command | Does | Available |
|---|---|---|
| `/herobrine status [player]` | Haunt level, phase, profile confidence, encounter count | ✅ (placeholder values) |
| `/herobrine reload` | Reload `config/herobrine.json` | ✅ |

More debug commands (sightings, signs, profiles, undo) arrive with their milestones.

## Configuration

`config/herobrine.json` is created on first launch. Out-of-range values are clamped,
and a malformed file is ignored (your edits are never overwritten). Apply changes with
`/herobrine reload`.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `hauntSpeedMultiplier` | `1.0` | Scales haunt-level gain (raise it for testing) |
| `griefing` | `NATURAL_ONLY` | `NONE` / `TORCHES_ONLY` / `NATURAL_ONLY` |
| `protectedRadius` | `16` | No world edits within this many blocks of beds, chests or block entities |
| `jumpscares` | `true` | Allow jumpscares |
| `maxJumpscaresPerDay` | `1` | Per in-game day |
| `fakeChatMessages` | `true` | Rare fake "joined the game" line |
| `minTraitSamples` | `20` | Samples before a combat habit counts as "known" |
| `requiredConfidence` | `0.6` | Profile confidence needed for a natural confrontation |
| `bossHealth` | `40` | Herobrine's boss-fight health |
| `healItemCap` | `3` | Max heals per fight |
| `adaptiveCounters` | `true` | Let him counter your habits in later fights |
| `debugOverlay` | `false` | Client HUD with live trait values |
| `sightingsThreshold` | `25` | Haunt level where Sightings begin |
| `stalkingThreshold` | `60` | Haunt level where Stalking begins |
| `confrontationThreshold` | `90` | Haunt level where a Confrontation becomes possible |

## Building from source

```sh
git clone https://github.com/AmmaarBakshi/herobrine.git
cd herobrine
./gradlew build            # jar in build/libs/, runs unit tests
./gradlew runClient        # dev client
./gradlew runServer        # dev dedicated server (run/server)
./gradlew runClientGameTest  # boots the real game and runs in-game tests
```

You need a JDK 25. If none is installed, Gradle downloads one automatically
(toolchain resolver).

### Reproducible builds

- Every dependency and plugin version is pinned in `gradle.properties` / `settings.gradle`.
- The Gradle wrapper verifies the distribution's SHA-256 checksum.
- Jars are built without file timestamps and with a stable entry order, so the same
  commit produces a byte-identical jar on any machine.
- Line endings are normalised via `.gitattributes`.
- Generated assets (icon, textures) come from deterministic scripts in `tools/`.

## Roadmap

- [x] **M1** Project setup, registries, `/herobrine status`
- [ ] **M2** Herobrine entity, original skin, glowing eyes, spawn egg
- [ ] **M3** Sightings and look detection
- [ ] **M4** Haunt director, sign events, world-edit safety and undo
- [ ] **M5** Combat behaviour recorder, player profile, persistence
- [ ] **M6** Mirror AI driven by the profile
- [ ] **M7** Confrontation, boss fight, Mirror Shard, advancement
- [ ] **M8** Adaptive counters and taunts
- [ ] **M9** Sounds, dedicated-server testing, performance pass

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Code and original assets: [MIT](LICENSE). Third-party assets, if any, are listed in
[CREDITS.md](CREDITS.md).
