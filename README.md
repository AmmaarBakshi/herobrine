# Herobrine: The Mirror

A Fabric mod for Minecraft **26.3** in which Herobrine lives in your world. He watches your
whole journey, remembers it, and now and then says something about it, calmly, in a voice of
his own. He also quietly studies *how you fight*, then shows up as a doppelgänger boss that
fights exactly like you, and gets better at countering you every time he comes back.

> **Status:** early development. See [Roadmap](#roadmap) for what works today.

## The experience

| Act | Name | What you feel |
|---|---|---|
| ∞ | **The Observer** | From the first day, someone is watching. Rarely, he is there. Sometimes he talks. |
| I | **Signs** | Something is off in your world: missing torches, strange tunnels, sounds. |
| II | **Sightings** | You keep seeing *someone* at the edge of view. Look at him and he's gone. |
| III | **The Mirror** | He fights like you: your weapon, your strafe, your healing habits. |
| ↺ | **Return** | Win or lose, he comes back, having learned to counter you. |

## The Living Observer

Herobrine is not an event that happens to you. He is a character who experiences your world
alongside you. He is not a mob and he doesn't chase you: most of the time he isn't there at all.

### How he appears

Rarely. Every appearance shares one cooldown (`appearanceCooldownMinutes`, 20 by default), and
after it a roll once a minute (`appearanceChancePerMinute`) decides, so the gaps are never regular
and are sometimes hours long. He is likelier when you are alone, at night, and near the BERSERK
memorial; never while you fight, and never during a boss fight unless `appearDuringBossFights` is on.

When he does appear, he picks a place with character: beside a tree trunk, on the far bank of a
river, on a ridge above you, in an old ruin, at the edge of a village, near your home, deep in a
dark cave. And one of four ways
(`appearanceWeights`):

| Way | What happens |
|---|---|
| `observer` | He stands in plain view and watches. Look at him and he lets you, for a while. Look away and he is gone. Walk up to him and he thins out into smoke. Sometimes he fades in at the edge of your view. |
| `distant` | Far away at the edge of your view. Look straight at him and he vanishes. |
| `behind` | Close behind you. One footstep. He is gone the moment you turn around. |
| `eyes` | In the dark, only his two glowing eyes. Approach, stare too long, or light a torch, and they go out. |

How it ends is remembered: whether you stared him down, walked up to him, looked away, or never
noticed him at all (he takes that personally).

### What he notices

| Moment | How it is noticed |
|---|---|
| Your gear: wooden, stone, copper, iron, diamond, netherite | The first time you hold or wear a better tier |
| The Wither, the Ender Dragon, the Warden | When one is built, emerges or found, when you fight it, when it dies. He never interferes. |
| Rain and thunderstorms | You out in them, once per spell |
| Caves | A minute underground |
| Building | 40 blocks placed close together |
| Your deaths | Their cause, and how many there have been |
| Milestones | First house (a bed under a roof), first night survived, first mining trip, first Nether portal, first enchantment, first village, first diamonds, first Elytra, first dragon, finding the memorial |

He compares everything with what you already had when he started watching, so installing the mod
in an old world doesn't make him comment on every diamond you found last year.

### His voice

His lines are recordings, played straight from `.ogg` files: drop a file into
`assets/herobrine/sounds/voice/<category>/` (in the mod or a resource pack) and it plays; nothing
needs registering. Until a line is recorded, its words appear as a caption instead, so a missing
file never loses a line. With vanilla subtitles on, recorded lines are captioned too.

He speaks rarely: each moment has a chance and a cooldown, at least `voiceMinGapSeconds` pass
between two lines, he never repeats one of his last eight lines, and he would rather say nothing
than repeat himself. The fourth-wall lines, where he talks about his developer, are the rarest of
all (and can be switched off with `fourthWall`).

- [docs/VOICE_SCRIPT.md](docs/VOICE_SCRIPT.md): the recording script. Every line with its file
  name, delivery note and when it plays, and which are recorded.
- [docs/DIALOGUE.md](docs/DIALOGUE.md): how to write lines and change when he speaks. The lines
  are data (`data/herobrine/dialogue/`), so data packs can add to them or replace them.

### What he remembers

Per player, saved with the player: deaths, sightings and how each ended, bosses met and killed,
your best gear, milestones, where he last stood, where you sleep, a short diary of important
events, and every line he has said and when. Lines depend on it: the first time you meet him he
says one thing, the fifth time another, the twentieth something else. `/herobrine memory <player>`
shows all of it.

### The BERSERK memorial

A small ruin of old stone that generates on its own in plains, forests, taigas, meadows and
savannas. It explains nothing. There are inscriptions, a broken shrine, an altar with a riddle,
and something under the floor for those who dig. He is far likelier to appear near it.
`/locate structure herobrine:berserk` finds the nearest one.

### What it costs

Nothing scans the world. He reacts to events, looks around once every 5 seconds per player
(a handful of lookups), and a little more every 20 seconds. Measured on a slow test VM: about
**16 µs per server tick with one player**, worst tick 0.2 ms. `/herobrine perf` shows the
observer on its own line, so you can check your own server.

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
| `/herobrine perf [reset]` | Measured cost of the recorder, the world systems and the boss AI |
| `/herobrine reload` | Reload `config/herobrine.json` |
| `/herobrine memory <player> [reset]` | Everything he remembers about the player, or forget it all |
| `/herobrine appear <player> [observer\|distant\|behind\|eyes]` | Make him appear now (skips the cooldown) |
| `/herobrine voice say <player> <line>` | Play one line (tab-completes every id), e.g. to check a recording |
| `/herobrine voice trigger <player> <trigger> [tags]` | Let him pick a line for a moment, ignoring chance and cooldowns |
| `/herobrine voice recordings` | How many lines have a recording, per category |


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
| `observer` | `true` | The Living Observer: his own appearances, what he notices, his voice |
| `voice` | `true` | His voice lines (captions when a recording is missing) |
| `voiceMinGapSeconds` | `90` | Least time between any two of his lines |
| `fourthWall` | `true` | Allow the (already extremely rare) lines about being a programmed character |
| `appearanceCooldownMinutes` | `20` | Least time between two appearances of any kind |
| `appearanceChancePerMinute` | `0.05` | Once off cooldown, chance each minute that he appears |
| `appearDuringBossFights` | `false` | Let him watch boss fights from a distance |
| `appearanceWeights` | `observer 5, distant 3, behind 1, eyes 3` | How likely each way of appearing is; 0 switches one off |

## Building from source

```sh
git clone https://github.com/AmmaarBakshi/herobrine.git
cd herobrine
./gradlew build            # jar in build/libs/, runs unit tests
./gradlew runClient        # dev client
./gradlew runServer        # dev dedicated server (run/server)
./gradlew runClientGameTest  # boots the real game and runs in-game tests
./gradlew runClientGameTest -Pgametest=Haunt  # just one (Command, Entity, Sighting, Haunt, Recorder, Observer...)
```

You need a JDK 25. If none is installed, Gradle downloads one automatically
(toolchain resolver).

### Reproducible builds

- Every dependency and plugin version is pinned in `gradle.properties` / `settings.gradle`.
- The Gradle wrapper verifies the distribution's SHA-256 checksum.
- Jars are built without file timestamps and with a stable entry order, so the same
  commit produces a byte-identical jar on any machine.
- Line endings are normalised via `.gitattributes`.
- Generated assets (icon, textures, sounds, the memorial's template) come from deterministic
  scripts in `tools/`. `tools/gen_voice_script.py` rebuilds docs/VOICE_SCRIPT.md from the dialogue.

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
- [ ] **M10** The Living Observer
  - [x] Rare appearances with configurable chance and cooldown, places with character
  - [x] Observer and eyes-only modes, fading in and out, leaving when approached or looked away from
  - [x] Data-driven dialogue, recorded voice lines with caption fallback, no repetition
  - [x] Comments on weather, caves, building, deaths, gear, bosses and milestones
  - [x] Persistent memory that the lines depend on
  - [x] Fourth-wall lines, extremely rare
  - [x] The BERSERK memorial
  - [ ] The developer's recordings (see [docs/VOICE_SCRIPT.md](docs/VOICE_SCRIPT.md))
  - [ ] Balancing from real play

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Code and original assets: [MIT](LICENSE). Third-party assets, if any, are listed in
[CREDITS.md](CREDITS.md).
