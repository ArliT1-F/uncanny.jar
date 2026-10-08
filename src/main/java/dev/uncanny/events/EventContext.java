package dev.uncanny.events;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.player.UncannyPlayerData;
import dev.uncanny.util.RandomUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Everything an event is allowed to look at.
 *
 * Passed in rather than fetched, so an event cannot wander off and start reading
 * things it should not: no scanning the world, no looking at other players, no
 * reaching into another dimension.
 */
public final class EventContext {

    public final MinecraftServer server;
    public final ServerWorld world;
    public final ServerPlayerEntity player;
    public final UncannyWorldState state;
    public final UncannyPlayerData data;
    public final long tick;
    private final RandomUtil.UncannyRandom random;

    public EventContext(MinecraftServer server, ServerWorld world, ServerPlayerEntity player,
                        UncannyWorldState state, UncannyPlayerData data, long tick, long seed) {
        this.server = server;
        this.world = world;
        this.player = player;
        this.state = state;
        this.data = data;
        this.tick = tick;
        this.random = RandomUtil.of(seed);
    }

    public RandomUtil.UncannyRandom random() {
        return this.random;
    }

    /** The player's current lore stage, as a number. */
    public int stage() {
        return dev.uncanny.player.PlayerProgress.stage(this.data, this.state).level();
    }
}
