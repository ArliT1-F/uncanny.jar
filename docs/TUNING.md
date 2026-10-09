# Tuning

Everything here is in `config/uncanny.json`, which is written to your instance's
`config` folder the first time the mod starts. Any key you delete keeps its default,
so a partial file is fine.

Values shown are the defaults.

---

## Master

| Key | Default | What it does |
|---|---|---|
| `enabled` | `true` | Turns the whole mod off. Dimensions still exist; nothing happens in them. |
| `debugLogging` | `false` | Logs the director's full decision funnel (Player / Playtime / Stage / instability / Candidates / Eligible / Selected / Executed plus why each event was rejected) at the check interval, and every generation decision. Extremely chatty, very useful. |
| `devForceCommands` | `false` | Development only. Unlocks `/uncanny force` and `/uncanny instability` (op level 2). Leave it off in normal play: with the flag off, nothing can shortcut the pacing system. |

## Pacing

| Key | Default | What it does |
|---|---|---|
| `gracePeriodMinutes` | `12` | Nothing of any kind happens before this. Enforced once, centrally, by the director. |
| `eventCheckIntervalTicks` | `40` | How often the director looks for something to do. 20 ticks = 1 second. |
| `anomalyCooldownTicks` | `3600` | Shortest gap between two quiet anomalies for one player (3 minutes). |
| `majorCooldownTicks` | `30000` | Shortest gap between major anomalies (25 minutes). |
| `nearMissCooldownTicks` | `12000` | Shortest gap between near misses (10 minutes). Near misses have their own, longer gap so aborted attempts can never crowd out real anomalies. |
| `anomalyFrequency` | `1.0` | Multiplies every probability. Also multiplies the chance of extra corridor links, so a busy world is also a more connected one. |
| `silenceChance` | `0.04` | Chance per ambience check that the world goes quiet instead of making a sound. |

## Reality instability

The hidden 0.0-1.0 the world's wrongness scales with. Never shown, never a
meter, never a jump scare: it decides which anomaly families are unlocked and
how often anything may fire (quiet anomalies ~0.9x at 0.0 rising to ~1.1x at
1.0; echoes 0.5x-1.5x; identity/ledger 0.7x-1.3x).

| Key | Default | What it does |
|---|---|---|
| `realityInstabilityRate` | `1.0` | Multiplies every instability gain (lore +0.004, anchors +0.008, anomalies +0.003..0.02, seals +0.015, Ledger advances +0.006, revisits +0.004, layer entry +0.012). `0.5` makes the world forget twice as slowly; `0.0` freezes it for testing. |

Instability only rises: there is no purchase, no decay, and no punishment for
playing your way. A cautious player simply reaches the loud families later, if
at all.

## Lore

| Key | Default | What it does |
|---|---|---|
| `startingStage` | `1` | Where the story starts. Raising it skips the slow burn and will feel wrong. |
| `discoveriesPerStage` | `6` | How many things you must read or notice to advance one stage. |
| `loreContainerChance` | `0.05` | Chance a generated container holds a document at all. |

## Generation

| Key | Default | What it does |
|---|---|---|
| `roomWeights` | see below | Weight per template id. Set one to `0` to remove that room from generation. |
| `anomalyRoomChance` | `0.01` | Second gate on the rarest rooms, on top of their rarity roll. |
| `impossibleRoomChance` | `0.02` | Same, for rooms that should not exist. |
| `landmarkChance` | `0.035` | How often a 4x4 chunk cell becomes a landmark: anchor rooms, the ring chamber, the Ledger hall. |

A template not listed in `roomWeights` falls back to the weight in its builder, so
you only need to list the ones you want to change.

## Transitions

| Key | Default | What it does |
|---|---|---|
| `allowSleepTransitions` | `true` | Sleeping can put you somewhere else. |
| `allowDoorTransitions` | `true` | Registered doorways work. Turn this off and the Archive can only be reached by command. |
| `allowWaterTransitions` | `true` | Deep water, held for five seconds. |
| `allowDeepFallTransitions` | `true` | Falling further than the Overworld should allow. |

## Multiplayer

| Key | Default | What it does |
|---|---|---|
| `perPlayerAnomalies` | `true` | Each player gets their own anomalies. `false` makes them world-wide. |
| `playerAnomalySpacing` | `64` | Reserved for spacing anomalies between players. Not currently enforced; anomalies are per-player instead. |

## Audio

| Key | Default | What it does |
|---|---|---|
| `audioEnabled` | `true` | Master switch for mod sounds. |
| `audioVolume` | `0.8` | Multiplies every sound the mod plays. |

---

## Recipes

**A calmer mod** - for a pack where the player should almost forget it is installed:

```json
"gracePeriodMinutes": 30,
"anomalyCooldownTicks": 12000,
"majorCooldownTicks": 72000,
"anomalyFrequency": 0.4
```

**A faster story** - for a map or a let's play that cannot wait:

```json
"gracePeriodMinutes": 2,
"discoveriesPerStage": 3,
"landmarkChance": 0.09,
"loreContainerChance": 0.15
```

**No anomalies in the Overworld at all.** The Overworld's anomalies are the tree,
door, torch, sign, chest, sky, grave and house events. Remove them from
`events/impl/AllEvents.java` - six lines - and the mod becomes a dimension mod that
leaves your world completely alone.

**A denser Hall.** More junctions, fewer dead ends:

```json
"anomalyFrequency": 1.6
```

Extra corridor links scale with this value, so the network gains loops and junctions
as a side effect. That is intentional: a busier mod is also a more connected one.

**No landmarks.** Anchor rooms, the ring chamber and the Ledger hall all disappear,
which soft-locks the ending. Do not set this to `0` unless you are also changing the
story.

---

## What is deliberately not configurable

- The number of seals. There are seven.
- The Ledger's status lines. They are the point of the mod.
- The anchor journal's shape. DAY 12, DAY 311, DAY 2,104.
- The eye arrangement seed. It is per world, so two worlds never match.

Changing these is a code change on purpose, because they are the parts that carry the
meaning.
