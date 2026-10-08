package dev.uncanny.builders;

import dev.uncanny.generation.GenerationContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * Reusable pieces of furniture and detail.
 *
 * Room templates are mostly made of these. Keeping them in one file means an
 * anchor room in the Hall and an anchor room in the Partition look like they were
 * built by the same people - which, in the story, they were.
 *
 * None of this is decorative filler: each piece is something the player is meant
 * to be able to reason about. A chair implies a sitter. Tally marks imply days.
 * An observation window implies an observer.
 */
public final class StructureBits {

    private StructureBits() {
    }

    /** A table: one block with a slab or trapdoor on top. */
    public static void table(GenerationContext ctx, int x, int y, int z, BlockState material) {
        ctx.set(x, y, z, material);
        ctx.set(x + 1, y, z, material);
        ctx.set(x, y + 1, z, Blocks.OAK_TRAPDOOR.getDefaultState());
        ctx.set(x + 1, y + 1, z, Blocks.OAK_TRAPDOOR.getDefaultState());
    }

    /** A chair made of stairs. Faces the table if one is given. */
    public static void chair(GenerationContext ctx, int x, int y, int z, Direction facing) {
        ctx.set(x, y, z, Blocks.OAK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, facing)
                .with(StairsBlock.HALF, BlockHalf.BOTTOM));
    }

    /** A bookshelf wall, the basic unit of the Archive. */
    public static void shelfWall(GenerationContext ctx, int x, int y, int z, int length, int height,
                                 Direction.Axis along) {
        for (int i = 0; i < length; i++) {
            for (int row = 0; row < height; row++) {
                if (along == Direction.Axis.X) {
                    ctx.set(x + i, y + row, z, Blocks.BOOKSHELF.getDefaultState());
                } else {
                    ctx.set(x, y + row, z + i, Blocks.BOOKSHELF.getDefaultState());
                }
            }
        }
    }

    /**
     * Tally marks.
     *
     * A sign with vertical strokes. The count matters: a room with forty marks is
     * a different room from a room with four hundred.
     */
    public static void tally(GenerationContext ctx, int x, int y, int z, int count) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < Math.min(count, 20); i++) {
            line.append('|');
        }
        ctx.sign(x, y, z, line.toString(), (count / 20) + " SETS", "", "");
    }

    /** A window that should not be underground, or should not look out on anything. */
    public static void observationWindow(GenerationContext ctx, int x, int y, int z, Direction facing) {
        BlockPos pos = ctx.pos(x, y, z);
        ctx.setBlock(pos, Blocks.IRON_BARS.getDefaultState());
        ctx.setBlock(pos.up(), Blocks.IRON_BARS.getDefaultState());
        // Behind the bars: nothing. That is the point.
        ctx.setBlock(pos.offset(facing), Blocks.BLACK_CONCRETE.getDefaultState());
        ctx.setBlock(pos.offset(facing).up(), Blocks.BLACK_CONCRETE.getDefaultState());
    }

    /** A bed with something on it. Used by anchor rooms. */
    public static void bedCorner(GenerationContext ctx, int x, int y, int z, Direction facing) {
        ctx.bed(x, y, z, facing);
        ctx.set(x, y, z + (facing == Direction.NORTH ? -1 : 1), Blocks.CHEST.getDefaultState());
    }

    /** A calendar. Always wrong by a little. */
    public static void calendar(GenerationContext ctx, int x, int y, int z, int day, boolean wrong) {
        ctx.sign(x, y, z,
                "DAY " + day,
                wrong ? "DAY " + (day + 1) : "",
                wrong ? "(CROSSED OUT)" : "",
                "");
    }

    /** Crude drawings on a wall: a circle, a ring, and a smaller ring inside it. */
    public static void drawing(GenerationContext ctx, int x, int y, int z, Direction facing) {
        // Paintings are entities and cannot be placed reliably during generation,
        // so the drawing is built out of blocks instead. It reads better anyway:
        // a picture you can walk up to and touch is a picture someone made here.
        BlockState ink = Blocks.BLACK_CONCRETE.getDefaultState();
        BlockPos base = ctx.pos(x, y, z);
        int[][] ring = {{0, 1}, {1, 0}, {1, 2}, {2, 1}, {0, 0}, {0, 2}, {2, 0}, {2, 2}};
        for (int[] offset : ring) {
            ctx.setBlock(base.up(offset[1]).offset(facing.getOpposite(), 0).add(offset[0] - 1, 0, 0), ink);
        }
        ctx.setBlock(base.up(1), Blocks.YELLOW_CONCRETE.getDefaultState());
    }

    /** A fossil. The Deep has them, and they are never the right shape. */
    public static void fossil(GenerationContext ctx, int x, int y, int z, long seed) {
        ctx.set(x, y, z, Blocks.BONE_BLOCK.getDefaultState());
        ctx.set(x + 1, y, z, Blocks.BONE_BLOCK.getDefaultState());
        ctx.set(x, y, z + 1, Blocks.BONE_BLOCK.getDefaultState());
        if ((seed & 1L) == 0L) {
            // One in two has too many of something.
            ctx.set(x + 1, y, z + 1, Blocks.BONE_BLOCK.getDefaultState());
            ctx.set(x + 2, y, z, Blocks.BONE_BLOCK.getDefaultState());
        }
    }

    /**
     * A measurement: a line of blocks with a sign at the end.
     *
     * Surveyors measured things. The measurements are always slightly off, which
     * the player cannot verify without a lot of effort, and which is therefore
     * much more unsettling than being obviously wrong.
     */
    public static void measurement(GenerationContext ctx, int x, int y, int z, int length, long seed) {
        for (int i = 0; i < length; i++) {
            ctx.set(x + i, y, z, Blocks.WHITE_CONCRETE.getDefaultState());
        }
        ctx.sign(x + length, y, z, (length * 100 + (int) (seed % 97)) + "mm", "", "", "");
    }

    /** A doorway with a number on it. Anchor rooms use this. */
    public static void numberedDoor(GenerationContext ctx, int x, int y, int z, Direction facing,
                                    int number, boolean open) {
        ctx.door(x, y, z, facing, open);
        ctx.wallSign(x, y + 2, z, facing, "ANCHOR " + String.format("%03d", number), "", "", "");
    }

    /**
     * A wall of small holes.
     *
     * The eye motif, at its smallest scale. On its own this is just texture. The
     * horror is in noticing that the same arrangement appears in a corridor, in a
     * library, and in a chamber a thousand blocks from either.
     */
    public static void eyeWall(GenerationContext ctx, int x, int y, int z, Direction facing, long seed, int count) {
        BlockPos base = ctx.pos(x, y, z);
        for (int i = 0; i < count; i++) {
            long roll = seed + i * 7919L;
            int dx = (int) Math.floorMod(roll, 5) - 2;
            int dy = (int) Math.floorMod(roll >>> 8, 4);
            BlockPos hole = base.up(dy).add(dx, 0, 0);
            ctx.setBlock(hole, Blocks.AIR.getDefaultState());
            // A dark block behind the hole gives it depth without a light check.
            ctx.setBlock(hole.offset(facing), Blocks.BLACK_CONCRETE.getDefaultState());
        }
    }

    /**
     * A ring of eye-like openings.
     *
     * The Ezekiel motif reduced to architecture: a circle, with holes in it, that
     * is clearly load-bearing and clearly not a machine. The mod never animates
     * this. It does not need to. The player does the rest.
     */
    public static void eyeRing(GenerationContext ctx, int centerX, int centerY, int centerZ,
                               int radius, long seed, Direction.Axis axis) {
        int eyes = 0;
        for (int angle = 0; angle < 360; angle += 15) {
            double radians = Math.toRadians(angle);
            int offsetA = (int) Math.round(Math.cos(radians) * radius);
            int offsetB = (int) Math.round(Math.sin(radians) * radius);
            BlockPos pos;
            if (axis == Direction.Axis.Y) {
                pos = ctx.pos(centerX + offsetA, centerY, centerZ + offsetB);
            } else if (axis == Direction.Axis.X) {
                pos = ctx.pos(centerX, centerY + offsetA, centerZ + offsetB);
            } else {
                pos = ctx.pos(centerX + offsetA, centerY + offsetB, centerZ);
            }
            ctx.setBlock(pos, Blocks.AIR.getDefaultState());
            ctx.setBlock(pos.up(), Blocks.SMOOTH_QUARTZ.getDefaultState());
            eyes++;
            // Every few openings gets a darker rim, which is what makes the ring
            // look like it is full of eyes rather than full of holes.
            if ((seed + angle) % 3 == 0) {
                ctx.setBlock(pos.down(), Blocks.BLACK_CONCRETE.getDefaultState());
            }
        }
        if (seed % 97 == 0) {
            // Occasionally the ring has one opening too many.
            ctx.setBlock(ctx.pos(centerX, centerY + radius, centerZ), Blocks.AIR.getDefaultState());
        }
    }

    /** Writes a list of short lines onto a wall as separate signs. */
    public static void wallLines(GenerationContext ctx, int x, int y, int z, Direction facing, List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            ctx.wallSign(x, y + i, z, facing, lines.get(i), "", "", "");
        }
    }
}
