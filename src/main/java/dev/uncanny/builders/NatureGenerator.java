package dev.uncanny.builders;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * THE WOODS, and THE COPY.
 *
 * Both layers use ordinary terrain generation, so the ground under the player is
 * real ground. What this class adds is the part that is wrong:
 *
 *   THE WOODS   paths that ignore the terrain, clearings that should not be there,
 *               signs pointing in contradictory directions, and trees that are
 *               identical to trees a long way off.
 *   THE COPY    almost the player's world. The same shapes, the same spacing, with
 *               one building that has a room the original does not have, and one
 *               sign describing something that never happened.
 *
 * Nothing here replaces the terrain. It is written on top of it, which is why both
 * layers still feel like places you could survive in.
 */
public final class NatureGenerator {

    private NatureGenerator() {
    }

    public static void generateChunk(MinecraftServer server, ServerWorld world,
                                     UncannyDimension dimension, ChunkPos chunk) {
        UncannyWorldState state = UncannyWorldState.get(server);
        if (state.isChunkGenerated(dimension.path(), chunk)) {
            return;
        }

        long seed = SeedUtil.forChunk(world.getSeed(), DimensionManager.seedOf(dimension, server),
                chunk.x, chunk.z);
        RandomUtil.UncannyRandom random = RandomUtil.of(seed);

        if (dimension == UncannyDimension.WOODS) {
            woods(world, chunk, random, seed);
        } else {
            copy(world, chunk, random, seed);
        }

        state.markChunkGenerated(dimension.path(), chunk);
        state.markDirty();
    }

    // ------------------------------------------------------------------ woods

    private static void woods(ServerWorld world, ChunkPos chunk, RandomUtil.UncannyRandom random, long seed) {
        BlockPos origin = new BlockPos(chunk.getStartX(), 70, chunk.getStartZ());

        double roll = random.nextDouble();
        if (roll < 0.14) {
            // A path that does not follow the terrain. It cuts through hills rather
            // than going around them, which is the first thing a player notices.
            path(world, origin, random);
        } else if (roll < 0.20) {
            // A clearing. Perfectly round, no stumps.
            int cx = origin.getX() + random.between(4, 11);
            int cz = origin.getZ() + random.between(4, 11);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz <= 9) {
                        removeColumn(world, new BlockPos(cx + dx, 0, cz + dz), 62, 80);
                    }
                }
            }
        } else if (roll < 0.26) {
            // A sign. Two arrows, both certain, pointing at the same place.
            BlockPos at = surface(world, origin.add(8, 0, 8));
            world.setBlockState(at, Blocks.OAK_FENCE.getDefaultState(), 3);
            world.setBlockState(at.up(), Blocks.OAK_SIGN.getDefaultState(), 3);
            if (world.getBlockEntity(at.up()) instanceof SignBlockEntity sign) {
                String place = random.pick(java.util.List.of("HOME", "THE HALL", "OUT", "BACK"));
                sign.setTextOnRow(0, Text.literal("<- " + place));
                sign.setTextOnRow(1, Text.literal(place + " ->"));
            }
        } else if (roll < 0.30) {
            // Identical trees. Same height, same shape, far apart.
            int x = origin.getX() + random.between(2, 13);
            int z = origin.getZ() + random.between(2, 13);
            BlockPos at = surface(world, new BlockPos(x, 0, z));
            identicalTree(world, at, seed);
        }
    }

    /** A straight path, ignoring the shape of the land. */
    private static void path(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random) {
        int z = origin.getZ() + random.between(4, 11);
        for (int x = origin.getX(); x < origin.getX() + 16; x++) {
            BlockPos at = surface(world, new BlockPos(x, 0, z));
            world.setBlockState(at, Blocks.DIRT_PATH.getDefaultState(), 3);
            removeColumn(world, at.up(), at.getY() + 1, at.getY() + 4);
        }
    }

    /** The same tree, twice. Called for both instances with the same seed. */
    private static void identicalTree(ServerWorld world, BlockPos at, long seed) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "twin"));
        int height = 4 + (int) (Math.floorMod(seed, 3));
        for (int y = 1; y <= height; y++) {
            world.setBlockState(at.up(y), Blocks.OAK_LOG.getDefaultState(), 3);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(at.up(height + 1).add(dx, 0, dz), Blocks.OAK_LEAVES.getDefaultState(), 3);
            }
        }
        if (random.nextBoolean()) {
            world.setBlockState(at.up(height + 2), Blocks.OAK_LEAVES.getDefaultState(), 3);
        }
    }

    // ------------------------------------------------------------------- copy

    private static void copy(ServerWorld world, ChunkPos chunk, RandomUtil.UncannyRandom random, long seed) {
        BlockPos origin = new BlockPos(chunk.getStartX(), 70, chunk.getStartZ());
        double roll = random.nextDouble();

        if (roll < 0.06) {
            // A building that is almost right.
            almostHouse(world, origin, random);
        } else if (roll < 0.10) {
            // A sign about something that did not happen here.
            BlockPos at = surface(world, origin.add(8, 0, 8));
            world.setBlockState(at, Blocks.OAK_SIGN.getDefaultState(), 3);
            if (world.getBlockEntity(at) instanceof SignBlockEntity sign) {
                String[] options = {
                        "REBUILT AFTER THE FIRE",
                        "MOVED IN 14 DAYS AGO",
                        "WE NEVER LEFT",
                        "SECOND BURIAL"
                };
                sign.setTextOnRow(0, Text.literal(options.get((int) Math.floorMod(seed, 4))));
            }
        } else if (roll < 0.14) {
            // A structure that was never generated in the original world.
            BlockPos at = surface(world, origin.add(8, 0, 8));
            for (int y = 0; y < 4; y++) {
                world.setBlockState(at.up(y), Blocks.COBBLED_DEEPSLATE.getDefaultState(), 3);
            }
            world.setBlockState(at.up(4), dev.uncanny.item.UncannyBlocks.SEAL_PLATE.getDefaultState(), 3);
        }
    }

    /** A house with one room too many, and no bed. */
    private static void almostHouse(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random) {
        int x0 = origin.getX() + random.between(2, 5);
        int z0 = origin.getZ() + random.between(2, 5);
        BlockPos base = surface(world, new BlockPos(x0, 0, z0));
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
                for (int y = 0; y < 4; y++) {
                    world.setBlockState(base.add(x, y, z),
                            edge ? Blocks.OAK_PLANKS.getDefaultState() : Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
        // The extra room, attached. There is no door into it.
        for (int x = 9; x < 13; x++) {
            for (int z = 2; z < 7; z++) {
                boolean edge = x == 9 || x == 12 || z == 2 || z == 6;
                for (int y = 0; y < 4; y++) {
                    world.setBlockState(base.add(x, y, z),
                            edge ? Blocks.OAK_PLANKS.getDefaultState() : Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
        world.setBlockState(base.add(4, 0, 0), Blocks.AIR.getDefaultState(), 3);
        world.setBlockState(base.add(4, 1, 0), Blocks.AIR.getDefaultState(), 3);
        // No bed. That is the only thing missing, and it is the thing that matters.
    }

    // ---------------------------------------------------------------- helpers

    /** Finds the highest non-air block in a column. Bounded, so it stays cheap. */
    private static BlockPos surface(ServerWorld world, BlockPos at) {
        for (int y = 100; y > 40; y--) {
            BlockPos pos = new BlockPos(at.getX(), y, at.getZ());
            if (!world.getBlockState(pos).isAir()) {
                return pos.up();
            }
        }
        return at.up(70);
    }

    /** Removes blocks in a column between two heights. */
    private static void removeColumn(ServerWorld world, BlockPos at, int fromY, int toY) {
        for (int y = fromY; y <= toY; y++) {
            BlockPos pos = new BlockPos(at.getX(), y, at.getZ());
            if (!world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }

}
