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

`UncannyEventManager.tick` runs every 40 ticks:

```
for each player:
    context = EventContext(server, world, player, worldState, playerData, tick, seed)
    candidates = [e for e in EVENTS if e.canRun(context, random)]
    pick one, weighted by severity
    run it, book the cooldown
```

`UncannyEvent.canRun` checks, in order of cost: lore stage, one-time flag, cooldown,
then conditions (the only part that touches the world), then probability.

Severity is the pacing mechanism:

| Severity | Weight | Cooldown | Example |
|---|---|---|---|
| `QUIET` | 0.5 | 3 min | a tree is gone |
| `NOTICED` | 1.0 | 3 min | a sign nobody wrote |
| `MAJOR` | 4.0 | 25 min, once each | the wrong grave |

At most one anomaly per player per check. A player who has seen a lot gets
progressively fewer, which is what stops the mod from becoming noise.

### Conditions and actions

`EventCondition` and `EventAction` are functional interfaces with static factories,
so an event reads close to the design document's own notation:

```java
when(EventCondition.and(
        EventCondition.overworld(),
        EventCondition.playedMinutes(30),
        EventCondition.inForest(),
        EventCondition.night()));
```

Every condition that searches does so inside a small bounded cube. Nothing in the mod
scans a large area, and nothing scans every tick.

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
```

The home fingerprint is bounded on purpose: 24 block types, 8 containers, 6 death
positions. It is filled from placement and break callbacks, not from scanning.

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

## Threading and cost

Everything runs on the server thread. There are no worker threads, no scheduled
executors, no async block access.

Budget per tick, worst case:

- 2 chunks filled (a few thousand block writes, the same order as vanilla
  decoration)
- one event condition pass per player, at most every 2 seconds
- one seal-decay check per minute
- one star-brightening check per minute
- one ring-chamber check per 2 seconds

Persistent data is written when the world saves. The config is read once and cached.
