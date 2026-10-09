# Testing

## What was actually verified

`python3 tools/check_project.py` runs dependency-free checks over the whole project.
It is not a build, and it is not a substitute for one - but it is real: while it was
being written it caught four genuine bugs (an invalid hex literal, a class name that
did not exist, a `void` method being assigned from, and `Blocks.BREAD`, which is an
item and not a block).

It currently passes 1180 checks:

```
parsed 115 java files
115 classes found
6 blocks, 9 items cross-referenced
9 texture references checked
8 dimensions cross-referenced
747 internal calls checked against 133 classes
0 bad assignments from void methods
56 json files validated
41 lore documents checked
block name check across 34 known item-only names

OK    1180 checks passed
```

The count is 293 checks plus 115 parsed files plus 747 calls. The two tree-sitter
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

**Not covered locally:** compilation against Minecraft. This sandbox cannot reach
Maven Central or Fabric's maven repository, so `./gradlew build` cannot resolve
`com.mojang:minecraft` or `fabric-api`. The real build runs on GitHub Actions:
`.github/workflows/build.yml` (copied from `ci/build.yml`) executes the gradle
build plus this static checker on every push. If the workflow file is missing
on a fresh branch, it is because pushing workflow files requires the
`workflows` permission the automation token does not have - a human adds the
file once via the GitHub web editor (Settings → the file lives at
`.github/workflows/build.yml`), and everything after that is push-driven.

---

## The anomaly system, specifically

### Dev-only commands (permission level 2)

- `/uncanny evaluate` - prints `id -> detail` for every registered anomaly:
  grace period, cooldowns, stage, instability bounds, labeled conditions,
  probability. This is how you answer "why didn't X happen?" without waiting.
  Read-only; always available to ops.
- `/uncanny force <id>` - runs one anomaly immediately, booking the same
  cooldowns a real run would.
- `/uncanny instability <0-100>` - sets the hidden instability to 0.0-1.0.

`force` and `instability` additionally require `devForceCommands: true` in
`config/uncanny.json` (default **false**). With the flag off - normal play -
no command can shortcut the pacing system. Turn it on only for a balancing
session and turn it off again.

### Verified by CI

- [ ] `build` job compiles both source sets and runs gradle `check`
- [ ] `static-checks` job passes the tree-sitter checker
- [ ] server smoke: `runServer` starts with `--start-public --nogui`, a seeded
      config (`enabled: true`, `debugLogging: true`, `gracePeriodMinutes: 0`),
      the log contains `anomalies registered` and
      `event director active, checking every N ticks`, and the run greps
      `Uncanny heartbeat: ok` before shutting down cleanly

### Verified by hand (needs a player)

- [ ] first checks print the debug funnel: Player, Playtime, Stage, Reality
      instability, Candidates, Eligible, Selected, Executed - and rejection
      lines like `missing_tree -> condition failed: ...`, once per interval,
      never per tick
- [ ] with `gracePeriodMinutes: 5`, every check prints
      `-> grace period (n of 5 minutes)` and nothing else happens; after 5
      minutes anomalies become possible
- [ ] `/uncanny force door_state` works and the cooldown afterwards shows in
      `/uncanny evaluate`
- [ ] changing `eventCheckIntervalTicks` in the config is picked up without a
      restart (the log says `event check interval now every N ticks`)
- [ ] playtime accrual: relogging does not advance the grace timer (time
      offline does not count)
- [ ] instability rises after reading Ledger pages, sealing, entering an
      uncanny layer - and persists across restart (`/uncanny status`)
- [ ] at instability 0.8 `/uncanny evaluate` shows identity/ledger events
      losing their `reality too stable` rejection; at 0.0 they all have it
- [ ] the TREE chain: `missing_tree` sets the stage; `wrong_tree` reports
      `chain stage not reached` until both earlier stages are marked; after
      forcing both, `wrong_tree` becomes eligible near the original site
- [ ] a near miss does NOT raise instability and does NOT record an anomaly
      location (check `/uncanny status` before/after)
- [ ] an echo (`restored_block`) fires only on a block the player broke, only
      when they come back, and only after the configured delay - and the
      world has no echo spam: the same block is not restored twice
- [ ] Anchor 741, the Ledger, seals, dimensions and existing lore all behave
      as before this extension (the "Checklist for a real client" above still
      passes in full)
- [ ] `uncanny-force-test` off means no command can force a probability

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
