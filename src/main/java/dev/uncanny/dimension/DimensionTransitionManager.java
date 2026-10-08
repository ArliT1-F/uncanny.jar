package dev.uncanny.dimension;

import dev.uncanny.audio.UncannySounds;
import dev.uncanny.config.UncannyConfig;
import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.player.PlayerProgress;
import dev.uncanny.player.UncannyPlayerData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The ways in, and the ways that are not ways back.
 *
 * There are no portals. Nothing glows and nothing hums on arrival. Instead the
 * mod watches for ordinary things - sleeping, opening a door, going into deep
 * water, falling too far - and sometimes, if the player has come far enough, the
 * ordinary thing simply does not put them back where they were.
 *
 * Each rule below answers two questions:
 *   should this happen now?   (conditions + probability + pacing)
 *   where does it put them?   (destination + whether it can be undone)
 *
 * Transient bookkeeping (was the player asleep last tick) lives in memory; only
 * real progression is written to {@link UncannyPlayerData}.
 */
public final class DimensionTransitionManager {

    /** How often each player is checked, in ticks. Cheap checks only. */
    public static final int CHECK_INTERVAL = 20;

    /** Transient per-player memory. Cleared on disconnect, nothing important in it. */
    private static final Map<UUID, Transient> TRANSIENT = new HashMap<>();

    private DimensionTransitionManager() {
    }

    private static final class Transient {
        boolean wasSleeping = false;
        int ticksUnderwater = 0;
        double fallStartY = 0;
        boolean falling = false;
        int ticksSinceTransition = 0;
    }

    private static Transient transientFor(UUID uuid) {
        return TRANSIENT.computeIfAbsent(uuid, id -> new Transient());
    }

    /** Forget a player who disconnected. Their saved progress is unaffected. */
    public static void forget(UUID uuid) {
        TRANSIENT.remove(uuid);
    }

    /**
     * Called every CHECK_INTERVAL ticks for every player, from the server tick.
     * Everything in here must stay cheap: a handful of block reads at most.
     */
    public static void tick(ServerPlayerEntity player, UncannyWorldState state) {
        UncannyConfig config = UncannyConfig.get();
        if (!config.enabled) {
            return;
        }
        Transient t = transientFor(player.getUuid());
        t.ticksSinceTransition += CHECK_INTERVAL;

        checkDoorways(player, state);
        checkSleep(player, state, t);
        checkWater(player, state, t);
        checkDeepFall(player, state, t);
    }

    /** Cooldown so a player cannot bounce between layers repeatedly. */
    private static boolean cooledDown(Transient t) {
        return t.ticksSinceTransition >= 20 * 20;
    }

    private static void moved(ServerPlayerEntity player, Transient t) {
        t.ticksSinceTransition = 0;
        UncannySounds.playTransition(player);
    }

    // -------------------------------------------------------------- doorways

    /**
     * Registered doorways. The mod places a few of these quietly during play; the
     * player is never told. Some are one way.
     */
    private static void checkDoorways(ServerPlayerEntity player, UncannyWorldState state) {
        BlockPos here = player.getBlockPos();
        String target = state.thresholdTarget(here);
        if (target == null) {
            // One block of slack: doors are walked through, not stood on.
            target = state.thresholdTarget(here.down());
        }
        if (target == null) {
            return;
        }
        UncannyDimension destination = UncannyDimension.fromPath(target);
        if (destination == null) {
            return;
        }
        Transient t = transientFor(player.getUuid());
        if (!cooledDown(t)) {
            return;
        }
        if (!DimensionManager.readyFor(player, state, destination)) {
            return;
        }
        if (DimensionManager.send(player, destination)) {
            moved(player, t);
            // Some doorways close behind you. The Deep's downward ones do.
            if (destination == UncannyDimension.ABYSS) {
                state.removeThreshold(here);
            }
        }
    }

    // ----------------------------------------------------------------- sleep

    /**
     * Sleeping is the oldest way in.
     *
     * The roll happens when the player lies down, not when they wake: that way the
     * game can leave them lying there for a normal night and simply not put them
     * back in the same bed.
     */
    private static void checkSleep(ServerPlayerEntity player, UncannyWorldState state, Transient t) {
        UncannyConfig config = UncannyConfig.get();
        boolean sleeping = player.isSleeping();

        if (sleeping && !t.wasSleeping) {
            UncannyPlayerData data = state.player(player.getUuid());
            data.sleepCount++;
            if (data.firstSleepPosition == null) {
                data.firstSleepPosition = player.getBlockPos().toImmutable();
            }
            state.markDirty();

            if (config.allowSleepTransitions
                    && PlayerProgress.dimensionsVisited(data) >= 1
                    && data.sleepCount >= 3
                    && chanceFor(data, 0.10)) {
                // Where they wake is decided now, while they sleep.
                t.wasSleeping = true;
                t.falling = data.sleepCount % 2 == 0; // alternating destinations
                return;
            }
        }

        if (!sleeping && t.wasSleeping) {
            t.wasSleeping = false;
            if (t.falling) {
                t.falling = false;
                Transient tt = transientFor(player.getUuid());
                if (cooledDown(tt) && DimensionManager.send(player, UncannyDimension.HALL)) {
                    moved(player, tt);
                }
            }
        } else {
            t.wasSleeping = sleeping;
        }
    }

    // ----------------------------------------------------------------- water

    /**
     * Deep water, held for a few seconds.
     *
     * It is not drowning. The player is never hurt. The water is simply deeper
     * than it was.
     */
    private static void checkWater(ServerPlayerEntity player, UncannyWorldState state, Transient t) {
        UncannyConfig config = UncannyConfig.get();
        if (!config.allowWaterTransitions || !inOpenWater(player)) {
            t.ticksUnderwater = 0;
            return;
        }
        t.ticksUnderwater += CHECK_INTERVAL;
        if (t.ticksUnderwater < 20 * 5) {
            return;
        }
        UncannyPlayerData data = state.player(player.getUuid());
        if (DimensionManager.current(player) != UncannyDimension.OVERWORLD) {
            t.ticksUnderwater = 0;
            return;
        }
        if (cooledDown(t) && chanceFor(data, 0.35) && DimensionManager.send(player, UncannyDimension.DEEP)) {
            t.ticksUnderwater = 0;
            moved(player, t);
        }
    }

    private static boolean inOpenWater(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        if (pos.getY() > 48) {
            return false;
        }
        BlockState here = player.getWorld().getBlockState(pos);
        BlockState above = player.getWorld().getBlockState(pos.up());
        return (here.isOf(Blocks.WATER) || here.isOf(Blocks.CAVE_AIR))
                && above.isOf(Blocks.WATER);
    }

    // ------------------------------------------------------------------ fall

    /**
     * Falling further than the Overworld should allow.
     *
     * There is no void and no damage: the fall simply does not end where it
     * should, and the ground that arrives is not the ground that was expected.
     */
    private static void checkDeepFall(ServerPlayerEntity player, UncannyWorldState state, Transient t) {
        UncannyConfig config = UncannyConfig.get();
        if (!config.allowDeepFallTransitions || DimensionManager.current(player) != UncannyDimension.OVERWORLD) {
            t.falling = false;
            return;
        }
        boolean airborne = !player.isOnGround() && player.getVelocity().y < -0.5;
        if (airborne) {
            if (!t.falling) {
                t.falling = true;
                t.fallStartY = player.getY();
            }
            if (t.fallStartY - player.getY() > 48 && player.getY() < -30) {
                UncannyPlayerData data = state.player(player.getUuid());
                if (cooledDown(t) && chanceFor(data, 0.5) && DimensionManager.send(player, UncannyDimension.DEEP)) {
                    t.falling = false;
                    moved(player, t);
                }
            }
        } else {
            t.falling = false;
        }
    }

    // --------------------------------------------------------------- helpers

    /**
     * A roll that respects pacing. Players who have seen a lot get fewer of these,
     * which is what keeps the mod from becoming a machine gun of transitions.
     */
    private static boolean chanceFor(UncannyPlayerData data, double base) {
        double fatigue = 1.0 / (1.0 + data.anomaliesSeen * 0.03);
        return Math.random() < base * fatigue;
    }

    /** Registers a doorway at a position, leading to a layer. */
    public static void registerDoorway(ServerWorld world, BlockPos pos, UncannyDimension target) {
        UncannyWorldState.get(world).addThreshold(pos.toImmutable(), target.path());
    }
}
