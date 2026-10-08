package dev.uncanny.events;

import dev.uncanny.config.UncannyConfig;
import dev.uncanny.data.AnomalyLocation;
import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionBehavior;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.events.impl.AllEvents;
import dev.uncanny.lore.SealManager;
import dev.uncanny.player.PlayerBehavior;
import dev.uncanny.player.PlayerMemory;
import dev.uncanny.player.PlayerProgress;
import dev.uncanny.player.RealityInstability;
import dev.uncanny.player.UncannyPlayerData;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import dev.uncanny.util.Throttle;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
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
 * The loop, at the configured interval (eventCheckIntervalTicks):
 *
 *   for each player
 *     accrue playtime and behavioural memory (cheap, one map write)
 *     global grace period: before gracePeriodMinutes, NO anomaly selection at all
 *     build a context (no world scanning)
 *     evaluate every event: each says yes, or why not
 *     of the ones that pass probability, pick one, weighted by severity,
 *       bent by the layer's identity and this player's behaviour
 *     run it
 *
 * At most one anomaly happens to one player per check, and the cooldown in
 * {@link UncannyEvent} keeps the gap between them long. A player can play for half
 * an hour and see three quiet things. That is the design.
 *
 * With debugLogging on, every check prints the full funnel - player, playtime,
 * stage, instability, counts, selection - and the reason every rejected event
 * was rejected, at the check interval and never per tick. That is the whole
 * balancing surface of the horror system in one log file.
 */
public final class UncannyEventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("uncanny/events");

    private static final List<UncannyEvent> EVENTS = new ArrayList<>();

    /** Replaced whenever config.eventCheckIntervalTicks changes. */
    private static Throttle THROTTLE = new Throttle(40);
    private static int throttleInterval = 40;

    private static final Throttle WORLD_THROTTLE = new Throttle(20 * 20);

    /** Ticks between playtime snapshots used to persist accrual. */
    private static final long DIRTY_BUCKET = 20L * 60 * 5;

    /** Anchor-room sign scan runs every Nth check, only in anchor-bearing layers. */
    private static int checkCounter = 0;
    private static boolean loggedActive = false;

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

    /** Looks up a registered event by id, for the debug command. */
    public static UncannyEvent find(String id) {
        for (UncannyEvent event : EVENTS) {
            if (event.id.equals(id)) {
                return event;
            }
        }
        return null;
    }

    /** Called every server tick. Does nothing most of the time. */
    public static void tick(MinecraftServer server) {
        UncannyConfig config = UncannyConfig.get();
        if (!config.enabled) {
            return;
        }
        syncInterval(config);
        long tick = server.getOverworld().getTime();
        UncannyWorldState state = UncannyWorldState.get(server);
        state.noteStart(tick);

        if (THROTTLE.ready(tick)) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSpectator()) {
                    continue;
                }
                tickPlayer(server, player, state, tick, config);
            }
        }

        if (WORLD_THROTTLE.ready(tick)) {
            SealManager.decay(server, state, tick);
        }
    }

    /** Applies eventCheckIntervalTicks. Rebuilds the throttle only when it changes. */
    private static void syncInterval(UncannyConfig config) {
        int wanted = Math.max(1, config.eventCheckIntervalTicks);
        if (wanted != throttleInterval) {
            throttleInterval = wanted;
            THROTTLE = new Throttle(wanted);
            LOGGER.info("[uncanny] event check interval now every {} ticks", wanted);
        }
        if (!loggedActive) {
            loggedActive = true;
            LOGGER.info("[uncanny] event director active, checking every {} ticks", throttleInterval);
        }
    }

    /** One player, one check. */
    private static void tickPlayer(MinecraftServer server, ServerPlayerEntity player,
                                   UncannyWorldState state, long tick, UncannyConfig config) {
        UncannyPlayerData data = state.player(player.getUuid());
        boolean debug = config.debugLogging;

        // ---- playtime and behavioural memory, in one pass ----
        //
        // Playtime only advances while the player is online: the delta is measured
        // from the last accrual and the join handler resets it, so time spent
        // logged out never counts towards the grace period.
        long bucketBefore = data.playTicks / DIRTY_BUCKET;
        if (data.lastPlayAccrualTick == 0 || tick < data.lastPlayAccrualTick) {
            data.lastPlayAccrualTick = tick;
        }
        long delta = tick - data.lastPlayAccrualTick;
        if (delta > 0) {
            // Guard against absurd deltas (clock jumps); 10 minutes in one check
            // is already more than any real session should produce.
            delta = Math.min(delta, 20L * 60 * 10);
            data.playTicks += delta;
            data.lastPlayAccrualTick = tick;
        }

        UncannyDimension here = DimensionManager.current(player);
        String areaBefore = data.memory.lastArea;
        String dimensionBefore = data.memory.lastDimension;
        boolean underground = player.getY() < 50
                && !player.getServerWorld().isSkyVisible(player.getBlockPos());
        data.memory.accrue(delta, tick, here.path(), player.getBlockPos(), underground);
        data.currentDimension = here.path();
        data.enteredDimension(here.path(), tick);

        // Persist memory when something worth persisting moved. Bounded: at most
        // one dirty flag per check, and only on a real change.
        if (!areaBefore.equals(data.memory.lastArea)
                || dimensionBefore == null || !dimensionBefore.equals(data.memory.lastDimension)
                || data.playTicks / DIRTY_BUCKET != bucketBefore) {
            state.markDirty();
        }

        // The first player to get far enough becomes the subject of the House.
        if (data.firstDimensionEntry.size() >= 2) {
            state.setSubject(player.getUuid());
        }

        // ---- the world remembers where anomalies were ----
        noteRevisit(data, state, here, player.getBlockPos(), tick);

        // ---- anchor rooms announce themselves to anyone who stands in one ----
        checkAnchorRoom(player, state, here, tick);

        // ---- global grace period: no anomaly selection at all before it ends ----
        long minutes = (long) PlayerProgress.minutesPlayed(data);
        if (minutes < config.gracePeriodMinutes) {
            if (debug) {
                LOGGER.info("[uncanny] {} -> grace period ({} of {} minutes)",
                        player.getName().getString(), minutes, config.gracePeriodMinutes);
            }
            return;
        }

        long seed = SeedUtil.mix(player.getUuid().getMostSignificantBits(), tick);
        RandomUtil.UncannyRandom random = RandomUtil.of(seed);
        EventContext context = new EventContext(server, player.getServerWorld(), player, state,
                data, tick, seed);

        // ---- evaluate every event: yes, or why not ----
        List<UncannyEvent.Evaluation> evaluations = new ArrayList<>(EVENTS.size());
        List<UncannyEvent> eligible = new ArrayList<>();
        List<UncannyEvent> candidates = new ArrayList<>();
        for (UncannyEvent event : EVENTS) {
            UncannyEvent.Evaluation evaluation;
            try {
                evaluation = event.evaluate(context, random, debug);
            } catch (RuntimeException e) {
                LOGGER.error("[uncanny] condition failed for {}", event.id, e);
                evaluation = new UncannyEvent.Evaluation(
                        UncannyEvent.Rejection.CONDITION, "condition failed (error)");
            }
            evaluations.add(evaluation);
            if (evaluation.rejection == UncannyEvent.Rejection.NONE) {
                eligible.add(event);
                candidates.add(event);
            } else if (evaluation.rejection == UncannyEvent.Rejection.PROBABILITY) {
                // Passed every gate and its conditions, then failed the roll:
                // it was eligible, it just did not get picked this time.
                eligible.add(event);
            }
        }

        // ---- pick one, weighted ----
        UncannyEvent chosen = null;
        if (!candidates.isEmpty()) {
            String areaKey = PlayerMemory.areaKey(here.path(), player.getBlockPos());
            String favoriteArea = data.memory.favoriteArea();
            double total = 0;
            double[] weights = new double[candidates.size()];
            for (int i = 0; i < candidates.size(); i++) {
                UncannyEvent event = candidates.get(i);
                double weight = event.baseWeight(data)
                        * DimensionBehavior.weightBias(event, here, data)
                        * PlayerBehavior.selectionBias(event, data, areaKey, favoriteArea);
                weights[i] = weight;
                total += weight;
            }
            double roll = random.nextDouble() * total;
            chosen = candidates.get(candidates.size() - 1);
            for (int i = 0; i < candidates.size(); i++) {
                roll -= weights[i];
                if (roll <= 0) {
                    chosen = candidates.get(i);
                    break;
                }
            }
        }

        // ---- run it ----
        boolean executed = false;
        if (chosen != null) {
            try {
                chosen.run(context);
                executed = true;
            } catch (RuntimeException e) {
                // One broken anomaly must not take the tick loop with it.
                LOGGER.error("[uncanny] anomaly {} failed", chosen.id, e);
            }
        }

        // ---- diagnostics: the whole funnel, once per check ----
        if (debug) {
            logDiagnostics(player, state, data, evaluations, eligible, candidates, chosen, executed);
        }
    }

    /**
     * The balancing surface. Printed at the event-check interval only, never per
     * tick, and only when debugLogging is on.
     */
    private static void logDiagnostics(ServerPlayerEntity player, UncannyWorldState state,
                                       UncannyPlayerData data,
                                       List<UncannyEvent.Evaluation> evaluations,
                                       List<UncannyEvent> eligible, List<UncannyEvent> candidates,
                                       UncannyEvent chosen, boolean executed) {
        String name = player.getName().getString();
        LOGGER.info("[uncanny] Player: {}", name);
        LOGGER.info("[uncanny] Playtime: {}", (long) PlayerProgress.minutesPlayed(data));
        LOGGER.info("[uncanny] Stage: {}", PlayerProgress.stage(data, state).level());
        LOGGER.info("[uncanny] Reality instability: {}",
                String.format(java.util.Locale.ROOT, "%.2f", RealityInstability.value(data)));
        LOGGER.info("[uncanny] Candidates: {}", candidates.size());
        LOGGER.info("[uncanny] Eligible: {}", eligible.size());
        LOGGER.info("[uncanny] Selected: {}", chosen == null ? "none" : chosen.id);
        LOGGER.info("[uncanny] Executed: {}", executed);
        for (int i = 0; i < EVENTS.size(); i++) {
            UncannyEvent.Evaluation evaluation = evaluations.get(i);
            if (evaluation.rejection == UncannyEvent.Rejection.NONE) {
                continue; // eligible: either chosen or lost the vote; counts show it
            }
            LOGGER.info("[uncanny] {} -> {}", EVENTS.get(i).id, evaluation.detail);
        }
    }

    /**
     * Why each registered event can or cannot run for this player right now.
     *
     * Used by /uncanny evaluate: the same evaluation the debug log prints, on
     * demand, without running anything. Does not touch the director's own random
     * stream (it uses its own salted seed).
     */
    public static List<String> explain(ServerPlayerEntity player, UncannyWorldState state,
                                       long tick) {
        List<String> lines = new ArrayList<>();
        UncannyConfig config = UncannyConfig.get();
        UncannyPlayerData data = state.player(player.getUuid());
        long minutes = (long) PlayerProgress.minutesPlayed(data);
        if (minutes < config.gracePeriodMinutes) {
            lines.add("grace period: " + minutes + " of " + config.gracePeriodMinutes + " minutes");
            return lines;
        }
        long seed = SeedUtil.mix(player.getUuid().getMostSignificantBits(), tick, 0xE4EA17);
        RandomUtil.UncannyRandom random = RandomUtil.of(seed);
        EventContext context = new EventContext(player.getServer(), player.getServerWorld(),
                player, state, data, tick, seed);
        for (UncannyEvent event : EVENTS) {
            try {
                UncannyEvent.Evaluation evaluation = event.evaluate(context, random, true);
                lines.add(event.id + " -> " + evaluation.detail);
            } catch (RuntimeException e) {
                lines.add(event.id + " -> condition failed (error: " + e.getMessage() + ")");
            }
        }
        return lines;
    }

    /**
     * A recorded anomaly nearby, revisited after a long gap, nudges instability
     * up a little. This is the "world remembers this place" hook: it runs at the
     * check interval and compares at most 128 packed positions. No blocks are
     * read.
     */
    private static void noteRevisit(UncannyPlayerData data, UncannyWorldState state,
                                    UncannyDimension dimension, BlockPos pos, long tick) {
        AnomalyLocation near = state.anomalyNear(dimension.path(), pos, 40);
        if (near == null || tick - near.lastSeen < 20L * 60 * 10) {
            return;
        }
        near.lastSeen = tick;
        RealityInstability.raise(data, 0.004);
        state.markDirty();
    }

    /**
     * Anchor rooms announce themselves: if a sign reading "ANCHOR nnn" is within
     * five blocks, the player has stood in an anchor room.
     *
     * Bounded and throttled: every 4th check, only in layers that contain anchor
     * rooms, only while fewer than nine have been recorded, at most a 11x6x11
     * block-entity probe. Sign reading is how this used to be intended to work -
     * the number was always on the wall; nothing ever wrote it down.
     */
    private static void checkAnchorRoom(ServerPlayerEntity player, UncannyWorldState state,
                                        UncannyDimension dimension, long tick) {
        if (dimension != UncannyDimension.HALL && dimension != UncannyDimension.ARCHIVE
                && dimension != UncannyDimension.PARTITION) {
            return;
        }
        checkCounter++;
        if (checkCounter % 4 != 0) {
            return;
        }
        UncannyPlayerData data = state.player(player.getUuid());
        if (data.anchorsSeen.size() >= 9) {
            return;
        }
        String label = findAnchorLabel(player.getServerWorld(), player.getBlockPos());
        if (label == null || data.anchorsSeen.contains(label)) {
            return;
        }
        data.anchorsSeen.add(label);
        RealityInstability.raise(data, 0.008);
        state.markDirty();
        if (UncannyConfig.get().debugLogging) {
            LOGGER.info("[uncanny] {} stood in {}", player.getName().getString(), label);
        }
    }

    /** Bounded search for a sign that names an anchor. Null when none. */
    private static String findAnchorLabel(ServerWorld world, BlockPos centre) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    if (blockEntity instanceof SignBlockEntity sign) {
                        String front = anchorOn(sign, true);
                        if (front != null) {
                            return front;
                        }
                        String back = anchorOn(sign, false);
                        if (back != null) {
                            return back;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String anchorOn(SignBlockEntity sign, boolean front) {
        var text = sign.getText(front);
        for (int row = 0; row < 4; row++) {
            String line = text.getMessage(row, false).getString().trim();
            if (line.startsWith("ANCHOR ") && line.length() >= 10) {
                return line;
            }
        }
        return null;
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
        // Time logged out does not count: restart the playtime accrual clock and
        // note where they actually are before the first check.
        data.lastPlayAccrualTick = player.getServerWorld().getTime();
        data.currentDimension = DimensionManager.current(player).path();
        state.markDirty();
    }
}
