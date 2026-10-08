# Testing

## What was actually verified

`python3 tools/check_project.py` runs dependency-free checks over the whole project.
It is not a build, and it is not a substitute for one - but it is real: while it was
being written it caught four genuine bugs (an invalid hex literal, a class name that
did not exist, a `void` method being assigned from, and `Blocks.BREAD`, which is an
item and not a block).

It currently passes 903 checks:

```
parsed 90 java files
90 classes found
6 blocks, 9 items cross-referenced
9 texture references checked
8 dimensions cross-referenced
520 internal calls checked against 102 classes
0 bad assignments from void methods
56 json files validated
41 lore documents checked
block name check across 34 known item-only names

OK    903 checks passed
```

The count is 293 checks plus 90 parsed files plus 520 calls. The two tree-sitter
passes need `pip install tree-sitter tree-sitter-java`; without them the script still
runs and says so, so a bare `python3 tools/check_project.py` cannot quietly look like
the full run:

```
WARN  tree_sitter is not installed; skipping the syntax pass (...)
WARN  tree_sitter missing; skipping the call-arity pass
OK    292 checks passed (skipped: java syntax, internal call arity)
```

The checker is injection-tested. Dropping in a call to a method that does not exist, a
call with the wrong number of arguments, or a syntax error each produces an exit code
of 1 and a line naming the file, so a clean run means the passes actually ran:

```
- call: ... calls StructureBits.noSuchMethod, which is not declared in StructureBits
- call: ... calls StructureBits.tally with 4 args, declared with [5]
- syntax: src/main/java/dev/uncanny/builders/HallTemplates.java:193:44
```

Covered: syntax (real Java 17 grammar), internal imports, method existence and
argument counts for every call into the mod's own classes, `void` assignments,
block/item/blockstate/model/texture/lang cross-references, dimension to dimension
type to biome references, client effects registration, JSON validity, lore
completeness, and `fabric.mod.json` entrypoints.

**Not covered:** compilation against Minecraft. This sandbox cannot reach Maven
Central or Fabric's maven repository, so `./gradlew build` cannot resolve
`com.mojang:minecraft` or `fabric-api`. No `.class` files were produced here, and
nothing has been run inside Minecraft. The first real build has to happen somewhere
with network access. `ci/build.yml` is a ready-to-use GitHub Actions workflow for
it; copy it into `.github/workflows/` to turn it on.

---

## Checklist for a real client

Work through this on a machine that can build. Anything that fails is a bug, not a
limitation.

### New world

- [ ] world creates without errors in the log
- [ ] `config/uncanny.json` appears
- [ ] no anomalies for the first `gracePeriodMinutes`
- [ ] after that, at most one quiet thing every few minutes

### Existing world

- [ ] adding the mod to a world that already exists does not corrupt it
- [ ] nothing is generated in the Overworld until an event does it

### Dimensions

- [ ] `/uncanny goto hall` arrives standing on solid ground, not in a wall
- [ ] corridors connect. Walk at least 40 chunks without hitting a sealed end.
      The generator cuts access paths after every builder runs, so a template cannot
      seal its own doorway even by accident
- [ ] the same corridor is identical after relogging
- [ ] the same corridor is identical after the chunk unloads and reloads
- [ ] two different world seeds produce visibly different Halls
- [ ] anchor rooms appear and are numbered. Two rooms in one world CAN share a
      number - the number is a pure function of the room's seed, and "the same room
      found in three locations" is one of the things the Surveyors wrote down
- [ ] the Archive contains the Ledger exactly once
- [ ] the Deep is solid rock with tunnels cut through it
- [ ] the shaft in the Deep leads down to Below, and Below has the door marked ABYSS
- [ ] the Prison of Stars is visible and one light brightens over time
- [ ] the ring chamber rotates when you look away and look back
- [ ] the Partition contains fragments of other layers

### The Ledger

- [ ] it does nothing at all when used early. No message, no sound, no particles
- [ ] later, it opens and the first page is your username
- [ ] the entry number matches the anchor rooms you found
- [ ] the events on its pages are things that actually happened to you
- [ ] the status lines advance, and `VACANT` is never explained
- [ ] the door appears afterwards and you are not forced through it

### Persistence

- [ ] quit mid-anomaly, restart, come back: the anomaly is still there
- [ ] the Ledger does not reset
- [ ] seal state survives a restart
- [ ] a one-time event never repeats after a relog
- [ ] generated chunks are not regenerated (no visible rebuild)

### Multiplayer

- [ ] two players in the same corridor get different anomalies
- [ ] world lore is the same for both
- [ ] the House is generated from the world's subject, and both players see the same
      House
- [ ] a client without the mod can join and play; they just miss the visuals
- [ ] no progression depends on client state

### Death and sleeping

- [ ] death is recorded and appears on the Ledger
- [ ] sleeping can relocate you, but not before the third night
- [ ] respawn puts you back where it should

### Performance

- [ ] entering a layer for the first time does not stall the client
- [ ] `/uncanny status` shows a small queue that drains
- [ ] flying quickly through the Hall does not grow the queue without limit
- [ ] the mod is playable on integrated server with a modest render distance

---

## Known gaps and extension points

Honest list of what is deliberately unfinished rather than silently broken.

| Area | State | Where to continue |
|---|---|---|
| Custom audio files | not shipped. Everything is a vanilla sound at an unusual pitch or position. Adding `.ogg` files means a `sounds.json` and one method in `UncannySounds`. | `audio/UncannySounds.java` |
| `playerAnomalySpacing` | declared in config, not enforced. Anomalies are per-player instead of spaced. | `events/UncannyEventManager.java` |
| The Copy | resembles the Overworld structurally rather than block-for-block. Making it a true offset copy would require reading the Overworld during generation, which is not safe from a chunk generator. | `builders/NatureGenerator.java` |
| The House in multiplayer | generated from the world's subject, not from each player's own home, because a dimension is shared. | `builders/HouseGenerator.java` |
| Suppressing vanilla cave ambience | not done. It would need a mixin, and the mod avoids mixins on purpose. Music is stopped instead. | `audio/AmbientManager.java` |
| The approaching star | expressed as increasing brightness, not movement. Moving a light source across a void is imperceptible and expensive. | `builders/StarField.java` |
| Seal plates | appear through events and in the Archive/Partition, not on a fixed schedule in every layer. | `events/impl/SealPlateEvent.java` |
