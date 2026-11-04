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
| `/herobrine status [player]` | Haunt level, phase, profile confidence, encounters (won/lost), sightings |
| `/herobrine haunt set <player> <0-100>` | Set a player's haunt level |
| `/herobrine sighting <player> [stalk\|jumpscare]` | Force a sighting |
| `/herobrine sign <event> [player]` | Force a haunt event (tab-completes the ids below) |
| `/herobrine undo [n]` | Revert the last `n` world-editing events (default 1) |
| `/herobrine profile <player>` | Every learned trait with its sample count (grey = not known yet) |
| `/herobrine profile <player> reset` | Wipe the profile |
| `/herobrine profile <player> preset <name>` | Load a test profile: `bow_kiter`, `sword_spammer`, `shield_turtle`, `pillar_coward` |
| `/herobrine summon <player>` | Start the confrontation now (full arrival sequence) |
| `/herobrine perf [reset]` | Measured recorder cost per player per tick |
| `/herobrine reload` | Reload `config/herobrine.json` |


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

### How he fights

In a fight Herobrine plays back your habits. A small utility planner picks his tactic twice a
second:

- **Heal** when he drops to the HP at which you heal, using what you heal with, up to
  `healItemCap` times.
- **Retreat** at your retreat point, escaping the way you do: running, pillaring up with your
  favourite block, or pearling away.
- **Fight** otherwise, with your bow as often as you shoot.

Within a tactic he copies how you do things. He keeps your distance and strafes to your side
at your intensity. He swings at your average charge (a spam-clicker gets many weak hits),
jump-crits and sprint-hits as often as you do, draws the bow for as long as you do, and raises
the shield as often as you, after your reaction delay. He wears unenchanted copies of your
armour and weapon. Nothing he holds ever drops, his arrows can't be picked up, and any pillar
he builds is removed when the fight ends.

### The confrontation

He comes for you in either of two ways:

- **Naturally**: your haunt level is 90 or more, he knows you well enough (profile
  confidence at least `requiredConfidence`), it's night, and nobody is within 64 blocks.
  This happens at most once per in-game day.
- **By shrine**: build a 3x3 floor of gold blocks, put netherrack on the centre, a
  redstone torch on each of its four sides, and light the netherrack. This forces the fight
  with whatever he has learned so far.

The music stops, the world goes dark and lightning strikes, and he is standing about 10
blocks away holding your kind of weapon, with a **Herobrine** boss bar. The fight happens
where you are. If you run more than 64 blocks, he appears behind you.

- **You win**: he drops a **Mirror Shard** and you get the *Know Thyself* challenge.
  Right-click the shard to see *What he learned about you*: every habit he has recorded,
  as values and bars.
- **You lose**: he waits at your respawn point, says one line, and leaves. He never takes
  your items.

Either way the fight is recorded and he will be back. There is no confrontation on
Peaceful; signs and sightings still happen.

### How he adapts

After each fight he notes the two habits you leaned on most. Next time he brings a
counter for each, and he uses it more often every time he comes back: 15% of his
decisions after one fight, up to 60%. The rest of the time he still mirrors you. The first
time a counter works in a fight, he tells you.

| Your habit | His counter |
|---|---|
| Blocking with a shield | Brings an axe and swaps to it when you raise the shield, disabling it the vanilla way |
| Strafing mostly one way | Moves to where you are going instead of mirroring you |
| Pillaring up | Breaks the top of your pillar (only blocks you placed, only if `griefing` allows), else shoots |
| Kiting with a bow | Sprints straight at you, zig-zagging |
| Always healing at the same HP | Goes all in with jump crits when you're just above that point |
| Pearling away | Throws a pearl after you and lands next to you |

He also talks during the fight, every 15 to 30 seconds, quoting your real habits
("Right again.", "You always heal at 3 hearts."). Set `adaptiveCounters` to
`false` for a Herobrine who only ever mirrors you.

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
- [x] **M6** Mirror AI driven by the profile
- [x] **M7** Confrontation, boss fight, Mirror Shard, advancement
- [x] **M8** Adaptive counters and taunts
- [ ] **M9** Sounds, dedicated-server testing, performance pass

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Code and original assets: [MIT](LICENSE). Third-party assets, if any, are listed in
[CREDITS.md](CREDITS.md).
