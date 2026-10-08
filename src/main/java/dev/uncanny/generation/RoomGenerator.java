package dev.uncanny.generation;

import dev.uncanny.config.UncannyConfig;
import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a room for a chunk and builds it.
 *
 * The decision tree, in order:
 *
 *   1. Is this chunk inside a landmark cell? Landmarks win, because they are the
 *      places the player is meant to remember.
 *   2. Otherwise, which templates could physically sit here? That is decided by
 *      the chunk's connection pattern, not by taste.
 *   3. Of those, which pass their rarity roll this time?
 *   4. Of those, pick one at random using the configured weights.
 *
 * Every step is a pure function of the seed and the coordinates, so the same
 * chunk always produces the same room and nothing has to be remembered about the
 * room itself - only the fact that it was built.
 */
public final class RoomGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger("uncanny/gen");

    private RoomGenerator() {
    }

    /**
     * Generates one chunk of a module-built layer.
     *
     * Safe to call repeatedly: once a chunk is marked as generated it is skipped.
     */
    public static void generateChunk(MinecraftServer server, ServerWorld world, UncannyDimension dimension,
                                     ChunkPos chunk) {
        UncannyWorldState state = UncannyWorldState.get(server);
        if (state.isChunkGenerated(dimension.path(), chunk)) {
            return;
        }

        long worldSeed = world.getSeed();
        long dimensionSeed = DimensionManager.seedOf(dimension, server);
        long chunkSeed = SeedUtil.forChunk(worldSeed, dimensionSeed, chunk.x, chunk.z);

        Room room = chooseRoom(world, dimension, chunk, chunkSeed, dimensionSeed, state);
        if (room == null) {
            return;
        }

        GenerationContext context = new GenerationContext(world, server, state, dimension, room, chunkSeed);
        try {
            room.template.builder.build(context);
            // A room that cannot be entered is a bug, not an anomaly. Cut the
            // access paths unless the template insists on drawing its own.
            if (!room.template.ensuresOwnAccess) {
                context.ensureAccessible();
            }
        } catch (RuntimeException e) {
            // A broken template must never take the server down. The chunk is
            // still marked, so the failure is visible as a gap rather than a crash.
            LOGGER.error("[uncanny] template {} failed in {} at {}", room.template.id, dimension.path(), chunk, e);
        }

        state.markChunkGenerated(dimension.path(), chunk);
        state.markDirty();

        if (UncannyConfig.get().debugLogging) {
            LOGGER.info("[uncanny] {} {} -> {} (links {}, {} blocks)", dimension.path(), chunk,
                    room.template.id, Integer.bitCount(room.linkMask), context.writes());
        }
    }

    /** Chooses the room for a chunk without building it. Useful for debugging. */
    public static Room chooseRoom(ServerWorld world, UncannyDimension dimension, ChunkPos chunk,
                                  long chunkSeed, long dimensionSeed, UncannyWorldState state) {
        UncannyConfig config = UncannyConfig.get();
        int linkMask = GenerationRules.linkMask(dimensionSeed, chunk.x, chunk.z);

        // A fully enclosed chunk is rare and deliberate: it is a sealed room.
        if (linkMask == 0) {
            linkMask = GenerationRules.NORTH;
        }

        long landmarkCell = GenerationRules.landmarkCell(dimensionSeed, chunk.x, chunk.z, config.landmarkChance);
        int offsetX = 0;
        int offsetZ = 0;

        List<RoomTemplate> library = RoomTemplates.forDimension(dimension);
        if (library.isEmpty()) {
            LOGGER.warn("[uncanny] no templates registered for {}", dimension.path());
            return null;
        }

        RoomTemplate chosen;
        if (landmarkCell >= 0) {
            offsetX = chunk.x - GenerationRules.landmarkX(landmarkCell);
            offsetZ = chunk.z - GenerationRules.landmarkZ(landmarkCell);
            boolean inside = offsetX >= 0 && offsetX < GenerationRules.LANDMARK_SIZE
                    && offsetZ >= 0 && offsetZ < GenerationRules.LANDMARK_SIZE;
            if (inside) {
                chosen = pickLandmark(library, chunkSeed, linkMask);
                if (chosen == null) {
                    chosen = pickNormal(library, chunkSeed, linkMask, config, dimension);
                }
            } else {
                chosen = pickNormal(library, chunkSeed, linkMask, config, dimension);
            }
        } else {
            chosen = pickNormal(library, chunkSeed, linkMask, config, dimension);
        }

        BlockPos origin = new BlockPos(chunk.getStartX(), dimension.connectorY(), chunk.getStartZ());
        return new Room(chosen, chunk, origin, linkMask, chunkSeed, offsetX, offsetZ);
    }

    /** Landmarks are chosen from the landmark subset, and only the primary chunk
     *  draws the centrepiece. The other chunks draw filler corridors. */
    private static RoomTemplate pickLandmark(List<RoomTemplate> library, long chunkSeed, int linkMask) {
        List<RoomTemplate> landmarks = new ArrayList<>();
        for (RoomTemplate template : library) {
            if (template.isLandmark && template.accepts(linkMask)) {
                landmarks.add(template);
            }
        }
        if (landmarks.isEmpty()) {
            for (RoomTemplate template : library) {
                if (template.isLandmark) {
                    landmarks.add(template);
                }
            }
        }
        if (landmarks.isEmpty()) {
            return null;
        }
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(chunkSeed, "landmark"));
        return random.pick(landmarks);
    }

    /**
     * Weighted pick from everything that fits.
     *
     * Rarity works by zeroing a template's weight when its roll fails, so rare
     * rooms stay rare without ever being impossible, and common rooms are always
     * available as a fallback.
     */
    private static RoomTemplate pickNormal(List<RoomTemplate> library, long chunkSeed, int linkMask,
                                           UncannyConfig config, UncannyDimension dimension) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(chunkSeed, "template"));

        List<RoomTemplate> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        RoomTemplate fallback = null;

        for (RoomTemplate template : library) {
            if (template.isLandmark || !template.accepts(linkMask)) {
                continue;
            }
            if (fallback == null && template.rarity == RoomTemplate.Rarity.COMMON) {
                fallback = template;
            }
            double weight = config.weightFor(template.id, template.baseWeight);
            if (weight <= 0) {
                continue;
            }
            // Rare templates only get a chance on the chunks that roll for them.
            if (template.rarity != RoomTemplate.Rarity.COMMON && !random.chance(template.rarity.roll())) {
                continue;
            }
            // Anomalies are gated a second time, globally, so one config value can
            // calm the whole mod down.
            if (template.category == RoomTemplate.Category.ANOMALY && !dimension.anomaliesAllowed()) {
                continue;
            }
            candidates.add(template);
            weights.add(weight);
        }

        if (candidates.isEmpty()) {
            return fallback != null ? fallback : library.get(0);
        }

        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }
}
