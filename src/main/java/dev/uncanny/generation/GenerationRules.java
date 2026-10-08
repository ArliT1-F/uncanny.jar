package dev.uncanny.generation;

import dev.uncanny.config.UncannyConfig;
import dev.uncanny.util.SeedUtil;
import net.minecraft.util.math.Direction;

/**
 * The rules that decide which chunks are connected to which.
 *
 * This is what stops a procedural dimension from being a pile of disconnected
 * corridors. It works with no saved state at all, purely from coordinates:
 *
 *   1. Every chunk except the origin picks exactly one PARENT, and the parent is
 *      always west or north. Following parents always walks towards the origin,
 *      so the whole grid is one connected tree and there are no loops in it.
 *
 *   2. A few extra links are added on top of the tree, which is where junctions,
 *      cross corridors and shortcuts come from. Extra links are computed from the
 *      pair of chunks they join, so both chunks always agree about whether the
 *      link exists - they do not need to talk to each other.
 *
 *   3. Dead ends fall out for free: a chunk with one link and no children is a
 *      dead end. They are not placed on purpose, they just happen.
 *
 * Because nothing here reads the world, two chunks can be generated at the same
 * time in any order and still fit together.
 */
public final class GenerationRules {

    /** Link bits. A chunk's connection pattern is one int: NORTH|EAST and so on. */
    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 4;
    public static final int WEST = 8;

    /** Chance that a tree edge is doubled up by an extra loop link. */
    public static final double EXTRA_LINK_CHANCE = 0.11;

    /** Landmarks are placed on a coarse grid so they are never next to each other. */
    public static final int LANDMARK_CELL = 4;

    private GenerationRules() {
    }

    // --------------------------------------------------------------- the tree

    /**
     * The parent direction of a chunk, or null at the origin.
     *
     * Only WEST and NORTH are ever candidates, which is what guarantees that the
     * tree has no cycles: you can never walk parents into a loop because every
     * step strictly decreases x or z.
     */
    public static Direction treeParent(long dimensionSeed, int chunkX, int chunkZ) {
        if (chunkX == 0 && chunkZ == 0) {
            return null;
        }
        boolean canWest = chunkX > 0;
        boolean canNorth = chunkZ > 0;
        if (canWest && !canNorth) {
            return Direction.WEST;
        }
        if (canNorth && !canWest) {
            return Direction.NORTH;
        }
        long roll = SeedUtil.mix(dimensionSeed, chunkX, chunkZ, 0x7A1EL);
        return (roll & 1L) == 0L ? Direction.WEST : Direction.NORTH;
    }

    /** True when the chunk on the far side of this edge chose us as its parent. */
    private static boolean isChildLink(long dimensionSeed, int chunkX, int chunkZ, Direction direction) {
        int nx = chunkX + direction.getOffsetX();
        int nz = chunkZ + direction.getOffsetZ();
        Direction parent = treeParent(dimensionSeed, nx, nz);
        return parent != null && parent == direction.getOpposite();
    }

    /** True when our own parent is in this direction. */
    private static boolean isParentLink(long dimensionSeed, int chunkX, int chunkZ, Direction direction) {
        return treeParent(dimensionSeed, chunkX, chunkZ) == direction;
    }

    /**
     * Extra links are symmetric by construction: both chunks compute the hash of
     * the same canonical pair (the more westerly/northerly chunk first), so they
     * cannot disagree.
     */
    private static boolean extraLink(long dimensionSeed, int ax, int az, int bx, int bz) {
        int lowX = Math.min(ax, bx);
        int lowZ = Math.min(az, bz);
        int highX = Math.max(ax, bx);
        int highZ = Math.max(az, bz);
        long roll = SeedUtil.mix(dimensionSeed, lowX, lowZ, highX, highZ, 0xE17AL);
        double chance = EXTRA_LINK_CHANCE * UncannyConfig.get().anomalyFrequency;
        return ((roll >>> 11) % 10000) / 10000.0 < chance;
    }

    private static boolean hasExtraLink(long dimensionSeed, int chunkX, int chunkZ, Direction direction) {
        return switch (direction) {
            case EAST -> extraLink(dimensionSeed, chunkX, chunkZ, chunkX + 1, chunkZ);
            case SOUTH -> extraLink(dimensionSeed, chunkX, chunkZ, chunkX, chunkZ + 1);
            case WEST -> extraLink(dimensionSeed, chunkX - 1, chunkZ, chunkX, chunkZ);
            case NORTH -> extraLink(dimensionSeed, chunkX, chunkZ - 1, chunkX, chunkZ);
            default -> false;
        };
    }

    /**
     * The full connection pattern of one chunk: NORTH|EAST|SOUTH|WEST bits set.
     *
     * The same inputs always give the same answer, on any machine, without saving
     * anything. That is the whole trick.
     */
    public static int linkMask(long dimensionSeed, int chunkX, int chunkZ) {
        int mask = 0;
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            int bit = bitOf(direction);
            if (isParentLink(dimensionSeed, chunkX, chunkZ, direction)
                    || isChildLink(dimensionSeed, chunkX, chunkZ, direction)
                    || hasExtraLink(dimensionSeed, chunkX, chunkZ, direction)) {
                mask |= bit;
            }
        }
        return mask;
    }

    public static int bitOf(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    public static Direction directionOf(int bit) {
        return switch (bit) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    public static int linkCount(int mask) {
        return Integer.bitCount(mask);
    }

    /** True when the two links are on opposite sides (a straight run). */
    public static boolean isStraight(int mask) {
        return mask == (NORTH | SOUTH) || mask == (EAST | WEST);
    }

    /** True when there are exactly two links and they are not opposite (a corner). */
    public static boolean isCorner(int mask) {
        return linkCount(mask) == 2 && !isStraight(mask);
    }

    // ------------------------------------------------------------- landmarks

    /**
     * Landmark cells are decided on a coarse grid, so a landmark never touches
     * another landmark, and the decision only depends on which cell you are in.
     *
     * @return the cell origin in chunk coordinates, or null if this is not a
     *         landmark cell.
     */
    public static long landmarkCell(long dimensionSeed, int chunkX, int chunkZ, double chance) {
        // Negative coordinates must floor rather than truncate, or the grid would
        // have a visible seam along x=0 and z=0.
        int cellX = Math.floorDiv(chunkX, LANDMARK_CELL);
        int cellZ = Math.floorDiv(chunkZ, LANDMARK_CELL);
        long roll = SeedUtil.mix(dimensionSeed, cellX, cellZ, 0x1ACEL);
        double rollValue = ((roll >>> 11) % 100000) / 100000.0;
        if (rollValue > chance) {
            return -1;
        }
        // The landmark sits in the middle of its cell rather than on the corner,
        // which gives it corridors on all sides.
        int originX = cellX * LANDMARK_CELL + 1;
        int originZ = cellZ * LANDMARK_CELL + 1;
        return ((long) originX << 32) | (originZ & 0xFFFFFFFFL);
    }

    public static int landmarkX(long packed) {
        return (int) (packed >> 32);
    }

    public static int landmarkZ(long packed) {
        return (int) packed;
    }

    /** How many chunks wide/tall a landmark occupies. */
    public static final int LANDMARK_SIZE = 2;

    /** True when this chunk is inside a landmark cell. */
    public static boolean inLandmark(long dimensionSeed, int chunkX, int chunkZ, double chance) {
        return landmarkCell(dimensionSeed, chunkX, chunkZ, chance) >= 0;
    }
}
