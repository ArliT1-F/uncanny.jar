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

    private DimensionManager() {
    }

    /**
     * Called once, from SERVER_STARTED. Not SERVER_STARTING: the overworld is only
     * created when the worlds load, which happens after SERVER_STARTING has fired.
     */
    public static void initialise(MinecraftServer server) {
        long worldSeed = server.getOverworld().getSeed();
        SEEDS.clear();
        for (UncannyDimension dimension : UncannyDimension.values()) {
            if (dimension == UncannyDimension.OVERWORLD) {
                SEEDS.put(dimension.path(), worldSeed);
            } else {
                SEEDS.put(dimension.path(), SeedUtil.derive(worldSeed, "dim:" + dimension.path()));
            }
        }
        LOGGER.debug("[uncanny] seeded {} layers from world seed", SEEDS.size());
    }

    /** The seed for one layer. Falls back to the world seed if called too early. */
    public static long seedOf(UncannyDimension dimension, MinecraftServer server) {
        Long seed = SEEDS.get(dimension.path());
        if (seed != null) {
            return seed;
        }
        return server.getOverworld().getSeed();
    }

    public static long seedOf(String path, MinecraftServer server) {
        Long seed = SEEDS.get(path);
        return seed != null ? seed : server.getOverworld().getSeed();
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
