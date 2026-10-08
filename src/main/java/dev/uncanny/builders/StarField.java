package dev.uncanny.builders;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.item.UncannyBlocks;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import dev.uncanny.util.Throttle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * THE PRISON OF STARS.
 *
 * Points of light in a black space, placed far enough away that they read as a sky
 * and close enough that they can be watched.
 *
 * They are not stars. The mod never says so, and it never explains what they are.
 * It only ever does three things with them:
 *
 *   - it places them, deterministically, from the world seed;
 *   - it makes one of them brighter over time;
 *   - it lets the player work out that one of them is where they came from.
 *
 * "One of them is getting closer" is expressed as light level rather than movement,
 * because moving a light source across a void is imperceptible and expensive, while
 * a light that is slowly becoming a light you can see the shape of is neither.
 */
public final class StarField {

    /** How many points of light a field gets. */
    public static final int STAR_COUNT = 90;

    /** How far out they are placed. */
    public static final int SPREAD = 96;

    private static final Throttle THROTTLE = new Throttle(20 * 60);

    /** The one that is approaching, as an offset from the field centre. */
    private static BlockPos approachingOffset = null;

    private StarField() {
    }

    /** Places a field around a centre. Called during generation, once per region. */
    public static void scatter(ServerWorld world, BlockPos centre, long seed) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "stars"));
        for (int i = 0; i < STAR_COUNT; i++) {
            int x = random.between(-SPREAD, SPREAD);
            int y = random.between(-30, 60);
            int z = random.between(-SPREAD, SPREAD);
            // Distance from the centre sets the brightness, so the field has depth.
            double distance = Math.sqrt(x * x + y * y + z * z);
            int level = (int) Math.max(1, 14 - distance / 10.0);
            world.setBlockState(centre.add(x, y, z),
                    UncannyBlocks.STARLIGHT.getDefaultState().with(UncannyBlocks.LEVEL, level), 3);
        }
        // One of them is closer than the rest, and it is the one to watch.
        approachingOffset = new BlockPos(random.between(-12, 12), random.between(6, 20),
                random.between(-12, 12));
        world.setBlockState(centre.add(approachingOffset),
                UncannyBlocks.STARLIGHT.getDefaultState().with(UncannyBlocks.LEVEL, 4), 3);
    }

    /**
     * Slowly brightens the approaching light.
     *
     * Once a minute, one level, only while the chunk is loaded. Over a long play
     * session the player can watch it change without ever catching it in the act.
     */
    public static void tick(MinecraftServer server) {
        UncannyWorldState state = UncannyWorldState.get(server);
        if (approachingOffset == null) {
            return;
        }
        if (!THROTTLE.ready(server.getOverworld().getTime())) {
            return;
        }
        for (World candidate : server.getWorlds()) {
            if (!(candidate instanceof ServerWorld world)) {
                continue;
            }
            String path = world.getRegistryKey().getValue().getPath();
            if (!path.equals(dev.uncanny.dimension.UncannyDimension.DEEP.path())
                    && !path.equals(dev.uncanny.dimension.UncannyDimension.ABYSS.path())) {
                continue;
            }
            // The field centre is remembered as the ledger's opposite: it is derived
            // from the same seed, so it does not need saving.
            BlockPos centre = new BlockPos(0, -40, 0);
            BlockPos star = centre.add(approachingOffset);
            if (!world.isChunkLoaded(star.getX() >> 4, star.getZ() >> 4)) {
                continue;
            }
            var blockState = world.getBlockState(star);
            if (!blockState.isOf(UncannyBlocks.STARLIGHT)) {
                continue;
            }
            int level = blockState.get(UncannyBlocks.LEVEL);
            if (level < 15) {
                world.setBlockState(star, blockState.with(UncannyBlocks.LEVEL, level + 1), 3);
            }
        }
    }

}
