package dev.uncanny.generation;

import dev.uncanny.builders.AbyssGenerator;
import dev.uncanny.builders.DeepGenerator;
import dev.uncanny.builders.HouseGenerator;
import dev.uncanny.builders.NatureGenerator;
import dev.uncanny.dimension.UncannyDimension;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The entry point for all generation, and the thing that keeps it cheap.
 *
 * Chunks are not filled in the moment they load. Instead they are put on a queue
 * and a few are processed per server tick. That has three benefits:
 *
 *   - no long pause when a player enters a layer for the first time;
 *   - no work at all for chunks that unload again before their turn comes up;
 *   - a hard cap on how much the mod can cost in one tick.
 *
 * The queue is bounded, so a client that flies around a lot cannot make it grow
 * without limit.
 */
public final class ProceduralDimensionGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger("uncanny/gen");

    /** How many chunks may be filled in during one server tick. */
    public static final int CHUNKS_PER_TICK = 2;

    /** Hard cap on the queue. Oldest entries are dropped past this point. */
    public static final int MAX_QUEUED = 512;

    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED_KEYS = new HashSet<>();

    /** A chunk waiting to be filled. */
    private record Pending(String dimensionPath, int chunkX, int chunkZ) {
        long key() {
            // Includes the dimension so the same coordinates in two layers do not
            // cancel each other out.
            return ChunkPos.toLong(this.chunkX, this.chunkZ) ^ (this.dimensionPath.hashCode() * 0x9E3779B9L);
        }
    }

    private ProceduralDimensionGenerator() {
    }

    /** Called from the chunk load event. Does almost nothing. */
    public static void onChunkLoaded(ServerWorld world, WorldChunk chunk) {
        UncannyDimension dimension = UncannyDimension.fromPath(world.getRegistryKey().getValue().getPath());
        if (dimension == null || dimension.mode() == UncannyDimension.Mode.NONE) {
            return;
        }
        Pending pending = new Pending(dimension.path(), chunk.getPos().x, chunk.getPos().z);
        if (QUEUED_KEYS.add(pending.key())) {
            QUEUE.addLast(pending);
            while (QUEUE.size() > MAX_QUEUED) {
                Pending dropped = QUEUE.pollFirst();
                if (dropped != null) {
                    QUEUED_KEYS.remove(dropped.key());
                }
            }
        }
    }

    /** Called every server tick. Fills at most CHUNKS_PER_TICK chunks. */
    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        for (int i = 0; i < CHUNKS_PER_TICK && !QUEUE.isEmpty(); i++) {
            Pending pending = QUEUE.pollFirst();
            if (pending == null) {
                return;
            }
            QUEUED_KEYS.remove(pending.key());

            UncannyDimension dimension = UncannyDimension.fromPath(pending.dimensionPath());
            if (dimension == null) {
                continue;
            }
            ServerWorld world = server.getWorld(dimension.key());
            if (world == null) {
                continue;
            }
            ChunkPos pos = new ChunkPos(pending.chunkX(), pending.chunkZ());
            // The chunk may have unloaded while it waited. Dropping it is correct:
            // it will be queued again if the player ever comes back.
            if (!world.isChunkLoaded(pos.x, pos.z)) {
                continue;
            }
            generate(server, world, dimension, pos);
        }
    }

    /** Fills one chunk immediately, using the strategy the layer asks for. */
    public static void generate(MinecraftServer server, ServerWorld world, UncannyDimension dimension, ChunkPos pos) {
        try {
            switch (dimension.mode()) {
                case MODULE -> RoomGenerator.generateChunk(server, world, dimension, pos);
                case PATCHWORK -> RoomGenerator.generateChunk(server, world, dimension, pos);
                case CARVE -> DeepGenerator.generateChunk(server, world, dimension, pos);
                case ECHO -> HouseGenerator.generateChunk(server, world, dimension, pos);
                case NATURE -> NatureGenerator.generateChunk(server, world, dimension, pos);
                case VOID -> AbyssGenerator.generateChunk(server, world, dimension, pos);
                case NONE -> {
                    // The Overworld is left alone. Deliberately.
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("[uncanny] generation failed for {} {}", dimension.path(), pos, e);
        }
    }

    /** Clears the queue. Called when a server stops so nothing leaks between worlds. */
    public static void reset() {
        QUEUE.clear();
        QUEUED_KEYS.clear();
    }

    /** Queue length, for the debug readout. */
    public static int queued() {
        return QUEUE.size();
    }
}
