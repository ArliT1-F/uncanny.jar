package dev.uncanny.builders;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.generation.GenerationContext;
import dev.uncanny.generation.GenerationRules;
import dev.uncanny.generation.RoomTemplate;
import dev.uncanny.generation.RoomTemplates;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.item.UncannyBlocks;
import dev.uncanny.lore.AnchorManager;
import dev.uncanny.lore.LoreManager;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * THE HALL.
 *
 * Seventeen templates. Between them they are a corridor network that goes on for
 * as long as the player is willing to walk, and that is never the same twice in
 * two different worlds.
 *
 * Each template is one method. To add a room: write the method, then add one
 * register() line at the bottom. The generator finds it by itself.
 *
 * Local coordinates: x and z run 0..15 across the chunk, y runs up from floor
 * level. A template must not write outside its own chunk.
 */
public final class HallTemplates {

    /** Straight runs: N-S (1|4 = 5) and E-W (2|8 = 10). */
    private static final int[] STRAIGHT = {GenerationRules.NORTH | GenerationRules.SOUTH,
            GenerationRules.EAST | GenerationRules.WEST};

    /** Corners: the four pairs of adjacent sides. */
    private static final int[] CORNERS = {
            GenerationRules.NORTH | GenerationRules.EAST,
            GenerationRules.EAST | GenerationRules.SOUTH,
            GenerationRules.SOUTH | GenerationRules.WEST,
            GenerationRules.NORTH | GenerationRules.WEST};

    /** T junctions: three sides, i.e. four minus one. */
    private static final int[] TEES = {7, 11, 13, 14};

    /** Any single doorway. */
    private static final int[] SINGLE = {1, 2, 4, 8};

    /** Any pattern at all. */
    private static final int[] ANY = {1, 2, 4, 8, 3, 5, 6, 9, 10, 12, 7, 11, 13, 14, 15};

    private HallTemplates() {
    }

    public static void register() {
        UncannyDimension hall = UncannyDimension.HALL;

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("hallway_straight").category(RoomTemplate.Category.CORRIDOR)
                .weight(35).masks(STRAIGHT).draw(HallTemplates::straight).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("hallway_corner").category(RoomTemplate.Category.CORRIDOR)
                .weight(20).masks(CORNERS).draw(HallTemplates::corner).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("hallway_t").category(RoomTemplate.Category.CORRIDOR)
                .weight(9).masks(TEES).draw(HallTemplates::tee).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("hallway_cross").category(RoomTemplate.Category.CORRIDOR)
                .weight(6).masks(new int[]{15}).draw(HallTemplates::cross).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("dead_end").category(RoomTemplate.Category.CORRIDOR)
                .weight(8).masks(SINGLE).draw(HallTemplates::deadEnd).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("staircase").category(RoomTemplate.Category.CORRIDOR)
                .weight(5).masks(5, 10, 3, 6, 12, 9).draw(HallTemplates::staircase).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("room_small").category(RoomTemplate.Category.ROOM)
                .weight(8).links(1).draw(HallTemplates::smallRoom).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("room_large").category(RoomTemplate.Category.ROOM)
                .weight(10).masks(ANY).draw(HallTemplates::largeRoom).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("flooded_room").category(RoomTemplate.Category.ROOM)
                .rarity(RoomTemplate.Rarity.UNCOMMON).weight(4).masks(ANY)
                .draw(HallTemplates::flooded).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("survey_room").category(RoomTemplate.Category.ROOM)
                .rarity(RoomTemplate.Rarity.UNCOMMON).weight(3).lore(true).links(1, 2)
                .draw(HallTemplates::surveyRoom).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("archive_room").category(RoomTemplate.Category.ROOM)
                .rarity(RoomTemplate.Rarity.UNCOMMON).weight(3).lore(true).links(1, 2)
                .draw(HallTemplates::archiveRoom).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("eyes_room").category(RoomTemplate.Category.ROOM)
                .rarity(RoomTemplate.Rarity.RARE).weight(2).masks(ANY)
                .draw(HallTemplates::eyesRoom).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("strange_room").category(RoomTemplate.Category.ROOM)
                .rarity(RoomTemplate.Rarity.RARE).weight(4).masks(ANY)
                .draw(HallTemplates::strangeRoom).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("impossible_room").category(RoomTemplate.Category.ROOM)
                .rarity(RoomTemplate.Rarity.RARE).weight(2).masks(ANY)
                .draw(HallTemplates::impossibleRoom).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("anomaly_room").category(RoomTemplate.Category.ANOMALY)
                .rarity(RoomTemplate.Rarity.VERY_RARE).weight(1).masks(ANY)
                .draw(HallTemplates::anomalyRoom).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("anchor_room").category(RoomTemplate.Category.LANDMARK)
                .rarity(RoomTemplate.Rarity.RARE).weight(2).landmark(true).lore(true).masks(ANY)
                .draw(HallTemplates::anchorRoom).build());

        RoomTemplates.register(hall, new RoomTemplate.Builder()
                .id("star_room").category(RoomTemplate.Category.LANDMARK)
                .rarity(RoomTemplate.Rarity.VERY_RARE).weight(1).landmark(true).masks(ANY)
                .draw(HallTemplates::starRoom).build());
    }

    // ---------------------------------------------------------------- pieces

    /** A sealed box with doorways. Most templates start here. */
    private static Palette.Scheme seal(GenerationContext ctx, int height) {
        Palette.Scheme scheme = Palette.of(ctx.dimension(), ctx.seed());
        ctx.shell(scheme.wall(), scheme.floor(), height);
        return scheme;
    }

    /** Fills the box back in and cuts one corridor through it. */
    private static void carveCorridor(GenerationContext ctx, Palette.Scheme scheme, Direction.Axis axis, int height) {
        ctx.fill(1, 0, 1, 14, height - 1, 14, scheme.wall());
        if (axis == Direction.Axis.Z) {
            ctx.clear(7, 0, 0, 8, 2, 15);
        } else {
            ctx.clear(0, 0, 7, 15, 2, 8);
        }
        // Floor of the corridor reads better slightly different from the walls.
        if (axis == Direction.Axis.Z) {
            ctx.fill(7, 0, 0, 8, 0, 15, scheme.floor());
        } else {
            ctx.fill(0, 0, 7, 15, 0, 8, scheme.floor());
        }
    }

    /** Adds ceiling lights along a corridor. Sparse, and never enough. */
    private static void lights(GenerationContext ctx, Palette.Scheme scheme, Direction.Axis axis, int height) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(ctx.seed(), "lights"));
        for (int i = 3; i < 16; i += 5 + random.nextInt(3)) {
            int y = height - 1;
            if (axis == Direction.Axis.Z) {
                ctx.set(7, y, i, scheme.light());
            } else {
                ctx.set(i, y, 7, scheme.light());
            }
        }
    }

    /** A small sign identifying the corridor. The numbers are meaningless. */
    private static void sectionSign(GenerationContext ctx, Direction direction) {
        int section = (int) Math.floorMod(ctx.seed() >>> 16, 240) + 1;
        switch (direction) {
            case NORTH -> ctx.wallSign(9, 2, 1, Direction.SOUTH, "SECTION " + section, "", "", "");
            case SOUTH -> ctx.wallSign(9, 2, 14, Direction.NORTH, "SECTION " + section, "", "", "");
            case WEST -> ctx.wallSign(1, 2, 9, Direction.EAST, "SECTION " + section, "", "", "");
            default -> ctx.wallSign(14, 2, 9, Direction.WEST, "SECTION " + section, "", "", "");
        }
    }

    // ------------------------------------------------------------- templates

    private static void straight(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 5);
        Direction.Axis axis = ctx.hasLink(Direction.NORTH) ? Direction.Axis.Z : Direction.Axis.X;
        carveCorridor(ctx, scheme, axis, 5);
        lights(ctx, scheme, axis, 5);
        if (ctx.random().chance(0.4)) {
            sectionSign(ctx, ctx.hasLink(Direction.NORTH) ? Direction.NORTH : Direction.WEST);
        }
        if (ctx.random().chance(0.2)) {
            StructureBits.measurement(ctx, 2, 1, 12, 4, ctx.seed());
        }
    }

    private static void corner(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 5);
        ctx.fill(1, 0, 1, 14, 4, 14, scheme.wall());
        // Two corridor stubs that meet in the middle of the room.
        if (ctx.hasLink(Direction.NORTH)) {
            ctx.clear(7, 0, 0, 8, 2, 8);
        }
        if (ctx.hasLink(Direction.SOUTH)) {
            ctx.clear(7, 0, 8, 8, 2, 15);
        }
        if (ctx.hasLink(Direction.WEST)) {
            ctx.clear(0, 0, 7, 8, 2, 8);
        }
        if (ctx.hasLink(Direction.EAST)) {
            ctx.clear(8, 0, 7, 15, 2, 8);
        }
        ctx.fill(7, 0, 7, 8, 0, 8, scheme.floor());
        ctx.set(7, 4, 7, scheme.light());
        if (ctx.random().chance(0.25)) {
            ctx.set(12, 1, 12, Blocks.GRAVEL.getDefaultState());
        }
    }

    private static void tee(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 5);
        ctx.fill(1, 0, 1, 14, 4, 14, scheme.wall());
        if (ctx.hasLink(Direction.NORTH)) {
            ctx.clear(7, 0, 0, 8, 2, 8);
        }
        if (ctx.hasLink(Direction.SOUTH)) {
            ctx.clear(7, 0, 8, 8, 2, 15);
        }
        if (ctx.hasLink(Direction.WEST)) {
            ctx.clear(0, 0, 7, 8, 2, 8);
        }
        if (ctx.hasLink(Direction.EAST)) {
            ctx.clear(8, 0, 7, 15, 2, 8);
        }
        ctx.fill(7, 0, 7, 8, 0, 8, scheme.floor());
        ctx.set(7, 4, 7, scheme.light());
        sectionSign(ctx, Direction.NORTH);
    }

    private static void cross(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 6);
        ctx.fill(1, 0, 1, 14, 5, 14, scheme.wall());
        ctx.clear(7, 0, 0, 8, 3, 15);
        ctx.clear(0, 0, 7, 15, 3, 8);
        // A pillar in the middle, because a four-way junction this wide needs one,
        // and because it gives the player something to break line of sight behind.
        ctx.fill(7, 0, 7, 8, 3, 8, scheme.accent());
        ctx.set(7, 5, 7, scheme.light());
        ctx.set(8, 5, 8, scheme.light());
    }

    private static void deadEnd(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 5);
        Direction open = Direction.NORTH;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (ctx.hasLink(direction)) {
                open = direction;
                break;
            }
        }
        carveCorridor(ctx, scheme, open.getAxis(), 5);
        // Block off the far end so it reads as a dead end rather than a corridor.
        Direction far = open.getOpposite();
        switch (far) {
            case NORTH -> ctx.fill(6, 0, 0, 9, 3, 1, scheme.wall());
            case SOUTH -> ctx.fill(6, 0, 14, 9, 3, 15, scheme.wall());
            case WEST -> ctx.fill(0, 0, 6, 1, 3, 9, scheme.wall());
            default -> ctx.fill(14, 0, 6, 15, 3, 9, scheme.wall());
        }
        // Something was here. Usually nothing is said about what.
        double roll = ctx.random().nextDouble();
        if (roll < 0.25) {
            StructureBits.tally(ctx, 3, 1, 12, 5 + ctx.random().nextInt(40));
        } else if (roll < 0.5) {
            ctx.torch(3, 1, 12);
        } else if (roll < 0.7) {
            ctx.set(3, 1, 12, Blocks.BARREL.getDefaultState());
        } else if (roll < 0.85) {
            StructureBits.drawing(ctx, 13, 1, 12, Direction.WEST);
        }
        // One dead end in twenty has a door in the end wall. It is always closed.
        if (ctx.random().chance(0.05)) {
            ctx.door(7, 0, far == Direction.NORTH || far == Direction.SOUTH ? 1 : 7,
                    far.getOpposite(), false);
        }
    }

    private static void staircase(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 8);
        Direction.Axis axis = ctx.hasLink(Direction.NORTH) ? Direction.Axis.Z : Direction.Axis.X;
        carveCorridor(ctx, scheme, axis, 4);
        // Stairs up to a landing and back down again. The corridor stays at one
        // height so it always meets its neighbours; the stairs are a detour.
        for (int i = 0; i < 4; i++) {
            ctx.set(3, i + 1, 4 + i, Blocks.STONE_BRICK_STAIRS.getDefaultState()
                    .with(StairsBlock.FACING, Direction.SOUTH).with(StairsBlock.HALF, BlockHalf.BOTTOM));
        }
        ctx.fill(2, 4, 8, 5, 4, 11, scheme.accent());
        ctx.set(3, 5, 9, scheme.light());
        // Whatever is on the landing. It is never anything useful.
        double roll = ctx.random().nextDouble();
        if (roll < 0.3) {
            StructureBits.tally(ctx, 4, 5, 10, 200 + ctx.random().nextInt(300));
        } else if (roll < 0.6) {
            ctx.set(4, 5, 10, Blocks.CHEST.getDefaultState());
            LoreManager.placeInChest(ctx, 4, 5, 10, 1, ctx.seed());
        } else {
            StructureBits.chair(ctx, 4, 5, 10, Direction.NORTH);
        }
    }

    private static void smallRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 4);
        ctx.fill(1, 0, 1, 14, 3, 14, scheme.wall());
        ctx.clear(5, 0, 5, 10, 3, 10);
        ctx.fill(5, 0, 5, 10, 0, 10, scheme.floor());
        ctx.set(7, 3, 7, scheme.light());
        double roll = ctx.random().nextDouble();
        if (roll < 0.4) {
            StructureBits.table(ctx, 6, 1, 6, scheme.accent());
            StructureBits.chair(ctx, 6, 1, 8, Direction.NORTH);
        } else if (roll < 0.7) {
            ctx.set(6, 1, 6, Blocks.BARREL.getDefaultState());
            ctx.set(7, 1, 6, Blocks.BARREL.getDefaultState());
        } else {
            ctx.set(6, 1, 6, Blocks.CHEST.getDefaultState());
            if (ctx.template().canContainLore) {
                LoreManager.placeInChest(ctx, 6, 1, 6, 1, ctx.seed());
            }
        }
    }

    private static void largeRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 7);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        // Four pillars. The room is empty otherwise, which is what makes it feel
        // like somewhere things are kept rather than somewhere people go.
        for (int[] pillar : new int[][]{{3, 3}, {3, 12}, {12, 3}, {12, 12}}) {
            ctx.fill(pillar[0], 1, pillar[1], pillar[0] + 1, 5, pillar[1] + 1, scheme.accent());
        }
        ctx.set(7, 6, 7, scheme.light());
        ctx.set(7, 6, 8, scheme.light());
        if (ctx.random().chance(0.3)) {
            StructureBits.measurement(ctx, 5, 1, 13, 6, ctx.seed());
        }
        if (ctx.random().chance(0.15)) {
            StructureBits.eyeWall(ctx, 7, 2, 1, Direction.SOUTH, ctx.state().eyeSeed(ctx.world().getSeed()), 6);
        }
    }

    private static void flooded(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 5);
        ctx.fill(1, 0, 1, 14, 1, 14, Blocks.WATER.getDefaultState());
        // A walkway just above the water, so the room can be crossed.
        for (int i = 2; i < 14; i += 2) {
            ctx.set(i, 1, 7, Blocks.OAK_SLAB.getDefaultState()
                    .with(SlabBlock.TYPE, SlabType.TOP));
            ctx.set(i, 1, 8, Blocks.OAK_SLAB.getDefaultState()
                    .with(SlabBlock.TYPE, SlabType.TOP));
        }
        ctx.set(7, 4, 7, scheme.light());
        if (ctx.random().chance(0.3)) {
            ctx.set(12, 2, 12, Blocks.LILY_PAD.getDefaultState());
        }
    }

    private static void surveyRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 5);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        StructureBits.table(ctx, 6, 1, 6, scheme.accent());
        StructureBits.chair(ctx, 6, 1, 8, Direction.NORTH);
        StructureBits.measurement(ctx, 2, 1, 12, 5, ctx.seed());
        ctx.set(7, 4, 7, scheme.light());
        // The clipboard. Every survey room has one, and none of them agree.
        ctx.sign(7, 2, 6, "DRIFT", (ctx.seed() % 900) / 100 + "." + (ctx.seed() % 97) + "mm", "WITHIN TOLERANCE", "");
        if (ctx.template().canContainLore) {
            LoreManager.placeInChest(ctx, 12, 1, 12, 1, ctx.seed());
        }
    }

    private static void archiveRoom(GenerationContext ctx) {
        Palette.Scheme scheme = Palette.of(UncannyDimension.ARCHIVE, ctx.seed());
        seal(ctx, 6);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        StructureBits.shelfWall(ctx, 1, 1, 2, 13, 4, Direction.Axis.X);
        StructureBits.shelfWall(ctx, 1, 1, 13, 13, 4, Direction.Axis.X);
        ctx.clear(6, 0, 5, 9, 4, 10);
        StructureBits.table(ctx, 7, 1, 7, scheme.accent());
        ctx.set(7, 5, 7, scheme.light());
        if (ctx.template().canContainLore) {
            LoreManager.placeInChest(ctx, 12, 1, 7, 2, ctx.seed());
        }
        // A gap in the shelving. One shelf is empty and the dust says it always was.
        ctx.fill(4, 1, 2, 5, 4, 2, Blocks.AIR.getDefaultState());
    }

    private static void eyesRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 6);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        long eyeSeed = ctx.state().eyeSeed(ctx.world().getSeed());
        // The same arrangement on three walls. The player is not told to compare
        // them; they simply notice eventually.
        StructureBits.eyeWall(ctx, 8, 2, 1, Direction.SOUTH, eyeSeed, 9);
        StructureBits.eyeWall(ctx, 1, 2, 8, Direction.EAST, eyeSeed, 9);
        StructureBits.eyeWall(ctx, 14, 2, 8, Direction.WEST, eyeSeed, 9);
        StructureBits.eyeRing(ctx, 8, 5, 8, 5, eyeSeed, Direction.Axis.Y);
        ctx.set(7, 5, 7, scheme.light());
    }

    private static void strangeRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 6);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        // One room in the Hall is not a room in the Hall.
        double roll = ctx.random().nextDouble();
        if (roll < 0.34) {
            // A tree, underground, in a corridor, with no sky above it.
            ctx.fill(6, 1, 6, 9, 1, 9, Blocks.GRASS_BLOCK.getDefaultState());
            ctx.fill(7, 2, 7, 8, 4, 8, Blocks.OAK_LOG.getDefaultState());
            ctx.fill(5, 5, 5, 10, 5, 10, Blocks.OAK_LEAVES.getDefaultState());
        } else if (roll < 0.67) {
            // A room full of doors. None of them are in a wall.
            for (int x = 3; x < 13; x += 3) {
                for (int z = 3; z < 13; z += 3) {
                    ctx.door(x, 1, z, Direction.NORTH, ctx.random().nextBoolean());
                }
            }
        } else {
            // The ceiling is missing, and there is nothing above it either.
            ctx.fill(1, 5, 1, 14, 12, 14, Blocks.AIR.getDefaultState());
            ctx.set(7, 1, 7, Blocks.CRAFTING_TABLE.getDefaultState());
        }
    }

    private static void impossibleRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 9);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
        // A staircase that climbs through the ceiling and arrives back at the
        // floor. It is not a loop the player can walk, which is worse.
        for (int i = 0; i < 8; i++) {
            ctx.set(3 + (i % 4), 1 + i, 3 + i, Blocks.STONE_BRICK_STAIRS.getDefaultState()
                    .with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.HALF, BlockHalf.BOTTOM));
        }
        ctx.fill(10, 4, 10, 13, 4, 13, scheme.accent());
        ctx.fill(11, 0, 11, 12, 3, 12, Blocks.AIR.getDefaultState());
        // A window, in an interior room, showing the room the player is standing in.
        StructureBits.observationWindow(ctx, 14, 2, 7, Direction.WEST);
        ctx.set(7, 8, 7, scheme.light());
    }

    private static void anomalyRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 6);
        // Furnished like a bedroom. In a corridor. Underground. Recently.
        ctx.fill(1, 0, 1, 14, 0, 14, Blocks.OAK_PLANKS.getDefaultState());
        StructureBits.bedCorner(ctx, 3, 1, 3, Direction.SOUTH);
        StructureBits.table(ctx, 10, 1, 10, Blocks.OAK_PLANKS.getDefaultState());
        ctx.torch(11, 2, 10);
        ctx.set(6, 1, 12, Blocks.CRAFTING_TABLE.getDefaultState());
        ctx.set(8, 1, 12, Blocks.FURNACE.getDefaultState());
        // The bed is made. Something has been sleeping here, or has just left.
        ctx.sign(10, 2, 10, "BACK SOON", "", "", "");
        if (ctx.random().chance(0.5)) {
            ctx.set(7, 1, 7, scheme.light());
        }
    }

    private static void anchorRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 6);
        ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());

        UncannyWorldState state = ctx.state();
        int number = AnchorManager.numberFor(ctx.seed());
        String status = AnchorManager.status(ctx.seed(), number);

        // Only the primary chunk of a landmark draws the centrepiece. The others
        // draw the surrounding corridor so the landmark is approachable.
        if (!ctx.room().isLandmarkPrimary()) {
            ctx.fill(1, 0, 1, 14, 0, 14, scheme.floor());
            ctx.set(7, 5, 7, scheme.light());
            return;
        }

        // Furniture: a bed, a table, food, water, a journal, wall markings.
        StructureBits.bedCorner(ctx, 2, 1, 2, Direction.EAST);
        StructureBits.table(ctx, 8, 1, 3, scheme.accent());
        StructureBits.chair(ctx, 8, 1, 5, Direction.NORTH);
        // A cake, whole, on the table. Nobody has eaten any of it.
        ctx.set(11, 1, 3, Blocks.CAKE.getDefaultState());
        ctx.set(12, 1, 3, Blocks.WATER_CAULDRON.getDefaultState());
        ctx.set(7, 5, 7, scheme.light());

        // The observation window. It looks into nothing.
        StructureBits.observationWindow(ctx, 14, 2, 7, Direction.WEST);

        // The journal. This is what the player came for, whether they know it or not.
        ctx.chest(3, 1, 12, Direction.NORTH, chest -> {
            var stack = new net.minecraft.item.ItemStack(net.minecraft.items.Items.WRITTEN_BOOK);
            var nbt = stack.getOrCreateNbt();
            nbt.putString("title", AnchorManager.label(number));
            nbt.putString("author", "");
            var pages = new net.minecraft.nbt.NbtList();
            for (String page : AnchorManager.journal(ctx.seed(), number)) {
                pages.add(net.minecraft.nbt.NbtString.of(page));
            }
            nbt.put("pages", pages);
            chest.setStack(13, stack);
        });

        // Wall markings and a calendar.
        StructureBits.tally(ctx, 5, 1, 14, 100 + (int) Math.floorMod(ctx.seed(), 3000));
        StructureBits.calendar(ctx, 12, 1, 14, 300 + (int) Math.floorMod(ctx.seed() >>> 8, 2000), true);
        StructureBits.wallLines(ctx, 9, 2, 1, Direction.SOUTH, AnchorManager.wallText(ctx.seed(), number));

        // The plaque outside the door. Short, and always the same shape.
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (ctx.hasLink(direction)) {
                StructureBits.wallLines(ctx, 7, 3, switch (direction) {
                    case NORTH -> 1;
                    case SOUTH -> 14;
                    default -> 7;
                }, direction.getOpposite(), AnchorManager.plaque(number, status));
                break;
            }
        }

        // Remember that this world has an anchor with this number.
        state.claimAnchor(number);
    }

    private static void starRoom(GenerationContext ctx) {
        Palette.Scheme scheme = seal(ctx, 10);
        ctx.fill(1, 0, 1, 14, 0, 14, Blocks.BLACK_CONCRETE.getDefaultState());
        ctx.clear(3, 1, 3, 12, 8, 12);
        // Points of light in the dark. They are not stars, and the mod never says
        // so out loud. This room is where the player starts to suspect it.
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(ctx.seed(), "stars"));
        for (int i = 0; i < 40; i++) {
            int x = random.between(3, 12);
            int y = random.between(3, 8);
            int z = random.between(3, 12);
            int level = random.between(2, 12);
            ctx.set(x, y, z, UncannyBlocks.STARLIGHT.getDefaultState()
                    .with(UncannyBlocks.LEVEL, level));
        }
        // One aperture in the ceiling, so the room reads as an instrument.
        ctx.clear(7, 9, 7, 8, 10, 8);
        ctx.sign(4, 1, 4, "PLATE 7", "OBSERVATION", "DO NOT OPEN", "");
        BlockPos centre = ctx.pos(8, 1, 8);
        ctx.setBlock(centre, UncannyBlocks.SEAL_PLATE.getDefaultState());
    }
}
