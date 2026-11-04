# Changelog

All notable changes are listed here. Versions follow [Semantic Versioning](https://semver.org/).

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
