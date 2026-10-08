package dev.uncanny.builders;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.generation.GenerationContext;
import dev.uncanny.generation.GenerationRules;
import dev.uncanny.generation.RoomTemplate;
import dev.uncanny.generation.RoomTemplates;
import dev.uncanny.item.UncannyBlocks;
import dev.uncanny.lore.AnchorManager;
import dev.uncanny.player.UncannyPlayerData;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * THE PARTITION.
 *
 * Not a throne room. A facility, silent and enormous, assembled out of pieces of
 * every other layer: a length of the Hall here, a corner of a house there, shelving
 * from the Archive, rock from the Deep, grass from the Woods.
 *
 * It is generated the same way as the Hall - modules on a grid, chosen from a seed -
 * but its module list is borrowed. That is why it feels like a place that has been
 * collecting things rather than a place that was designed.
 *
 * Two landmarks live here: the ring chamber, and the room with the Ledger in it.
 */
public final class PartitionTemplates {

    private static final int[] STRAIGHT = {GenerationRules.NORTH | GenerationRules.SOUTH,
            GenerationRules.EAST | GenerationRules.WEST};
    private static final int[] ANY = {1, 2, 4, 8, 3, 5, 6, 9, 10, 12, 7, 11, 13, 14, 15};

    private PartitionTemplates() {
    }

    public static void register() {
        UncannyDimension partition = UncannyDimension.PARTITION;

        RoomTemplates.register(partition, new RoomTemplate.Builder()
                .id("partition_corridor").category(RoomTemplate.Category.CORRIDOR)
                .weight(30).masks(STRAIGHT).draw(PartitionTemplates::corridor).build());

        RoomTemplates.register(partition, new RoomTemplate.Builder()
                .id("fragment_hall").category(RoomTemplate.Category.ROOM)
                .weight(14).masks(ANY).draw(PartitionTemplates::fragmentHall).build());

        RoomTemplates.register(partition, new RoomTemplate.Builder()
                .id("fragment_house").category(RoomTemplate.Category.ROOM)
                .weight(12).masks(ANY).draw(PartitionTemplates::fragmentHouse).build());

        RoomTemplates.register(partition, new RoomTemplate.Builder()
                .id("fragment_woods").category(RoomTemplate.Category.ROOM)
                .weight(10).masks(ANY).draw(PartitionTemplates::fragmentWoods).build());

        RoomTemplates.register(partition, new RoomTemplate.Builder()
                .id("fragment_archive").category(RoomTemplate.Category.ROOM)
                .weight(10).masks(ANY).draw(PartitionTemplates::fragmentArchive).build());

        RoomTemplates.register(partition, new RoomTemplate.Builder()
                .id("fragment_deep").category(RoomTemplate.Category.ROOM)
                .weight(8).masks(ANY).draw(PartitionTemplates::fragmentDeep).build());

        RoomTemplates.register(partition, new RoomTemplate.Builder()
                .id("anchor_corridor").category(RoomTemplate.Category.LANDMARK)
                .rarity(RoomTemplate.Rarity.RARE).weight(4).landmark(true).masks(ANY)
                .draw(PartitionTemplates::anchorCorridor).build());

        RoomTemplates.register(partition, new RoomTemplate.Builder()
                .id("ring_chamber").category(RoomTemplate.Category.LANDMARK)
                .rarity(RoomTemplate.Rarity.RARE).weight(3).landmark(true).masks(ANY)
                .draw(PartitionTemplates::ringChamber).build());

        RoomTemplates.register(partition, new RoomTemplate.Builder()
                .id("partition_ledger_room").category(RoomTemplate.Category.LANDMARK)
                .rarity(RoomTemplate.Rarity.VERY_RARE).weight(2).landmark(true).masks(ANY)
                .draw(PartitionTemplates::ledgerRoom).build());
    }

    // ------------------------------------------------------------- templates

    private static Palette.Scheme seal(GenerationContext ctx, int height) {
        Palette.Scheme scheme = Palette.of(UncannyDimension.PARTITION, ctx.seed());
        ctx.shell(scheme.wall(), scheme.floor(), height);
        return scheme;
    }

    private static void corridor(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 7);
        ctx.fill(1, 0, 1, 14, 6, 14, scheme.wall());
        if (ctx.hasLink(net.minecraft.util.math.Direction.NORTH)
                || ctx.hasLink(net.minecraft.util.math.Direction.SOUTH)) {
            ctx.clear(6, 0, 0, 9, 4, 15);
            ctx.fill(6, 0, 0, 9, 0, 15, scheme.floor());
        } else {
            ctx.clear(0, 0, 6, 15, 4, 9);
            ctx.fill(0, 0, 6, 15, 0, 9, scheme.floor());
        }
        // The corridor is too wide, which is the only thing wrong with it.
        ctx.set(7, 6, 7, scheme.light());
        ctx.set(8, 6, 8, scheme.light());
    }

    /** A piece of the Hall, set into a wall like a specimen. */
    private static void fragmentHall(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 8);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        Palette.Scheme hall = Palette.of(UncannyDimension.HALL, ctx.seed());
        ctx.fill(4, 1, 4, 11, 5, 11, hall.wall());
        ctx.clear(7, 1, 4, 8, 3, 11);
        ctx.set(7, 4, 7, hall.light());
        ctx.sign(12, 1, 12, "SPECIMEN", "HALL", "SECTION 8", "");
    }

    /** A corner of someone's house. Possibly the player's. */
    private static void fragmentHouse(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 6);
        ctx.fill(1, 0, 1, 14, 0, 14, Blocks.OAK_PLANKS.getDefaultState());
        UncannyPlayerData subject = subjectOf(ctx.state());
        net.minecraft.block.Block wall = Blocks.OAK_PLANKS;
        if (subject != null) {
            var block = net.minecraft.registry.Registries.BLOCK.get(
                    new net.minecraft.util.Identifier(subject.home.dominantMaterial()));
            if (block != Blocks.AIR) {
                wall = block;
            }
        }
        ctx.fill(3, 1, 3, 3, 4, 12, wall.getDefaultState());
        ctx.fill(3, 1, 12, 12, 4, 12, wall.getDefaultState());
        ctx.set(5, 1, 5, Blocks.RED_BED.getDefaultState());
        ctx.set(10, 1, 5, Blocks.CHEST.getDefaultState());
        ctx.set(10, 1, 9, Blocks.CRAFTING_TABLE.getDefaultState());
        ctx.set(6, 4, 8, Blocks.LANTERN.getDefaultState());
    }

    /** Grass, a tree, and a sky that is not there. */
    private static void fragmentWoods(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 9);
        ctx.fill(1, 0, 1, 14, 0, 14, Blocks.GRASS_BLOCK.getDefaultState());
        ctx.fill(7, 1, 7, 7, 5, 7, Blocks.OAK_LOG.getDefaultState());
        ctx.fill(5, 6, 5, 9, 6, 9, Blocks.OAK_LEAVES.getDefaultState());
        ctx.set(3, 1, 11, Blocks.PODZOL.getDefaultState());
        ctx.set(11, 1, 3, Blocks.OAK_SIGN.getDefaultState());
        ctx.sign(11, 1, 3, "TRAIL", "ENDS", "", "");
        ctx.set(7, 8, 7, scheme.light());
    }

    /** Shelving, and one shelf that has been read to the last page. */
    private static void fragmentArchive(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 7);
        Palette.Scheme archive = Palette.of(UncannyDimension.ARCHIVE, ctx.seed());
        ctx.fill(1, 0, 1, 14, 0, 14, archive.floor());
        StructureBits.shelfWall(ctx, 1, 1, 3, 14, 5, Direction.Axis.X);
        StructureBits.shelfWall(ctx, 1, 1, 12, 14, 5, Direction.Axis.X);
        ctx.clear(7, 0, 4, 8, 5, 11);
        ctx.set(7, 6, 7, scheme.light());
        ctx.fill(9, 1, 3, 11, 5, 3, Blocks.AIR.getDefaultState());
        ctx.sign(9, 1, 4, "WITHDRAWN", "ALL", "", "");
    }

    /** Rock from the Deep, and a door with a number on it. */
    private static void fragmentDeep(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 6);
        Palette.Scheme deep = Palette.of(UncannyDimension.DEEP, ctx.seed());
        ctx.fill(1, 0, 1, 14, 0, 14, deep.floor());
        ctx.fill(2, 1, 2, 13, 4, 13, deep.wall());
        ctx.clear(6, 1, 6, 9, 3, 9);
        int number = AnchorManager.numberFor(ctx.seed());
        ctx.door(7, 1, 6, Direction.SOUTH, false);
        ctx.wallSign(7, 4, 6, Direction.SOUTH, AnchorManager.label(number), "", "", "");
        ctx.set(7, 5, 7, deep.light());
    }

    /**
     * A row of numbered doors. Some of them are open.
     *
     * The doors face inwards from the side walls rather than across the corridor,
     * so the walkable axis is never blocked by a door that happens to be shut.
     */
    private static void anchorCorridor(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 6);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        ctx.fill(1, 1, 1, 2, 4, 14, scheme.wall());
        ctx.fill(13, 1, 1, 14, 4, 14, scheme.wall());
        for (int z = 2; z < 14; z += 3) {
            int number = AnchorManager.numberFor(ctx.salt(z));
            ctx.door(3, 1, z, Direction.EAST, ctx.random().chance(0.3));
            ctx.wallSign(3, 4, z, Direction.EAST, AnchorManager.label(number), "", "", "");
            int other = AnchorManager.numberFor(ctx.salt(z + 41));
            ctx.door(12, 1, z, Direction.WEST, ctx.random().chance(0.3));
            ctx.wallSign(12, 4, z, Direction.WEST, AnchorManager.label(other), "", "", "");
        }
        ctx.set(7, 5, 7, scheme.light());
    }

    /** The ring chamber. The room the player turns around in. */
    private static void ringChamber(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 16);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        if (ctx.room().isLandmarkPrimary()) {
            EzekielRings.build(ctx);
            ctx.set(7, 15, 7, scheme.light());
        } else {
            // Approach corridor. The chamber is entered from one side only.
            ctx.fill(6, 1, 0, 9, 5, 15, scheme.wall());
            ctx.clear(7, 0, 0, 8, 3, 15);
        }
    }

    /**
     * The room with the Ledger in it.
     *
     * This is the end of the mod. There is nothing past it, and nothing behind the
     * book. The player reads it, the page changes, and a door appears somewhere else
     * in the layer that looks like the door to their own house.
     */
    private static void ledgerRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 12);
        ctx.fill(1, 0, 1, 14, 0, 14, Blocks.QUARTZ_BLOCK.getDefaultState());
        if (!ctx.room().isLandmarkPrimary()) {
            ctx.fill(1, 1, 1, 14, 10, 14, scheme.wall());
            ctx.clear(7, 0, 0, 8, 3, 15);
            return;
        }

        // Columns and height. Silence is a property of a room this empty.
        for (int[] column : new int[][]{{2, 2}, {2, 13}, {13, 2}, {13, 13}}) {
            ctx.fill(column[0], 1, column[1], column[0] + 1, 10, column[1] + 1, Blocks.QUARTZ_PILLAR.getDefaultState());
        }
        ctx.set(7, 11, 7, Blocks.SEA_LANTERN.getDefaultState());
        ctx.set(8, 11, 8, Blocks.SEA_LANTERN.getDefaultState());

        // The plinth and the book.
        ctx.fill(7, 1, 7, 8, 2, 8, Blocks.QUARTZ_BLOCK.getDefaultState());
        BlockPos book = ctx.pos(7, 3, 7);
        ctx.setBlock(book, UncannyBlocks.LEDGER.getDefaultState());

        // The eye ring, above it, where it cannot be avoided.
        StructureBits.eyeRing(ctx, 8, 10, 8, 6, ctx.state().eyeSeed(ctx.world().getSeed()), Direction.Axis.Y);

        // A seal plate showing what has already happened.
        ctx.setBlock(ctx.pos(4, 1, 11), UncannyBlocks.SEAL_PLATE.getDefaultState()
                .with(UncannyBlocks.OPENED, ctx.state().openedSealCount()));

        ctx.sign(11, 1, 4, "ANCHOR 740", "DECEASED", "", "");
        ctx.sign(11, 1, 5, "ANCHOR 741", "ACTIVE", "", "");

        ctx.state().setPartitionBuilt(true);
    }

    private static UncannyPlayerData subjectOf(UncannyWorldState state) {
        var subject = state.subject();
        return subject == null ? null : (state.hasPlayer(subject) ? state.player(subject) : null);
    }
}
