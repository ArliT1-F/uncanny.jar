package dev.uncanny.dimension;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.player.PlayerProgress;
import dev.uncanny.player.UncannyPlayerData;
import dev.uncanny.util.SeedUtil;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Getting in and out of the layers.
 *
 * Two jobs:
 *   1. work out the seed a layer should use, from the world seed, so every layer
 *      in a world is different from every other layer but stable across restarts;
 *   2. move players between layers without dropping them into walls.
 *
 * The dimensions themselves are data-driven. Nothing is registered in code, which
 * means a datapack can retune them (or remove one) without a code change.
 */
public final class DimensionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("uncanny/dimension");

    /** path -> seed, filled in when the server starts. */
    private static final Map<String, Long> SEEDS = new HashMap<>();

    /**
     * True while the layers have not been seeded yet.
     *
     * Set when a seeding attempt happens before the worlds exist, so the work can
     * be picked up on the next tick instead of being lost (or thrown away as an
     * exception during startup).
     */
    private static boolean pending = true;

    /** Guards the "no world yet" warning so it cannot spam the log, once per server. */
    private static boolean warnedNoWorld = false;

    private DimensionManager() {
    }

    /**
     * Works out the seed for every layer.
     *
     * Called from SERVER_STARTED, which is the first event that can see a world:
     * the overworld is created inside setupServer(), so anything that fires before
     * it (SERVER_STARTING, for one) still has {@code getOverworld() == null}, and
     * asking that for a seed is what used to take the whole server down.
     *
     * It is safe to call from anywhere, including too early: if there is no
     * overworld yet the seeding is deferred to the next {@link #tick}.
     */
    public static void initialise(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            pending = true;
            if (!warnedNoWorld) {
                warnedNoWorld = true;
                LOGGER.warn("[uncanny] no overworld yet, layer seeds deferred to the first tick");
            }
            return;
        }
        seedAll(overworld.getSeed());
    }

    /**
     * Finishes a deferred seeding. Called from the server tick, before anything
     * that asks for a seed, and does nothing at all once the seeds are in place.
     */
    public static void tick(MinecraftServer server) {
        if (pending) {
            initialise(server);
        }
    }

    /** Forgets the seeds. Called when the server stops, so nothing leaks between worlds. */
    public static void reset() {
        SEEDS.clear();
        pending = true;
        warnedNoWorld = false;
    }

    private static void seedAll(long worldSeed) {
        SEEDS.clear();
        for (UncannyDimension dimension : UncannyDimension.values()) {
            if (dimension == UncannyDimension.OVERWORLD) {
                SEEDS.put(dimension.path(), worldSeed);
            } else {
                SEEDS.put(dimension.path(), SeedUtil.derive(worldSeed, "dim:" + dimension.path()));
            }
        }
        pending = false;
        LOGGER.debug("[uncanny] seeded {} layers from world seed {}", SEEDS.size(), worldSeed);
    }

    /** The seed for one layer. Never throws, even if it is asked far too early. */
    public static long seedOf(UncannyDimension dimension, MinecraftServer server) {
        return seedOf(dimension.path(), server);
    }

    public static long seedOf(String path, MinecraftServer server) {
        Long seed = SEEDS.get(path);
        if (seed != null) {
            return seed;
        }
        // Not seeded yet (or the map was cleared). Fill it in on the spot rather
        // than reaching for the overworld and hoping it is there.
        initialise(server);
        seed = SEEDS.get(path);
        if (seed != null) {
            return seed;
        }
        // Still nothing: there is no world at all, or this is not one of our
        // layers. A stable per-path placeholder either way - a wrong seed is
        // recoverable, a crash in the middle of startup is not.
        if (!warnedNoWorld) {
            warnedNoWorld = true;
            LOGGER.warn("[uncanny] seed for '{}' asked for before any world exists, using a placeholder", path);
        }
        return SeedUtil.derive(0L, "dim:" + path);
    }

    /** The ServerWorld for a layer, or null if the datapack removed it. */
    public static ServerWorld worldOf(MinecraftServer server, UncannyDimension dimension) {
        RegistryKey<World> key = dimension.key();
        return server.getWorld(key);
    }

    /** Which layer a player is currently standing in. */
    public static UncannyDimension current(ServerPlayerEntity player) {
        String path = UncannyDimension.pathOf(player.getWorld().getRegistryKey());
        UncannyDimension dimension = UncannyDimension.fromPath(path);
        return dimension == null ? UncannyDimension.OVERWORLD : dimension;
    }

    /**
     * Moves a player into a layer.
     *
     * The layer is entered at its arrival point unless a specific position is
     * given, and the player is nudged upwards until there is air to stand in, so
     * a bad arrival coordinate cannot bury them.
     *
     * @return false if the layer does not exist in this world (datapack removed it)
     */
    public static boolean send(ServerPlayerEntity player, UncannyDimension dimension, BlockPos at) {
        ServerWorld target = worldOf(player.getServer(), dimension);
        if (target == null) {
            LOGGER.warn("[uncanny] dimension {} is missing, cannot send {}", dimension.path(), player.getName().getString());
            return false;
        }

        BlockPos destination = at != null ? at : dimension.arrival();
        destination = findStandable(target, destination, dimension);

        // teleport() is the vanilla cross-dimension move: it handles the chunk
        // load, the screen transition and the client's dimension switch.
        player.teleport(target, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                player.getYaw(), player.getPitch());

        UncannyWorldState state = UncannyWorldState.get(target);
        UncannyPlayerData data = state.player(player.getUuid());
        data.enteredDimension(dimension.path(), target.getTime());
        state.markDirty();
        return true;
    }

    public static boolean send(ServerPlayerEntity player, UncannyDimension dimension) {
        return send(player, dimension, null);
    }

    /**
     * Finds somewhere to stand near a position.
     *
     * Bounded on purpose: it never searches more than a few blocks, so it cannot
     * turn into an accidental world scan.
     */
    public static BlockPos findStandable(ServerWorld world, BlockPos start, UncannyDimension dimension) {
        BlockPos pos = start;
        for (int i = 0; i < 8; i++) {
            if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                    && world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()) {
                return pos;
            }
            pos = pos.up();
        }
        return start.up();
    }

    /**
     * Whether the player is allowed to be pushed deeper into the story right now.
     *
     * Used by transitions that should feel earned. It is a soft gate: the mod can
     * always be configured to be kinder.
     */
    public static boolean readyFor(ServerPlayerEntity player, UncannyWorldState state, UncannyDimension destination) {
        UncannyPlayerData data = state.player(player.getUuid());
        if (destination == UncannyDimension.PARTITION) {
            return PlayerProgress.mayEnterPartition(data, state);
        }
        if (destination == UncannyDimension.ARCHIVE) {
            return PlayerProgress.dimensionsVisited(data) >= 2;
        }
        return true;
    }
}
