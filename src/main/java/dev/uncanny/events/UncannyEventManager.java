package dev.uncanny.events;

import dev.uncanny.config.UncannyConfig;
import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.events.impl.AllEvents;
import dev.uncanny.lore.SealManager;
import dev.uncanny.player.UncannyPlayerData;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import dev.uncanny.util.Throttle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The event director.
 *
 * One class decides what happens, when, and to whom. Every anomaly in the mod goes
 * through here, which is what makes the pacing controllable from one place instead
 * of being spread across twenty classes that each think they are being subtle.
 *
 * The loop, once every couple of seconds:
 *
 *   for each player
 *     build a context (no world scanning)
 *     ask every event whether it can run
 *     of the ones that can, pick one, weighted by severity
 *     run it
 *
 * At most one anomaly happens to one player per check, and the cooldown in
 * {@link UncannyEvent} keeps the gap between them long. A player can play for half
 * an hour and see three quiet things. That is the design.
 *
 * World-level bookkeeping - seal decay, ledger state, playtime - happens on the
 * same tick, because it is cheap and it keeps everything in one order.
 */
public final class UncannyEventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("uncanny/events");

    private static final List<UncannyEvent> EVENTS = new ArrayList<>();
    private static final Throttle THROTTLE = new Throttle(40);
    private static final Throttle WORLD_THROTTLE = new Throttle(20 * 20);

    private UncannyEventManager() {
    }

    public static void register(UncannyEvent event) {
        EVENTS.add(event);
    }

    /** Registers every anomaly the mod ships with. */
    public static void registerAll() {
        EVENTS.clear();
        AllEvents.register();
        LOGGER.info("[uncanny] {} anomalies registered", EVENTS.size());
    }

    public static int count() {
        return EVENTS.size();
    }

    public static List<String> ids() {
        return EVENTS.stream().map(event -> event.id).toList();
    }

    /** Called every server tick. Does nothing most of the time. */
    public static void tick(MinecraftServer server) {
        UncannyConfig config = UncannyConfig.get();
        if (!config.enabled) {
            return;
        }
        long tick = server.getOverworld().getTime();
        UncannyWorldState state = UncannyWorldState.get(server);
        state.noteStart(tick);

        if (THROTTLE.ready(tick)) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSpectator()) {
                    continue;
                }
                tickPlayer(server, player, state, tick);
            }
        }

        if (WORLD_THROTTLE.ready(tick)) {
            SealManager.decay(server, state, tick);
        }
    }

    /** One player, one check. */
    private static void tickPlayer(MinecraftServer server, ServerPlayerEntity player,
                                   UncannyWorldState state, long tick) {
        UncannyPlayerData data = state.player(player.getUuid());

        // Playtime, in ticks, accumulated by the throttle interval. Cheap and it
        // drives every grace period in the mod.
        data.playTicks += THROTTLE.interval();

        // Being somewhere counts, even if the player never opens a book.
        UncannyDimension here = DimensionManager.current(player);
        data.enteredDimension(here.path(), tick);

        // The first player to get far enough becomes the subject of the House.
        if (data.firstDimensionEntry.size() >= 2) {
            state.setSubject(player.getUuid());
        }

        long seed = SeedUtil.mix(player.getUuid().getMostSignificantBits(), tick);
        RandomUtil.UncannyRandom random = RandomUtil.of(seed);
        EventContext context = new EventContext(server, player.getServerWorld(), player, state, data, tick, seed);

        // Gather candidates first so the choice can be weighted.
        List<UncannyEvent> candidates = new ArrayList<>();
        for (UncannyEvent event : EVENTS) {
            try {
                if (event.canRun(context, random)) {
                    candidates.add(event);
                }
            } catch (RuntimeException e) {
                LOGGER.error("[uncanny] condition failed for {}", event.id, e);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        // Weighted pick: rarer events stay rare even when several are eligible.
        double total = 0;
        for (UncannyEvent event : candidates) {
            total += event.severity.weight();
        }
        double roll = random.nextDouble() * total;
        UncannyEvent chosen = candidates.get(candidates.size() - 1);
        for (UncannyEvent event : candidates) {
            roll -= event.severity.weight();
            if (roll <= 0) {
                chosen = event;
                break;
            }
        }

        try {
            chosen.run(context);
            if (UncannyConfig.get().debugLogging) {
                LOGGER.info("[uncanny] {} -> {} ({} candidates)", player.getName().getString(), chosen.id,
                        candidates.size());
            }
        } catch (RuntimeException e) {
            // One broken anomaly must not take the tick loop with it.
            LOGGER.error("[uncanny] anomaly {} failed", chosen.id, e);
        }
    }

    /** Called when a player dies. Recorded, not acted on: the Ledger will use it. */
    public static void onDeath(ServerPlayerEntity player) {
        UncannyWorldState state = UncannyWorldState.get(player.getServerWorld());
        UncannyPlayerData data = state.player(player.getUuid());
        data.deathCount++;
        if (data.firstDeathPosition == null) {
            data.firstDeathPosition = player.getBlockPos().toImmutable();
        }
        data.home.recordDeath(player.getBlockPos());
        state.markDirty();
    }

    /** Called when a player joins. */
    public static void onJoin(ServerPlayerEntity player) {
        UncannyWorldState state = UncannyWorldState.get(player.getServerWorld());
        UncannyPlayerData data = state.player(player.getUuid());
        data.username = player.getName().getString();
        if (data.home.spawnPosition == null) {
            data.home.spawnPosition = player.getBlockPos().toImmutable();
        }
        state.markDirty();
    }
}
