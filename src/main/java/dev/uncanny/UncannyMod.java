package dev.uncanny;

import dev.uncanny.audio.UncannySounds;
import dev.uncanny.builders.EzekielRings;
import dev.uncanny.builders.StarField;
import dev.uncanny.config.UncannyConfig;
import dev.uncanny.command.UncannyCommand;
import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.DimensionTransitionManager;
import dev.uncanny.generation.ProceduralDimensionGenerator;
import dev.uncanny.generation.RoomTemplates;
import dev.uncanny.item.UncannyBlocks;
import dev.uncanny.item.UncannyItems;
import dev.uncanny.lore.LoreManager;
import dev.uncanny.events.UncannyEventManager;
import dev.uncanny.player.PlayerActivityTracker;
import dev.uncanny.player.UncannyPlayerData;
import dev.uncanny.util.Throttle;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uncanny.
 *
 * A horror mod with no monsters in it. Everything here is either registration or
 * scheduling; the interesting code lives in the packages below, and each package
 * has one job:
 *
 *   dimension/   the layers, and how a player gets between them
 *   generation/  the procedural room system
 *   builders/    what each room actually looks like
 *   events/      the event director, and every anomaly
 *   lore/        documents, anchors, seals, and the Ledger
 *   player/      what the mod remembers about you
 *   data/        what the mod remembers about the world
 *   audio/       sound, and silence
 *   rendering/   client-side visuals
 *
 * Nothing is registered twice and nothing is initialised lazily in a way that
 * depends on ordering, other than blocks before items.
 */
public class UncannyMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("uncanny");

    /** Transition checks are cheaper if they are not done every tick. */
    private static final Throttle TRANSITION_THROTTLE = new Throttle(DimensionTransitionManager.CHECK_INTERVAL);

    @Override
    public void onInitialize() {
        UncannyConfig config = UncannyConfig.get();

        // ---- registration, in the order Minecraft requires ----
        UncannyBlocks.register();
        UncannyItems.register();
        RoomTemplates.registerAll();
        UncannyEventManager.registerAll();

        // Lore is loaded from JSON inside the jar.
        LoreManager.load();

        // ---- server lifecycle ----
        // SERVER_STARTING fires before any world is loaded, so the overworld (which the
        // layer seeds are derived from) does not exist yet. SERVER_STARTED fires once
        // all worlds are live, so anything that reads a world belongs there.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            UncannyConfig.get().save();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DimensionManager.initialise(server);
            LOGGER.info("[uncanny] {} room templates, {} lore documents, {} anomalies",
                    RoomTemplates.count(), LoreManager.size(), UncannyEventManager.count());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ProceduralDimensionGenerator.reset();
            DimensionManager.reset();
            UncannySounds.reset();
            dev.uncanny.util.Scheduler.reset();
        });

        // ---- the tick ----
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Picks up a seeding that was deferred during startup. Cheap: one
            // boolean read once the layers are seeded.
            DimensionManager.tick(server);

            // Pending reverts always land, even if the mod was switched off
            // between scheduling and firing them.
            dev.uncanny.util.Scheduler.tick(server.getOverworld().getTime());

            if (!UncannyConfig.get().enabled) {
                return;
            }
            ProceduralDimensionGenerator.tick(server);
            UncannyEventManager.tick(server);
            UncannySounds.tick(server);
            StarField.tick(server);
            EzekielRings.tick(server);

            if (TRANSITION_THROTTLE.ready(server.getOverworld().getTime())) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    UncannyWorldState state = UncannyWorldState.get(server);
                    UncannyPlayerData data = state.player(player.getUuid());
                    DimensionTransitionManager.tick(player, state);
                    if (data.username == null || !data.username.equals(player.getName().getString())) {
                        data.username = player.getName().getString();
                    }
                }
            }
        });

        // ---- generation ----
        // CHUNK_LOAD gives us the finished chunk. We do not fill it here: we queue
        // it, and a couple are processed per tick, so entering a layer for the first
        // time never causes a stall.
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                ProceduralDimensionGenerator.onChunkLoaded(world, chunk));

        // ---- players ----
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                UncannyEventManager.onJoin(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                DimensionTransitionManager.forget(handler.getPlayer().getUuid()));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                UncannyEventManager.onDeath(player);
            }
        });

        // ---- watching the player build ----
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer
                    && world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                PlayerActivityTracker.onUseBlock(serverPlayer, serverWorld, hitResult.getBlockPos(), hand);
            }
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer
                    && world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                PlayerActivityTracker.onBreakBlock(serverPlayer, serverWorld, pos, state);
            }
        });

        // ---- the debug command ----
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                UncannyCommand.register(dispatcher));

        LOGGER.info("[uncanny] initialised. The world will not feel different for a while.");
    }
}
