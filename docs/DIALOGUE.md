# Writing Herobrine's dialogue

Everything Herobrine can say lives in two data files:

- `src/main/resources/data/herobrine/dialogue/dialogue.json`: the lines.
- `src/main/resources/data/herobrine/dialogue/triggers.json`: when he speaks.

They are ordinary data-pack files, reloaded with `/reload`. A data pack can ship its own
`data/herobrine/dialogue/dialogue.json` to **add** lines (or override lines with the same `id`), or
replace everything with `"replace": true`, the same way tags work. Mistakes never stop the game: a
broken entry is skipped and the server log names the file and line. `./gradlew test` also checks the
shipped files and fails on any unknown tag or counter.

For the recording side, see [VOICE_SCRIPT.md](VOICE_SCRIPT.md).

## How a line gets chosen

1. Something happens: a **trigger** (he was noticed, the player died, they picked up an iron sword...).
2. The trigger's `chance` and `cooldown` are rolled once, right then. Most of the time he stays silent.
3. If he wants to speak, the line waits for a quiet moment (`patience` seconds at most): no other line
   in the last `voiceMinGapSeconds` (config, 90 s by default), no fight going on.
4. The trigger's categories are tried **in order**. Each category has its own chance and cooldown, so a
   rare category first in the list stays rare.
5. Inside a category, a line can only be picked if its conditions fit, it is off its own cooldown, it was
   not `once` and already said, and it is not among the last 8 lines he said. Of those, only the highest
   `priority` lines stay, and one is drawn by `weight`. If nothing is left, he says nothing.

All times are game time, saved with the world, so reloading a world never makes him repeat himself.

## Lines (`dialogue.json`)

```json
{
  "id": "progression.iron",
  "text": "Iron. You're becoming a little more confident, aren't you?",
  "note": "Knowing smile. Teasing the confidence.",
  "priority": 0, "weight": 10, "chance": 1.0, "cooldown": 0, "once": false,
  "when": {"tags": ["tier:iron"], "not_tags": [], "memory": {"deaths": "..3"}}
}
```

| Field | Default | Meaning |
|---|---|---|
| `id` | required | Unique, lowercase. Also the caption's lang key: `herobrine.voice.<id>` |
| `category` | the part of `id` before the first `.` | Which set of lines it belongs to, and its audio folder |
| `audio` | `herobrine:voice/<category>/<rest of id>` | The recording, inside `assets/<namespace>/sounds/`, without `.ogg` |
| `text` | required | Exactly what he says. Shown as the caption, and instead of the voice if the file is missing |
| `note` | `""` | Delivery note for the recording. Not used in game |
| `priority` | `0` | Among the lines that fit, only the highest priority ones are drawn from |
| `weight` | `10` | Relative chance among lines of the same priority. `0` disables the line |
| `chance` | `1.0` | Chance the line is considered at all when it fits |
| `cooldown` | `0` | Seconds before this line can be said again |
| `once` | `false` | Said at most once per player, ever |
| `when.tags` | none | Context tags that must all be present |
| `when.not_tags` | none | Context tags that must all be absent |
| `when.memory` | none | Ranges for memory counters: `"5"`, `"5..9"`, `"5.."`, `"..9"` |

Captions can be translated without touching this file: add `herobrine.voice.<id>` to a lang file.

## Triggers (`triggers.json`)

| Trigger | When the mod fires it | Tags it adds |
|---|---|---|
| `encounter` | The player notices him | `mode:*`, `place:*`, `first_meeting` or `long_absence` |
| `reaction` | An appearance ends | `outcome:approached`, `outcome:stared`, `outcome:looked_away`, `outcome:ignored`, `mode:*` |
| `idle` | Every 20 seconds, as a tiny chance | |
| `progression` | A tool, weapon or armour piece of a new best tier is held or worn | `tier:*` |
| `boss` | A Wither is built, a Warden emerges, the player enters an End with a dragon, hits or is hit by one, or kills one | `boss:*`, `phase:arrive`, `phase:fight`, `phase:killed` |
| `weather` | The player is out in rain or a thunderstorm, once per spell | `weather:rain`, `weather:thunder` |
| `cave` | A minute underground, once per trip | `depth:shallow`, `depth:deep` |
| `building` | 40 blocks placed close together within 4 minutes | `near:home` |
| `death` | After respawning | `cause:<damage type>`, `killer:<entity>`, `in_boss_fight` |
| `milestone` | A milestone is reached for the first time | `milestone:*` |

Each trigger takes `categories` (tried in order), `chance`, `cooldown` (seconds), `delay`
(`[min, max]` seconds before he speaks), `patience` (seconds it may wait for a quiet moment),
`appear` (chance he also shows himself nearby, if he is due) and `in_combat` (may speak mid-fight).
The `categories` section sets `chance` and `cooldown` for whole categories.

## Context tags

Besides the trigger's own tags, every line is chosen with these:

| Tag | True when |
|---|---|
| `dimension:overworld`, `dimension:the_nether`, `dimension:the_end` | The player's dimension |
| `time:day`, `time:night` | Overworld time |
| `weather:clear`, `weather:rain`, `weather:thunder` | Overworld weather |
| `outdoors`, `underground`, `indoors` | Sky above them; or no sky and below sea level; or neither |
| `alone` | No other player within 64 blocks |
| `health:low` | At or below 30% health |
| `creative` | In creative mode |
| `near:home`, `near:village`, `near:berserk` | Within 48 blocks of their bed; in a village; in the memorial |
| `visible`, `mode:observer`, `mode:eyes`, `mode:sighting` (far away), `mode:stalking` (behind them) | He is standing somewhere right now |
| `place:tree`, `place:river`, `place:ridge`, `place:ruin`, `place:village`, `place:home`, `place:cave`, `place:open`, `place:dark`, `place:distant`, `place:behind` | Where he chose to appear |
| `ignored` | The player missed his last two appearances or more |
| `haunt:signs`, `haunt:sightings`, `haunt:stalking`, `haunt:confrontation` | The classic haunt phase |

Tier tags: `tier:wooden`, `tier:stone`, `tier:copper`, `tier:iron`, `tier:diamond`, `tier:netherite`.
Boss tags: `boss:wither`, `boss:ender_dragon`, `boss:warden`. Milestone tags: `milestone:first_house`,
`milestone:first_night`, `milestone:first_mining`, `milestone:first_nether_portal`,
`milestone:first_enchantment`, `milestone:first_village`, `milestone:first_diamonds`,
`milestone:first_elytra`, `milestone:first_dragon`, `milestone:found_berserk`.

## Memory counters

What he remembers, per player, saved with the player. `/herobrine memory <player>` shows them all.

| Counter | Meaning |
|---|---|
| `encounters` | Times the player saw him |
| `appearances` | Times he appeared, noticed or not |
| `ignored`, `ignored_streak` | Appearances they missed, in total and in a row |
| `approached`, `stared`, `looked_away` | How appearances ended |
| `deaths` | Times they died |
| `lines` | Lines he has said to them |
| `boss_encounters`, `boss.wither`, `boss.ender_dragon`, `boss.warden` | Boss encounters he noticed |
| `kill.wither`, `kill.ender_dragon`, `kill.warden` | Bosses they killed |
| `gear_tier` | Best equipment tier he has seen (0 none, 1 wooden, 2 stone, 3 copper, 4 iron, 5 diamond, 6 netherite) |
| `nights` | Nights survived start to finish |
| `underground_minutes` | Time spent underground |
| `days_known` | In-game days since they first saw him (worked out when needed) |
| `minutes_since_encounter` | Minutes since they last saw him (worked out when needed) |
| `milestones` | Milestones reached |
| `confrontations`, `mirror_wins`, `mirror_losses` | Mirror fights, from the encounter history |
| `haunt` | Haunt level, 0 to 100 |

The memory also keeps the time of every line, category and trigger, his last appearance and where,
the player's home (the last bed they slept in), and a short diary of important events (milestones,
boss encounters and kills, new gear tiers).
