# Architecture

Written for someone who is more comfortable in Python than in Java. There are no
clever patterns here: each package does one job, and the interesting logic is in
four files.

---

## The four files that matter

| File | What it decides |
|---|---|
| `generation/GenerationRules.java` | which chunks connect to which |
| `generation/RoomGenerator.java` | which room goes in a chunk |
| `events/UncannyEventManager.java` | what happens, when, to whom |
| `lore/LedgerManager.java` | what the book says about you |

Everything else supports those four.

---

## Generation

### The connection graph

`GenerationRules.linkMask(seed, chunkX, chunkZ)` returns a 4-bit mask saying which
of a chunk's four sides are open. It reads nothing from the world.

```
parent(chunk) = WEST or NORTH, chosen by hash    (null at the origin)
treeLink(C, D)  = parent(C) == D  or  parent(D) == C
extraLink(C, D) = hash(min(C,D), max(C,D)) < 0.11
mask(C)         = all directions where treeLink or extraLink
```

Three properties fall out of this:

1. **Connected.** Parent links always decrease `x` or `z`, so there are no cycles,
   and every chunk can walk to the origin. The layer is one structure.
2. **Symmetric.** An extra link is hashed from the canonical pair of chunks, so both
   chunks compute the same answer independently. They never have to coordinate, which
   is what makes it safe to generate two chunks at once.
3. **Dead ends for free.** A chunk with no children and no extra links has one
   opening. Nothing places dead ends; they are what a leaf looks like.

### Template selection

`RoomGenerator.chooseRoom` runs four filters in order:

1. Is this chunk inside a landmark cell? Landmarks are decided on a coarse 4x4
   grid, so two landmarks are never adjacent.
2. Which templates physically fit this connection pattern? A straight corridor
   cannot sit on a T junction, no matter how much the weights want it to.
3. Which of those pass their rarity roll *this time*?
4. Weighted pick from the survivors, using the weights in `config/uncanny.json`.

A `COMMON` template is always kept as a fallback, so a chunk can never fail to
resolve.

After the builder runs, `GenerationContext.ensureAccessible()` cuts a two-wide spoke
from the middle of the chunk to every open side. Most templates fill their interior
with wall and then carve a shape out of it, which makes it very easy to seal a
doorway without noticing. The post-pass makes that impossible; a template that
genuinely draws its own paths can opt out with `ensuresOwnAccess()`.

### Chunk-local building

A `GenerationContext` gives a builder local coordinates (0..15 across, y up from
floor level) and refuses to write outside them. Every corridor puts its doorway at
local 7-8 on the shared face, so neighbours line up without knowing about each
other.

This is why rooms are 16x16 and why the connector height is fixed per layer: the
constraint is what makes parallel generation safe.

### The queue

`ProceduralDimensionGenerator` does not build on the chunk-load event. It queues the
chunk and processes two per tick, dropping anything that unloads first. The queue is
capped at 512 entries.

---

## Layers

Each layer is one enum entry in `UncannyDimension` plus three JSON files
(dimension, dimension type, biome).

| Layer | Generation mode | What it is |
|---|---|---|
| Overworld | `NONE` | untouched, deliberately |
| Hall | `MODULE` | corridor network, 17 templates |
| Archive | `MODULE` | library, holds the Ledger |
| Partition | `PATCHWORK` | module templates borrowed from every layer |
| Deep | `CARVE` | solid rock, tunnels cut out of it |
| Below | `VOID` | mostly nothing, and one door |
| House | `ECHO` | built from the player's remembered home |
| Copy / Woods | `NATURE` | real terrain with wrongness written on top |

The modes are dispatched in one `switch` in `ProceduralDimensionGenerator`. Adding a
layer means adding a case.

### Transitions

There are no portals. `DimensionTransitionManager` watches for ordinary things:

- sleeping (the roll happens when you lie down, not when you wake)
- deep water, held for five seconds
- falling further than the Overworld should allow
- walking through a registered doorway

Each has its own probability, its own cooldown, and its own gate on progression.
Some are one-way. None of them are announced.

---

## The event director

`UncannyEventManager.tick` runs every server tick but does almost nothing: a
`Throttle` gates the real work to `eventCheckIntervalTicks` (default 40), so the
cadence in the config is the cadence you get. One check:

```
for each player:
    accrue playtime + behavioural memory (one map write)
    global grace period: before gracePeriodMinutes, stop here
    context = EventContext(..., tick, seed)
    for each event: evaluation = event.evaluate(context, random, explain)
        NONE        -> candidate (eligible and passed its roll)
        PROBABILITY -> eligible (failed only the roll)
        anything else -> rejected, with a reason
    pick one candidate, weighted:
        severity x layer identity x this player's behaviour
    chosen.run(context)   # books cooldowns, records the anomaly, raises instability
    if debugLogging: print the whole funnel, once per check
```

`evaluate()` checks in order of cost: stage, one-time flag, cooldown, chain
preconditions, instability bounds, progression gates, conditions (the only part
that touches the world), probability. Every rejection carries a detail string -
`cooldown (41s left)`, `reality too stable (v < 0.10)`,
`condition failed: not in forest` - so with `debugLogging` on, the log answers
"why didn't X happen?" directly. Conditions are labeled with
`EventCondition.named("reason", fn)`, which is what makes those strings human.

Severity is the pacing mechanism:

| Severity | Weight | Cooldown | Example |
|---|---|---|---|
| `QUIET` | 0.5 | 3 min | a tree is gone |
| `NOTICED` | 1.0 | 3 min | a sign nobody wrote |
| `MAJOR` | 4.0 | 25 min, once each | the wrong grave |

At most one anomaly per player per check. A player who has seen a lot gets
progressively fewer, which is what stops the mod from becoming noise.

### Families

Every event declares a `Family` that gates *how* anomalies may escalate:
`ENVIRONMENTAL` (the world slightly off), `ARCHITECTURAL` (structures moved),
`FALSE_NORMALITY` (almost correct: extra stair, wrong wood, one wrong block),
`AUDITORY` (sound and its absence), `ECHO` (the player's own actions coming
back), `NEAR_MISS` (aborted attempts), `DIMENSIONAL` (layer identity),
`MEMORY`, `LEDGER`, `IDENTITY` (reserved for high instability). Family filters
in `PlayerBehavior.advancedFamilies` decide which families a player's
instability and playtime have unlocked; a fresh player simply cannot roll an
identity event.

### Near misses and echoes

- **Near misses** (`family NEAR_MISS`) have their own cooldown
  own cooldown (`nearMissCooldownTicks`) and never raise instability or record
  a location: a sound that stops, a door that almost changes, a structure that
  flickers for six ticks. They are the mod practicing silence.
- **Reality Echoes** fire only against *this player's* recorded actions: the
  last 16 blocks they broke (restored when they return 8-48 blocks later, ≥10
  minutes later), their dominant building material, their doors. They need
  `instability ≥ 0.12-0.15` and a few dozen recorded actions, so they arrive
  late and feel earned.

### Anomaly chains

`AnomalyChain` is progression via persistent flags, not a quest: each stage
sets `the_tree:<stage>` in the player's memory, later stages declare
`.after(chain, stage)` preconditions, and each stage keeps its own conditions
and roll. A player who never saw `missing_tree` can never see `wrong_tree`, but
one who saw it may simply never get the next roll - "not every player sees every
stage" is the design, not a bug. Chain state is stored per player and is
visible only to the director.

### Dimension atmosphere

`DimensionAtmosphereEvent` registers one behavioural rule per layer (cracked
twins in the Hall, floating arrangements in the Woods, duplicated chests in the
Copy, a record written for you in the Archive, fragments of other layers at
boundaries). Each is: one bounded search when its gates pass, one to five block
writes, nothing scheduled, nothing scanned per tick.

### The scheduler

`util/Scheduler` is a `TreeMap<Long, Runnable>` ticked from the server loop for
timed writes (a door restored four ticks later, a sound played in a future
tick). It holds at most 128 tasks; everything else is immediate.

### Conditions and actions

`EventCondition` is a functional interface with static factories, so an event
reads close to the design document's own notation:

```java
when(EventCondition.and(
        EventCondition.overworld(),
        EventCondition.playedMinutes(30),
        EventCondition.inForest(),
        EventCondition.night()));
```

Every condition that searches does so inside a small bounded cube. Nothing in
the mod scans a large area, and nothing scans every tick.

---

## Player memory and instability

Two hidden systems feed the director. Neither is ever shown to the player:
there are no stat screens, no meters, no RPG vocabulary.

`player/PlayerMemory` is a bounded record of behaviour: dimension visits and
time, area revisit counts, door uses, structure finds, sleeps, underground
ratio, build/break totals, the last 16 broken blocks, and the anomaly
locations this player has caused. It accrues once per check from data the game
already produced (position, dimension, a block callback) - never from world
scans. It is persisted in the player's NBT with hard caps.

`player/RealityInstability` is a hidden 0.0-1.0 that rises slowly and persists:
lore reads (+0.004), entering an uncanny layer (+0.012), standing in an anchor
(+0.008), sealing (+0.015 per seal), Ledger advances (+0.006), anomalies
(+0.003..0.02 by severity, multiplied by `realityInstabilityRate`), revisiting
an old anomaly spot (+0.004). It gates:

- which families are unlocked at all (low = subtle only)
- how often events may fire (`instabilityFactor`)
- weighted preference for louder anomalies
- the identity/ledger registers above 0.5-0.6

It is never decremented by gameplay, never displayed, and never causes damage
or jumpscares: it decides what *kind* of wrongness the world is capable of
being.

`player/PlayerBehavior` turns the memory into two numbers per event: a
probability multiplier and a selection weight bias - so a player who builds a
lot hears knocking, a player who lives behind doors gets door anomalies more
often, and a player who never opens the Ledger keeps the book's late stages
locked without ever being punished for it.

---

## Persistence

`data/UncannyWorldState` is a `PersistentState` saved to
`<world>/data/uncanny_world.dat`.

```
world state                      player state (per uuid)
  seals[7]                         entry number
  opened seals                     play time, sleeps, deaths
  generated chunks (per layer)     first death, first sleep
  thresholds (doorways)            layers entered, in order
  subject uuid                     lore read, clues, anchors seen
  ledger position                  completed events
  ring chamber position            major anomaly flags
  world-shared lore                ledger state, anchor status
                                   marks (positions to find again)
                                   home fingerprint
                                   behaviour memory (bounded)
                                   hidden instability (0.0-1.0)
                                   chain stage flags
                                   recent broken blocks (16)
```

The home fingerprint is bounded on purpose: 24 block types, 8 containers, 6 death
positions. It is filled from placement and break callbacks, not from scanning.
Anomaly locations are capped at 128 globally, and every per-player collection
has an explicit limit.

`markDirty()` sets a flag; Minecraft writes the file when the world saves. Nothing
here serialises on a tick.

---

## Client

Three systems, all optional:

- `rendering/FogEffects` registers a `DimensionEffects` per layer, keyed by the
  `effects` field in the dimension type JSON. This is how a layer gets its own sky
  and fog colour without a mixin.
- `rendering/AnomalyEffects` holds decaying visual state driven by the `VISUAL`
  payload.
- `audio/AmbientManager` handles silence. It can stop music; suppressing vanilla
  cave ambience entirely would need a mixin, which this mod deliberately avoids.

The two payloads (`OPEN_BOOK`, `VISUAL`) are display-only. A client without the mod
loses atmosphere and nothing else.

---

## Startup order

The mod hooks three Fabric lifecycle events, and the order matters:

| Event | Safe to touch | Used for |
|---|---|---|
| `SERVER_STARTING` | nothing world-shaped | writing the config |
| `SERVER_STARTED` | everything | seeding the layers |
| `SERVER_STOPPING` | everything, for the last time | clearing per-world caches |

The overworld does not exist during `SERVER_STARTING`: it is created inside
`setupServer()`, which runs after that event has fired. `MinecraftServer.getOverworld()`
returns `null` there, so calling `.getSeed()` on it is a `NullPointerException` that
kills the server before the world ever opens - which is exactly what crashed
`DimensionManager.initialise()` on the first real run.

The rule now: anything that wants a world waits for `SERVER_STARTED`.
`DimensionManager` also defends itself - if `initialise()` is ever called early it
marks the seeds pending and finishes them on the first tick, and `seedOf()` returns
a stable placeholder instead of throwing. Startup bugs should be logged, not fatal.

---

## Threading and cost

Everything runs on the server thread. There are no worker threads, no scheduled
executors, no async block access.

Budget per tick, worst case:

- 2 chunks filled (a few thousand block writes, the same order as vanilla
  decoration)
- one event check per player, at most every `eventCheckIntervalTicks` (default
  2 seconds): bounded cube searches (≤ 11x6x11 reads) only for events whose
  cheap gates already passed
- one anchor-room probe every 4th check, in three layers only, capped at 9
  signs found
- one seal-decay check per minute
- one anomaly-location revisit compare per check (≤ 128 packed longs, no block
  reads)
- scheduler: at most 128 pending timed tasks

Persistent data is written when the world saves. The config is read once and cached.
