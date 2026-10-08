package dev.uncanny.builders;

import dev.uncanny.builders.StructureBits;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.generation.GenerationContext;
import dev.uncanny.generation.GenerationRules;
import dev.uncanny.generation.RoomTemplate;
import dev.uncanny.generation.RoomTemplates;
import dev.uncanny.item.UncannyBlocks;
import dev.uncanny.lore.LoreManager;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;

/**
 * THE ARCHIVE.
 *
 * A library. Shelves, reading desks, catalogues, and - in exactly one place in the
 * whole layer - the Ledger.
 *
 * Most of what is on the shelves is boring. That is not padding, it is the design:
 * a player who has read eleven boundary measurements and then finds one that names
 * them has done the work themselves, and no amount of atmosphere could substitute.
 */
public final class ArchiveTemplates {

    private static final int[] STRAIGHT = {GenerationRules.NORTH | GenerationRules.SOUTH,
            GenerationRules.EAST | GenerationRules.WEST};
    private static final int[] ANY = {1, 2, 4, 8, 3, 5, 6, 9, 10, 12, 7, 11, 13, 14, 15};

    private ArchiveTemplates() {
    }

    public static void register() {
        UncannyDimension archive = UncannyDimension.ARCHIVE;

        RoomTemplates.register(archive, new RoomTemplate.Builder()
                .id("archive_corridor").category(RoomTemplate.Category.CORRIDOR)
                .weight(40).masks(STRAIGHT).draw(ArchiveTemplates::corridor).build());

        RoomTemplates.register(archive, new RoomTemplate.Builder()
                .id("archive_stacks").category(RoomTemplate.Category.ROOM)
                .weight(28).masks(ANY).lore(true).draw(ArchiveTemplates::stacks).build());

        RoomTemplates.register(archive, new RoomTemplate.Builder()
                .id("archive_reading").category(RoomTemplate.Category.ROOM)
                .weight(12).links(1, 2).lore(true).draw(ArchiveTemplates::readingRoom).build());

        RoomTemplates.register(archive, new RoomTemplate.Builder()
                .id("archive_catalogue").category(RoomTemplate.Category.ROOM)
                .weight(8).masks(ANY).draw(ArchiveTemplates::catalogue).build());

        RoomTemplates.register(archive, new RoomTemplate.Builder()
                .id("archive_empty").category(RoomTemplate.Category.ANOMALY)
                .rarity(RoomTemplate.Rarity.RARE).weight(3).masks(ANY)
                .draw(ArchiveTemplates::emptied).build());

        RoomTemplates.register(archive, new RoomTemplate.Builder()
                .id("archive_ledger_hall").category(RoomTemplate.Category.LANDMARK)
                .rarity(RoomTemplate.Rarity.VERY_RARE).weight(1).landmark(true).masks(ANY)
                .draw(ArchiveTemplates::ledgerHall).build());
    }

    // ------------------------------------------------------------- templates

    private static Palette.Scheme seal(GenerationContext ctx, int height) {
        Palette.Scheme scheme = Palette.of(UncannyDimension.ARCHIVE, ctx.seed());
        ctx.shell(scheme.wall(), scheme.floor(), height);
        return scheme;
    }

    private static void corridor(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 6);
        ctx.fill(1, 0, 1, 14, 5, 14, scheme.wall());
        // The corridor runs along whichever axis the chunk is connected on, or an
        // east-west corridor ends up with its doorways facing a wall.
        boolean alongZ = ctx.hasLink(net.minecraft.util.math.Direction.NORTH)
                || ctx.hasLink(net.minecraft.util.math.Direction.SOUTH);
        if (alongZ) {
            ctx.clear(5, 0, 0, 10, 4, 15);
            ctx.fill(5, 0, 0, 10, 0, 15, scheme.floor());
            // Shelves along both sides. Reading while walking.
            StructureBits.shelfWall(ctx, 1, 1, 2, 3, 3, Direction.Axis.Z);
            StructureBits.shelfWall(ctx, 12, 1, 2, 3, 3, Direction.Axis.Z);
        } else {
            ctx.clear(0, 0, 5, 15, 4, 10);
            ctx.fill(0, 0, 5, 15, 0, 10, scheme.floor());
            StructureBits.shelfWall(ctx, 2, 1, 1, 3, 3, Direction.Axis.X);
            StructureBits.shelfWall(ctx, 2, 1, 12, 3, 3, Direction.Axis.X);
        }
        ctx.set(7, 5, 7, scheme.light());
        if (ctx.random().chance(0.4)) {
            LoreManager.placeInChest(ctx, 2, 1, 12, 2, ctx.seed());
        }
    }

    private static void stacks(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 8);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        // Three rows of shelving with aisles between them.
        for (int row = 0; row < 3; row++) {
            int z = 2 + row * 5;
            StructureBits.shelfWall(ctx, 2, 1, z, 12, 5, Direction.Axis.X);
        }
        ctx.clear(7, 0, 1, 8, 6, 14);
        ctx.set(7, 7, 7, scheme.light());
        ctx.set(8, 7, 8, scheme.light());
        if (ctx.random().chance(0.5)) {
            LoreManager.placeInChest(ctx, 12, 1, 12, 3, ctx.seed());
        }
        // One shelf has been cleared. Not tidied: cleared.
        ctx.fill(4, 1, 7, 6, 5, 7, Blocks.AIR.getDefaultState());
    }

    private static void readingRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 5);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        for (int[] desk : new int[][]{{4, 4}, {10, 4}, {4, 11}, {10, 11}}) {
            StructureBits.table(ctx, desk[0], 1, desk[1], scheme.accent());
            StructureBits.chair(ctx, desk[0], 1, desk[1] + 2, net.minecraft.util.math.Direction.NORTH);
            ctx.set(desk[0], 2, desk[1], Blocks.LANTERN.getDefaultState());
        }
        ctx.set(7, 4, 7, scheme.light());
        if (ctx.random().chance(0.6)) {
            LoreManager.placeInChest(ctx, 7, 1, 13, 3, ctx.seed());
        }
    }

    private static void catalogue(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 5);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        // Drawers. A wall of them, and one is open.
        for (int x = 2; x < 14; x += 2) {
            // Leave a gap in the middle, or the drawer wall seals the doorway.
            if (x == 6 || x == 8) {
                continue;
            }
            for (int y = 1; y <= 3; y++) {
                ctx.set(x, y, 13, Blocks.BARREL.getDefaultState());
            }
        }
        ctx.set(8, 2, 13, Blocks.AIR.getDefaultState());
        ctx.sign(8, 1, 12, "DRAWER 8", "EMPTY SINCE", "BEFORE THE SURVEY", "");
        ctx.set(7, 4, 7, scheme.light());
        // A map table. The maps are wrong, and the player cannot tell yet.
        ctx.fill(5, 1, 5, 10, 1, 9, scheme.accent());
        ctx.fill(6, 2, 6, 9, 2, 8, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
    }

    /** A room where every shelf has been emptied. The dust has not been disturbed. */
    private static void emptied(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 7);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        for (int row = 0; row < 3; row++) {
            StructureBits.shelfWall(ctx, 2, 1, 2 + row * 5, 12, 4, Direction.Axis.X);
        }
        // Remove every book. Leave the shelves.
        for (int x = 2; x < 14; x++) {
            for (int row = 0; row < 3; row++) {
                for (int y = 1; y <= 4; y++) {
                    ctx.set(x, y, 2 + row * 5, scheme.accent());
                }
            }
        }
        ctx.set(7, 6, 7, scheme.light());
        ctx.sign(7, 1, 12, "WITHDRAWN", "", "", "");
    }

    /**
     * The hall the Ledger is in.
     *
     * Only the primary chunk of the landmark draws the plinth. The book is placed
     * once per world and its position is written to world state, so the compass can
     * point at it and so it is never generated twice.
     */
    private static void ledgerHall(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 12);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());

        if (!ctx.room().isLandmarkPrimary()) {
            // Approach corridors, so the hall can be entered from any side.
            StructureBits.shelfWall(ctx, 1, 1, 7, 14, 6, Direction.Axis.X);
            ctx.clear(7, 0, 0, 8, 4, 15);
            return;
        }

        // Columns, a floor of a different stone, and a lot of height.
        for (int[] column : new int[][]{{3, 3}, {3, 12}, {12, 3}, {12, 12}}) {
            ctx.fill(column[0], 1, column[1], column[0] + 1, 10, column[1] + 1, scheme.accent());
        }
        ctx.fill(6, 0, 6, 9, 0, 9, Blocks.QUARTZ_BLOCK.getDefaultState());
        ctx.set(7, 11, 7, scheme.light());
        ctx.set(8, 11, 8, scheme.light());

        // The plinth, and the book on it.
        ctx.fill(7, 1, 7, 8, 2, 8, Blocks.QUARTZ_BLOCK.getDefaultState());
        BlockPos book = ctx.pos(7, 3, 7);
        ctx.setBlock(book, UncannyBlocks.LEDGER.getDefaultState());

        if (ctx.state().ledgerPosition() == null) {
            ctx.state().setLedgerPosition(book);
        }

        // The eye ring, high up, where it will be noticed eventually.
        StructureBits.eyeRing(ctx, 8, 10, 8, 5, ctx.state().eyeSeed(ctx.world().getSeed()), Direction.Axis.Y);

        // One reading desk, facing the book. Someone sat here a long time.
        StructureBits.table(ctx, 5, 1, 10, scheme.accent());
        StructureBits.chair(ctx, 5, 1, 9, net.minecraft.util.math.Direction.SOUTH);
        ctx.set(5, 2, 10, Blocks.LANTERN.getDefaultState());

        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(ctx.seed(), "ledger_hall"));
        ctx.sign(10, 1, 10, "DO NOT", "ATTEMPT TO", "OPEN", "");
        if (random.nextBoolean()) {
            ctx.sign(11, 1, 10, "WE SPENT", "TWELVE YEARS", "", "");
        }
    }

}
