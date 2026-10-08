package dev.uncanny.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Deterministic seed mixing.
 *
 * Everything the mod generates is a pure function of a handful of longs:
 *
 *     world seed + dimension seed + coordinates  ->  one long  ->  everything else
 *
 * Because the same inputs always produce the same output, a Hall corridor looks
 * identical after a restart, after a chunk reload, and on another player's
 * machine, without anything being stored on disk.
 *
 * This is the single most important file for "procedural but stable".
 */
public final class SeedUtil {

    /** A fixed odd constant, mixed into every hash so that seed 0 is not special. */
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private SeedUtil() {
    }

    /**
     * SplitMix64 finaliser. Cheap, and it spreads nearby inputs far apart, which
     * matters a lot: chunk (0,0) and chunk (0,1) must not look related.
     */
    public static long mix(long... values) {
        long h = GOLDEN;
        for (long v : values) {
            h ^= v + GOLDEN + (h << 6) + (h >>> 2);
            h ^= h >>> 33;
            h *= 0xFF51AFD7ED558CCDL;
            h ^= h >>> 33;
            h *= 0xC4CEB9FE1A85EC53L;
            h ^= h >>> 33;
        }
        return h;
    }

    /** Mixes in one more value without starting over (used for per-feature rolls). */
    public static long derive(long base, long salt) {
        return mix(base, salt);
    }

    /** Mixes in a readable string salt. Slower than {@link #derive(long, long)} but clearer. */
    public static long derive(long base, String salt) {
        return mix(base, salt.hashCode(), 0x5DEECE66DL);
    }

    /** The seed that drives one chunk of one dimension. */
    public static long forChunk(long worldSeed, long dimensionSeed, int chunkX, int chunkZ) {
        return mix(worldSeed, dimensionSeed, chunkX, chunkZ);
    }

    /** Convenience overload taking a ChunkPos. */
    public static long forChunk(long worldSeed, long dimensionSeed, ChunkPos pos) {
        return forChunk(worldSeed, dimensionSeed, pos.x, pos.z);
    }

    /** A seed for a specific block position (used for small, local anomalies). */
    public static long forBlock(long base, BlockPos pos) {
        return mix(base, pos.getX(), pos.getY(), pos.getZ());
    }

    /** A stable, non-cryptographic id for a player uuid string. */
    public static long forString(String value) {
        long h = 1125899906842597L;
        for (int i = 0; i < value.length(); i++) {
            h = 31 * h + value.charAt(i);
        }
        return mix(h, value.length());
    }
}
