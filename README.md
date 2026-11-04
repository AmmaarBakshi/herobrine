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

| Command | Does |
|---|---|
| `/herobrine status [player]` | Haunt level, phase, profile confidence, encounters, sightings |
| `/herobrine haunt set <player> <0-100>` | Set a player's haunt level |
| `/herobrine sighting <player> [stalk\|jumpscare]` | Force a sighting |
| `/herobrine sign <event> [player]` | Force a haunt event (tab-completes the ids below) |
| `/herobrine undo [n]` | Revert the last `n` world-editing events (default 1) |
| `/herobrine profile <player>` | Every learned trait with its sample count (grey = not known yet) |
| `/herobrine profile <player> reset` | Wipe the profile |
| `/herobrine profile <player> preset <name>` | Load a test profile: `bow_kiter`, `sword_spammer`, `shield_turtle`, `pillar_coward` |
| `/herobrine perf [reset]` | Measured recorder cost per player per tick |
| `/herobrine reload` | Reload `config/herobrine.json` |

Confrontation commands arrive with their milestone.

### What he learns

While you fight hostile mobs (you hit or were hit in the last 5 s, or one within 8 blocks is
after you), the server records your habits as moving averages: weapon choice, attack timing,
crits, sprint-hits, how much you shoot, strafe direction and intensity, the distance you keep,
jumping, shield use and reaction time, when and with what you heal, when you back off, and
whether you pillar, pearl away or water-clutch. A habit counts once it has `minTraitSamples`
samples. The profile is saved with the player and survives relogs, deaths and restarts.

Set `debugOverlay` to `true` to see the values live in a corner of the screen.

Measured cost: about **15 µs per player per tick while fighting** (budget: 100 µs), and less
out of combat. Check your own server with `/herobrine perf`.

### Haunt events

| Id | What happens | Needs `griefing` |
|---|---|---|
| `torch_thief` | Up to 3 torches vanish from a cave you were in | `TORCHES_ONLY` |
| `tunnel` | A perfectly straight 2x2 tunnel near where you mine | `NATURAL_ONLY` |
| `sand_pyramid` | A small sand pyramid in a desert or shallow ocean, 40-100 blocks away | `NATURAL_ONLY` |
| `leafless_tree` | One natural tree loses every leaf | `NATURAL_ONLY` |
| `sign` | A sign in your path; some messages quote your own habits | `NATURAL_ONLY` |
| `ambient_sound` | Footsteps behind you, a door, mining inside the walls (only you hear them) | none |
| `ghost_replay` | He appears ~30 blocks away and re-enacts your last 10 seconds | none |
| `fake_chat` | "Herobrine joined the game", at most once a day | none |

World edits never happen in anyone's view, never within `protectedRadius` of a bed or
any block entity (chests, furnaces, signs...), only touch natural blocks, and are all
logged so `/herobrine undo` can put them back (the log survives restarts).

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
./gradlew runClientGameTest -Pgametest=Haunt  # just one (Command, Entity, Sighting, Haunt, Recorder)
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
- [x] **M2** Herobrine entity, original skin, glowing eyes, spawn egg
- [x] **M3** Sightings and look detection
- [x] **M4** Haunt director, sign events, world-edit safety and undo
- [x] **M5** Combat behaviour recorder, player profile, persistence
- [ ] **M6** Mirror AI driven by the profile
- [ ] **M7** Confrontation, boss fight, Mirror Shard, advancement
- [ ] **M8** Adaptive counters and taunts
- [ ] **M9** Sounds, dedicated-server testing, performance pass

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Code and original assets: [MIT](LICENSE). Third-party assets, if any, are listed in
[CREDITS.md](CREDITS.md).
