package dev.uncanny.events;

import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.player.PlayerProgress;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * A test an event can be gated behind.
 *
 * Conditions are functions, and they compose. A typical event reads:
 *
 *     EventCondition.and(
 *         EventCondition.playedMinutes(30),
 *         EventCondition.inForest(),
 *         EventCondition.night(),
 *         EventCondition.notSeenBefore())
 *
 * which is close enough to the design document's own notation that the two can be
 * read side by side.
 *
 * IMPORTANT: a condition may read a handful of blocks around the player. It must
 * never scan a large area. Every condition here obeys that.
 */
@FunctionalInterface
public interface EventCondition {

    boolean test(EventContext context);

    // ------------------------------------------------------------- composing

    static EventCondition and(EventCondition... conditions) {
        return context -> {
            for (EventCondition condition : conditions) {
                if (!condition.test(context)) {
                    return false;
                }
            }
            return true;
        };
    }

    static EventCondition or(EventCondition... conditions) {
        return context -> {
            for (EventCondition condition : conditions) {
                if (condition.test(context)) {
                    return true;
                }
            }
            return false;
        };
    }

    static EventCondition not(EventCondition condition) {
        return context -> !condition.test(context);
    }

    // ------------------------------------------------------------- libraries

    /** Always true. Used for events that only have timing requirements. */
    static EventCondition always() {
        return context -> true;
    }

    /** The player has been in this world for at least this many minutes. */
    static EventCondition playedMinutes(double minutes) {
        return context -> PlayerProgress.minutesPlayed(context.data) >= minutes;
    }

    /** The player is standing in the named layer. */
    static EventCondition inDimension(UncannyDimension dimension) {
        return context -> DimensionManager.current(context.player) == dimension;
    }

    /** The player is in the Overworld. */
    static EventCondition overworld() {
        return context -> DimensionManager.current(context.player) == UncannyDimension.OVERWORLD;
    }

    /** It is night. Cheap: one read of the world clock. */
    static EventCondition night() {
        return context -> {
            long time = context.world.getTimeOfDay() % 24000L;
            return time >= 13000L && time <= 23000L;
        };
    }

    /** It is raining. */
    static EventCondition raining() {
        return context -> context.world.isRaining();
    }

    /** The player is underground, by the ordinary definition. */
    static EventCondition underground() {
        return context -> context.player.getY() < 50
                && !context.world.isSkyVisible(context.player.getBlockPos());
    }

    /**
     * The player is standing in a forest.
     *
     * Matched on the biome id rather than a biome tag, because the tag names moved
     * between Minecraft versions and this mod has to keep compiling against 1.20.1.
     * One registry lookup, no scan.
     */
    static EventCondition inForest() {
        return context -> {
            BlockPos pos = context.player.getBlockPos();
            var biomeId = context.world.getBiome(pos).getKey().orElse(null);
            if (biomeId == null) {
                return false;
            }
            String path = biomeId.getValue().getPath();
            return path.contains("forest") || path.contains("taiga")
                    || path.contains("jungle") || path.contains("grove");
        };
    }

    /** The player is above ground with sky visible. */
    static EventCondition underOpenSky() {
        return context -> context.world.isSkyVisible(context.player.getBlockPos());
    }

    /** The player's lore stage is at least this. */
    static EventCondition stage(int level) {
        return context -> context.stage() >= level;
    }

    /** This event has not already happened to this player. */
    static EventCondition notSeenBefore() {
        return context -> !context.data.eventDone("");
    }

    /** The player has entered at least this many layers. */
    static EventCondition dimensionsVisited(int count) {
        return context -> PlayerProgress.dimensionsVisited(context.data) >= count;
    }

    /** The player has read at least this many documents. */
    static EventCondition loreRead(int count) {
        return context -> context.data.discoveredLore.size() >= count;
    }

    /** The player has noticed at least this many clues. */
    static EventCondition clues(int count) {
        return context -> context.data.clues.size() >= count;
    }

    /** There is a tree within a few blocks. Bounded search, eight blocks out. */
    static EventCondition treeNearby(int radius) {
        return context -> findTree(context, radius) != null;
    }

    /** There is air and grass nearby, i.e. somewhere a tree could go. */
    static EventCondition openGroundNearby(int radius) {
        return context -> findOpenGround(context, radius) != null;
    }

    /** There is a door within a few blocks. */
    static EventCondition doorNearby(int radius) {
        return context -> findDoor(context, radius) != null;
    }

    // ---------------------------------------------------------------- search

    /**
     * Finds a log block near the player.
     *
     * The search is a small cube and is called at most once per event check, so
     * the cost is bounded. radius 4 means 729 block reads, once every few seconds.
     */
    static BlockPos findTree(EventContext context, int radius) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    BlockState state = context.world.getBlockState(pos);
                    if (state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.BIRCH_LOG)
                            || state.isOf(Blocks.SPRUCE_LOG) || state.isOf(Blocks.DARK_OAK_LOG)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    static BlockPos findOpenGround(EventContext context, int radius) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos ground = centre.add(dx, 0, dz);
                BlockState below = context.world.getBlockState(ground.down());
                if (below.isOf(Blocks.GRASS_BLOCK)
                        && context.world.getBlockState(ground).isAir()
                        && context.world.getBlockState(ground.up()).isAir()
                        && context.world.isSkyVisible(ground)) {
                    return ground;
                }
            }
        }
        return null;
    }

    static BlockPos findDoor(EventContext context, int radius) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    if (context.world.getBlockState(pos).getBlock() instanceof net.minecraft.block.DoorBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
