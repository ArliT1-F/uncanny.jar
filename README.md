# uncanny.jar

A Minecraft Fabric horror/ARG mod with **no monsters in it**.

There are no hostile mobs, no stalkers, no combat, no jumpscares on a timer. The
horror comes from a world that is quietly wrong: a tree that was there yesterday, a
door that closed itself, a book that contains people, and the slow realisation that
the player is being recorded.

The intended reaction is not *"what is going to attack me"* but
*"why is Minecraft doing that?"*

---

## Contents

- [What is in the mod](#what-is-in-the-mod)
- [Building](#building)
- [Installing](#installing)
- [Playing](#playing)
- [Configuration](#configuration)
- [The `/uncanny` command](#the-uncanny-command)
- [How it works](#how-it-works)
- [Extending it](#extending-it)
- [What has and has not been verified](#what-has-and-has-not-been-verified)
- [Project layout](#project-layout)

---

## What is in the mod

**Eight layers of the same reality.** The Overworld, plus seven procedural
dimensions: the Hall, the House, the Copy, the Woods, the Archive, the Deep, Below,
and the Partition. They are not portals to other places - they are states of one
place, and they overlap.

**Procedural generation, deterministic.** The Hall is built from 17 reusable room
modules; the Archive, the Partition, the Deep and the rest add 15 more. Layout is a
pure function of `world seed + dimension seed + chunk coordinates`, so a corridor
looks identical after a restart, a chunk reload, or on another player's machine, and
two different worlds produce two completely different Halls. No layout is stored on
disk; only the fact that a chunk has been built.

**An event director.** 17 anomalies, each with conditions, probability, cooldown,
severity and a lore-stage gate. A player can play for thirty minutes and see three
quiet things. Major events - the wrong grave, the other house, the room that
remembers - happen once each, ever.

**A lore system.** 41 documents loaded from JSON, progressing through nine stages
from *"boundary measurements remain stable"* to documents that use the player's own
username. Written in the voice of a bureaucracy: measurements, corrections,
crossed-out lines, and no adjectives.

**The Ledger.** A book in the Archive that will not open. Late in the game it opens
for one person, and its pages are generated from what that player actually did -
first sleep, first death, which layers they entered, in what order. The last page is
a status line, and the mod never explains it.

**Seven seals.** They do not hold something in. They keep realities from
recognising one another, and they only ever weaken when someone goes somewhere.

**Biblical and cosmic imagery, as architecture.** Four intersecting rings with too
many openings in them, which rotate when nobody is looking. A prison of stars at the
bottom of the Deep, where the lights are not stars. Nothing is confirmed. Nothing is
explained.

**Persistence.** Everything is saved with the world. Quit mid-anomaly, restart the
game, come back a week later: the story is where you left it.

---

## Building

Requirements: **Java 17 or newer** and an internet connection (Gradle downloads
Minecraft, the Yarn mappings and Fabric API on the first build).

```bash
./gradlew build
```

The jar lands in `build/libs/uncanny-<version>.jar`.

On Windows use `gradlew.bat build`. The Gradle wrapper is committed, so no separate
Gradle install is needed.

The first build downloads roughly 500 MB of dependencies. Later builds are fast.

## Installing

1. Install [Fabric](https://fabricmc.net/use/) for **Minecraft 1.20.1**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.20.1.
3. Put both `fabric-api-*.jar` and `uncanny-*.jar` in `.minecraft/mods`.
4. Play.

**Fabric API is the only dependency.** There are no mixins, no GeckoLib, no
Architectury, no Cardinal Components, no configuration screen mod. Nothing else is
required and nothing else is loaded.

## Playing

Start a normal world. Play normally. The mod does nothing for the first several
minutes, and then it does one small thing.

There is no tutorial, no guide book, no quest log, and no message telling you the
mod is active. Progress is driven by what you do:

| You do this | The mod notices |
|---|---|
| play for a while | the first quiet anomalies become possible |
| sleep | one of the ways in |
| go into deep water, or fall too far | another way in |
| build things | the House and the Copy start to resemble your home |
| read documents | the lore stage advances |
| walk through a door that was not there | the Archive |

If you want to see the whole arc quickly, use the debug command below.

## Configuration

`config/uncanny.json` is created on first launch. Every key has a default, so you
can delete anything you do not care about.

```jsonc
{
  "enabled": true,              // master switch
  "anomalyFrequency": 1.0,      // 0.5 is calmer, 2.0 is much busier
  "gracePeriodMinutes": 12,     // nothing happens before this
  "anomalyCooldownTicks": 3600, // 3 minutes between quiet anomalies
  "majorCooldownTicks": 30000,  // 25 minutes between major ones
  "discoveriesPerStage": 6,     // how fast the lore advances
  "landmarkChance": 0.035,      // how often anchor rooms and ring chambers appear
  "roomWeights": { "hallway_straight": 35.0, "impossible_room": 2.0 },
  "perPlayerAnomalies": true,   // false = world-wide anomalies for everyone
  "audioVolume": 0.8
}
```

See [docs/TUNING.md](docs/TUNING.md) for what each one actually does.

## The `/uncanny` command

Level 2 (operator) only. It is a support tool; the game never mentions it.

```
/uncanny status          what the mod currently believes about you and the world
/uncanny goto hall       jump to a layer, ignoring the progression gates
/uncanny stage 6         push your lore stage forward
/uncanny ledger          open the Ledger as it currently stands
/uncanny weaken 3        give way on SEAL III immediately
/uncanny templates       list every registered room template
```

---

## How it works

The short version, package by package. Full detail in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Generation is chunk-local and deterministic

Each chunk in a modular layer holds exactly one room, chosen from
`(worldSeed, dimensionSeed, chunkX, chunkZ)`. A room never writes outside its own
chunk.

Connectivity is guaranteed by a spanning tree computed from coordinates alone:
every chunk except the origin picks one parent, always west or north, so following
parents always walks toward the origin and the whole grid is one connected
structure. A few extra links are added on top for junctions, and because an extra
link is hashed from the *pair* of chunks it joins, both sides always agree without
talking to each other. Dead ends are not placed - they are what a leaf of the tree
looks like.

Chunks are filled on load through a bounded queue, two per tick, so entering a layer
for the first time never stalls.

### The event director

`UncannyEventManager` runs every two seconds, per player. It gathers every event
whose conditions hold, picks one weighted by severity, and runs it. Cooldowns and
one-time flags are persisted, so an event cannot fire twice by accident and cannot
be repeated by relogging.

### Persistence

One `PersistentState` (`UncannyWorldState`) holds both halves:

- **world state** - seals, generated chunks, thresholds, the Ledger's position,
  shared lore. Identical for every player on a server.
- **player state** - one record per UUID: entry number, counters, discoveries,
  clues, the Ledger's state, and a lightweight fingerprint of what they built.

### Multiplayer

Progression is server-side only. Anomalies are per-player by default, so two people
can stand in the same corridor and only one of them hears the knock. The House and
the Copy are generated from the fingerprint of the world's *subject* - the first
player to progress far enough - because a dimension is shared and cannot be
per-player.

---

## Extending it

The three things people ask for first:

**A new room.** Write one method, add one `register()` line. See
[data/uncanny/structures/README.md](src/main/resources/data/uncanny/structures/README.md).

**A new anomaly.** Subclass `UncannyEvent`, describe it with conditions, add one
line to `events/impl/AllEvents.java`. The director handles timing, cooldowns and
persistence.

**A new document.** Drop a JSON file in
`src/main/resources/data/uncanny/lore/`. No Java, no restart of anything but the
game. See [docs/LORE.md](docs/LORE.md) for the writing rules, which matter more
here than the format does.

**A new layer.** One enum entry in `UncannyDimension`, one dimension JSON, one
dimension type JSON, one biome JSON, one entry in `FogEffects`.

---

## What has and has not been verified

Be precise about this, because it matters if you are about to run the mod.

**Verified here**, with `python3 tools/check_project.py`:

- all 89 Java files parse cleanly under a real Java 17 grammar;
- every `import dev.uncanny.*` resolves to a file that exists;
- every call into the mod's own classes uses a method that exists, with an argument
  count matching a declared overload (503 calls checked);
- no assignment from a method that returns `void`;
- every registered block and item has a blockstate, a model and a translation;
- every model points at a texture that exists;
- every dimension points at a dimension type and biome that exist, and its client
  effects identifier is registered in Java;
- all 56 JSON files are valid; all 41 lore documents are complete and unique.

That checker is not a stub of the build - it caught four real bugs while it was
being written (an invalid hex literal, a class name that did not exist, a `void`
method being assigned from, and `Blocks.BREAD`, which is an item).

**Not verified here:** compilation against Minecraft itself. This sandbox has no
route to Maven Central or Fabric's maven repository, so `./gradlew build` cannot
resolve `com.mojang:minecraft` or `fabric-api`, and no `.class` files were produced.
The Minecraft and Fabric API call sites were written from the 1.20.1 Yarn mappings
and reviewed, and the checker guards the mod's own internal consistency, but the
first real build has to happen on a machine with network access. The
`.github/workflows/build.yml` workflow runs it on push.

Also not verified: in-game behaviour. Nothing here has been run inside Minecraft.
[docs/TESTING.md](docs/TESTING.md) is the checklist to work through on a real
client.

---

## Project layout

```
build.gradle, gradle.properties, settings.gradle   Fabric 1.20.1 project
src/main/java/dev/uncanny/
    UncannyMod.java          server entry point: registration and scheduling
    UncannyClient.java       client entry point: visuals and payloads
    Uncanny.java             the mod id, in one place
    config/                  UncannyConfig - config/uncanny.json
    dimension/               the layers, and how you get between them
    generation/              the procedural room system
    builders/                what each room actually looks like
    events/                  the event director
    events/impl/             every anomaly
    lore/                    documents, anchors, seals, the Ledger
    player/                  what the mod remembers about you
    data/                    what the mod remembers about the world
    block/ item/             the six blocks and three items
    audio/                   sound, and silence
    rendering/ screen/       client visuals and the reading screen
    net/                     the two display-only payloads
    util/                    seeds, positions, NBT, throttles
src/main/resources/
    fabric.mod.json
    assets/uncanny/          lang, blockstates, models, generated textures
    data/uncanny/
        dimension/           8 layer definitions
        dimension_type/      8 dimension types
        worldgen/biome/      8 biomes, all with empty spawn lists
        lore/                41 documents
docs/                        architecture, lore bible, tuning, testing
tools/
    generate_textures.py     regenerates every texture from code
    check_project.py         the dependency-free checks described above
```

## License

MIT. See [LICENSE](LICENSE).
