# Changelog

All notable changes are listed here. Versions follow [Semantic Versioning](https://semver.org/).

## Unreleased

### The Living Observer
- Herobrine as a character who shares your world: rare appearances, with configurable chance
  and cooldown, in places with character such as ruins and the edges of villages. He watches,
  shows only his eyes in the dark, fades in and out, and leaves when approached or looked away
  from.
- His voice: data-driven lines (`data/herobrine/dialogue/`, see `docs/DIALOGUE.md`) about
  weather, caves, building, deaths, gear, bosses and milestones, chosen by what he remembers
  and never repeated too soon, plus extremely rare fourth-wall lines (`fourthWall` switches
  them off).
- Lines play the developer's own recordings and show as captions until recorded; the
  recording script is `docs/VOICE_SCRIPT.md`.
- Persistent per-player memory of encounters, deaths, bosses, gear, milestones and what he
  has said.
- The BERSERK memorial, a rare structure with books to find.
- `/herobrine memory`, `/herobrine appear` and `/herobrine voice`; `/herobrine perf` reports
  the observer on its own line.

## 1.0.0

The first complete version, for Minecraft 26.3 with Fabric API 0.165.2+26.3.

### Haunting
- Per-player haunt level (0-100) with phases: Signs, Sightings, Stalking, Confrontation.
- Sign events: torch thief, straight tunnels, sand pyramids, leafless trees, signs that quote
  your habits, private ambient sounds, a ghost replay of your last 10 seconds, fake chat.
- World edits never happen in view or near beds and block entities, only touch natural
  blocks, and can be undone with `/herobrine undo` (the log survives restarts).
- Sightings at the edge of view that vanish when looked at, stalking, rare jumpscares.

### The Mirror
- A behaviour recorder that learns how you fight (weapon, timing, crits, strafing, range,
  shield, healing, retreating, pillaring, pearls) at about 15 µs per player per tick.
- A boss that fights with your habits, your kind of gear and your escapes.
- The confrontation: natural or shrine trigger, arrival sequence, boss bar, win (Mirror Shard,
  *Know Thyself*) or loss (the aftermath), encounter history.
- Adaptive counters for your two most-used habits in later fights, and taunts built from
  your real stats.

### Polish
- Original synthesized sounds, including the arrival swell and the shatter.
- `/herobrine perf` reports the recorder, the haunt systems and the boss AI separately; one
  boss costs about 250 µs per tick on a dedicated server.
- In-game tests for every milestone, including one over a real dedicated server.
