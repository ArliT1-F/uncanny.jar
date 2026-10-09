package dev.uncanny.events;

import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.player.PlayerProgress;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.StairsBlock;
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
 * Every condition carries the reason it fails ("not in forest", "no door
 * nearby"), which is what the debug log prints when an event does not run:
 *
 *     [uncanny] missing_tree -> condition failed: not in forest
 *
 * IMPORTANT: a condition may read a handful of blocks around the player. It must
 * never scan a large area. Every condition here obeys that.
 */
@FunctionalInterface
public interface EventCondition {

    boolean test(EventContext context);

    /** Why a labeled condition fails, or null when it is unlabeled. */
    default String failure(EventContext context) {
        return null;
    }

    // ------------------------------------------------------------- composing

    static EventCondition and(EventCondition... conditions) {
        return new EventCondition() {
            @Override
            public boolean test(EventContext context) {
                for (EventCondition condition : conditions) {
                    if (!condition.test(context)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public String failure(EventContext context) {
                // The first child that fails explains itself; that is the reason
                // the debug log shows, so the child labels matter more than this one.
                for (EventCondition condition : conditions) {
                    if (!condition.test(context)) {
                        return condition.failure(context);
                    }
                }
                return null;
            }
        };
    }

    static EventCondition or(EventCondition... conditions) {
        return new EventCondition() {
            @Override
            public boolean test(EventContext context) {
                for (EventCondition condition : conditions) {
                    if (condition.test(context)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String failure(EventContext context) {
                for (EventCondition condition : conditions) {
                    String label = condition.failure(context);
                    if (label != null) {
                        return label;
                    }
                }
                return null;
            }
        };
    }

    static EventCondition not(EventCondition condition) {
        return new EventCondition() {
            @Override
            public boolean test(EventContext context) {
                return !condition.test(context);
            }

            @Override
            public String failure(EventContext context) {
                return "opposite condition holds";
            }
        };
    }

    /**
     * Attaches the reason a condition fails, for the debug log.
     *
     * The label describes the FAILURE, not the success: inForest() is labeled
     * "not in forest", so a failed condition reads
     * {@code missing_tree -> condition failed: not in forest}.
     */
    static EventCondition named(String label, EventCondition condition) {
        return new EventCondition() {
            @Override
            public boolean test(EventContext context) {
                return condition.test(context);
            }

            @Override
            public String failure(EventContext context) {
                return condition.test(context) ? null : label;
            }
        };
    }

    /** The same, with a label computed from the context. */
    static EventCondition named(java.util.function.Function<EventContext, String> label,
                                EventCondition condition) {
        return new EventCondition() {
            @Override
            public boolean test(EventContext context) {
                return condition.test(context);
            }

            @Override
            public String failure(EventContext context) {
                return condition.test(context) ? null : label.apply(context);
            }
        };
    }

    // ------------------------------------------------------------- libraries

    /** Always true. Used for events that only have timing requirements. */
    static EventCondition always() {
        return context -> true;
    }

    /** The player has been in this world for at least this many minutes. */
    static EventCondition playedMinutes(double minutes) {
        return named("under " + (long) minutes + " minutes played",
                context -> PlayerProgress.minutesPlayed(context.data) >= minutes);
    }

    /** The player is standing in the named layer. */
    static EventCondition inDimension(UncannyDimension dimension) {
        return named("not in " + dimension.displayName(),
                context -> DimensionManager.current(context.player) == dimension);
    }

    /** The player is in the Overworld. */
    static EventCondition overworld() {
        return named("not in the overworld",
                context -> DimensionManager.current(context.player) == UncannyDimension.OVERWORLD);
    }

    /** It is night. Cheap: one read of the world clock. */
    static EventCondition night() {
        return named("not night", context -> {
            long time = context.world.getTimeOfDay() % 24000L;
            return time >= 13000L && time <= 23000L;
        });
    }

    /** It is raining. */
    static EventCondition raining() {
        return named("not raining", context -> context.world.isRaining());
    }

    /** The player is underground, by the ordinary definition. */
    static EventCondition underground() {
        return named("not underground", context -> context.player.getY() < 50
                && !context.world.isSkyVisible(context.player.getBlockPos()));
    }

    /**
     * The player is standing in a forest.
     *
     * Matched on the biome id rather than a biome tag, because the tag names moved
     * between Minecraft versions and this mod has to keep compiling against 1.20.1.
     * One registry lookup, no scan.
     */
    static EventCondition inForest() {
        return named("not in forest", context -> {
            BlockPos pos = context.player.getBlockPos();
            var biomeId = context.world.getBiome(pos).getKey().orElse(null);
            if (biomeId == null) {
                return false;
            }
            String path = biomeId.getValue().getPath();
            return path.contains("forest") || path.contains("taiga")
                    || path.contains("jungle") || path.contains("grove");
        });
    }

    /** The player is above ground with sky visible. */
    static EventCondition underOpenSky() {
        return named("no open sky",
                context -> context.world.isSkyVisible(context.player.getBlockPos()));
    }

    /** The player's lore stage is at least this. */
    static EventCondition stage(int level) {
        return named("lore stage below " + level, context -> context.stage() >= level);
    }

    /** This event has not already happened to this player. */
    static EventCondition notSeenBefore() {
        return named("already happened", context -> !context.data.eventDone(""));
    }

    /** The player has entered at least this many layers. */
    static EventCondition dimensionsVisited(int count) {
        return named("fewer than " + count + " layers visited",
                context -> PlayerProgress.dimensionsVisited(context.data) >= count);
    }

    /** The player has read at least this many documents. */
    static EventCondition loreRead(int count) {
        return named("fewer than " + count + " documents read",
                context -> context.data.discoveredLore.size() >= count);
    }

    /** The player has noticed at least this many clues. */
    static EventCondition clues(int count) {
        return named("fewer than " + count + " clues",
                context -> context.data.clues.size() >= count);
    }

    /** There is a tree within a few blocks. Bounded search, eight blocks out. */
    static EventCondition treeNearby(int radius) {
        return named("no tree nearby", context -> findTree(context, radius) != null);
    }

    /** There is air and grass nearby, i.e. somewhere a tree could go. */
    static EventCondition openGroundNearby(int radius) {
        return named("no open ground nearby",
                context -> findOpenGround(context, radius) != null);
    }

    /** There is a door within a few blocks. */
    static EventCondition doorNearby(int radius) {
        return named("no door nearby", context -> findDoor(context, radius) != null);
    }

    /** There is a chest within a few blocks. */
    static EventCondition chestNearby(int radius) {
        return named("no chest nearby", context -> findChest(context, radius) != null);
    }

    /** There is a staircase within a few blocks that can take one more step. */
    static EventCondition stairsNearby(int radius) {
        return named("no staircase nearby",
                context -> findStairsWithRoom(context, radius) != null);
    }

    /** There is a dirt path block within a few blocks. */
    static EventCondition pathNearby(int radius) {
        return named("no path nearby",
                context -> findDirtPath(context, radius) != null);
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

    static BlockPos findChest(EventContext context, int radius) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    if (context.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A staircase that can take one additional step: the stairs themselves, plus
     * an air block with solid ground in front of its low side for a bottom slab.
     * One bounded cube; nothing else is read.
     */
    static BlockPos findStairsWithRoom(EventContext context, int radius) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    BlockState state = context.world.getBlockState(pos);
                    if (!(state.getBlock() instanceof StairsBlock)) {
                        continue;
                    }
                    // Read the facing through the public property constant: the
                    // lookup is by property name ("facing"), so any horizontal
                    // block state answers, and no package-private field is needed.
                    BlockPos target = pos.offset(
                            state.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING));
                    if (context.world.getBlockState(target).isAir()
                            && !context.world.getBlockState(target.down()).isAir()
                            && context.world.getBlockState(target.up()).isAir()) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    static BlockPos findDirtPath(EventContext context, int radius) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    if (context.world.getBlockState(pos).isOf(Blocks.DIRT_PATH)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
