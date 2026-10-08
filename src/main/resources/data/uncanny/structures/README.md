# Room templates

Room templates are **not** JSON, and that is deliberate.

A template has two halves:

1. **its description** - id, size, weight, rarity, category, which connection
   patterns it fits on, whether it may hold lore or anomalies, whether it can be a
   landmark. This is data, and the parts a pack author will want to change (weights
   and rarity) are already in `config/uncanny.json`.

2. **its construction** - the actual block layout. This is a function that writes
   blocks. Expressing it as JSON would mean either shipping NBT structure files,
   which cannot be read or edited by a human, or inventing a block-placement DSL,
   which is more code than the templates themselves.

So the description lives in `RoomTemplate` and the construction lives in
`builders/`. Adding a room is two steps:

```java
// 1. write the layout in builders/HallTemplates.java
private static void myRoom(GenerationContext ctx) {
    Palette.Scheme scheme = seal(ctx, 5);
    ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
    // ...
}

// 2. describe it in HallTemplates.register()
RoomTemplates.register(hall, new RoomTemplate.Builder()
        .id("my_room")
        .category(RoomTemplate.Category.ROOM)
        .weight(6)
        .rarity(RoomTemplate.Rarity.UNCOMMON)
        .links(1, 2)
        .draw(HallTemplates::myRoom)
        .build());
```

Nothing else changes. The generator finds it, the weights come from config, and the
room is immediately part of the procedural layout.

## Tuning without touching Java

`config/uncanny.json` holds a weight for every template id:

```json
"roomWeights": {
  "hallway_straight": 35.0,
  "impossible_room": 2.0
}
```

Set a weight to `0` to remove a room from generation entirely. Set `anomalyRoomChance`
to `0` to turn the rare rooms off. Templates not listed in the config fall back to
the weight declared in their builder.
