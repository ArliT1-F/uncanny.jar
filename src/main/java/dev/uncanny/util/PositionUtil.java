package dev.uncanny.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;

/**
 * Small position helpers. Mostly here so the generators read cleanly.
 */
public final class PositionUtil {

    /** Set the block only if the chunk is loaded, and do not trigger neighbour updates. */
    public static final int QUIET = Block.NOTIFY_LISTENERS;

    private PositionUtil() {
    }

    /** The corner of the chunk that contains pos (the block at local 0,0,0). */
    public static BlockPos chunkOrigin(BlockPos pos) {
        return new BlockPos(pos.getX() & ~15, pos.getY(), pos.getZ() & ~15);
    }

    public static BlockPos chunkOrigin(ChunkPos pos, int y) {
        return new BlockPos(pos.getStartX(), y, pos.getStartZ());
    }

    /** True when both positions are inside the same 16x16 column of chunks. */
    public static boolean sameChunk(BlockPos a, BlockPos b) {
        return (a.getX() >> 4) == (b.getX() >> 4) && (a.getZ() >> 4) == (b.getZ() >> 4);
    }

    /** The chunk coordinate for a block coordinate. */
    public static int toChunk(int block) {
        return block >> 4;
    }

    /** Local 0..15 coordinate inside a chunk. */
    public static int local(int block) {
        return block & 15;
    }

    /** The neighbour chunk in a direction. */
    public static ChunkPos neighbor(ChunkPos pos, Direction direction) {
        return new ChunkPos(pos.x + direction.getOffsetX(), pos.z + direction.getOffsetZ());
    }

    /**
     * Packs a chunk position into one long so it can live in a long[] in NBT.
     * Uses the same encoding Minecraft uses internally for chunk positions.
     */
    public static long pack(ChunkPos pos) {
        return ChunkPos.toLong(pos.x, pos.z);
    }

    public static ChunkPos unpack(long packed) {
        return new ChunkPos(packed);
    }

    /** Writes a block, but never crashes if the chunk happens to be gone. */
    public static boolean setQuietly(ServerWorld world, BlockPos pos, BlockState state) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        return world.setBlockState(pos, state, QUIET);
    }

    /** Removes a block without dropping anything (anomalies must not give out items). */
    public static boolean clearQuietly(ServerWorld world, BlockPos pos) {
        BlockState current = world.getBlockState(pos);
        if (current.isAir()) {
            return false;
        }
        return world.setBlockState(pos, Blocks.AIR.getDefaultState(), QUIET);
    }

    /** Manhattan distance, which is what the corridor network cares about. */
    public static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    /** Squared distance, for cheap radius checks. */
    public static double distanceSquared(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
