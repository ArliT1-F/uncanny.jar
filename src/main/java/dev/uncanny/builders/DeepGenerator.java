package dev.uncanny.builders;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.generation.GenerationRules;
import dev.uncanny.item.UncannyBlocks;
import dev.uncanny.lore.AnchorManager;
import dev.uncanny.lore.LoreManager;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;

/**
 * THE DEEP.
 *
 * The layer's chunk generator makes it solid rock. This class only ever takes rock
 * away, which is why it is cheap: a tunnel is a few hundred block writes, and an
 * untouched chunk costs nothing at all.
 *
 * What is down here, in order of how deep you have to go:
 *
 *   - tunnels, on the same connection rules as the Hall
 *   - caverns, where the rules are ignored and the rock is simply gone
 *   - an underground forest, which has no business existing
 *   - Surveyor facilities, still lit
 *   - vertical shafts, going down past where the world is supposed to end
 *   - and at the bottom, the Prison of Stars
 */
public final class DeepGenerator {

    /** Height of the main tunnel band. */
    private static final int TUNNEL_HEIGHT = 4;

    private DeepGenerator() {
    }

    public static void generateChunk(MinecraftServer server, ServerWorld world,
                                     UncannyDimension dimension, ChunkPos chunk) {
        UncannyWorldState state = UncannyWorldState.get(server);
        if (state.isChunkGenerated(dimension.path(), chunk)) {
            return;
        }

        long worldSeed = world.getSeed();
        long dimensionSeed = DimensionManager.seedOf(dimension, server);
        long seed = SeedUtil.forChunk(worldSeed, dimensionSeed, chunk.x, chunk.z);
        RandomUtil.UncannyRandom random = RandomUtil.of(seed);
        int floor = dimension.connectorY();
        BlockPos origin = new BlockPos(chunk.getStartX(), floor, chunk.getStartZ());

        // Every chunk gets the main band carved, so the layer is always walkable.
        carveTunnels(world, chunk, floor, dimensionSeed);

        double roll = random.nextDouble();
        if (roll < 0.10) {
            cavern(world, origin, random);
        } else if (roll < 0.16) {
            undergroundForest(world, origin, random);
        } else if (roll < 0.22) {
            facility(world, origin, random, seed);
        } else if (roll < 0.27) {
            anchorRoom(world, origin, random, seed, state);
        } else if (roll < 0.34) {
            shaft(world, origin, random, seed);
        } else if (roll < 0.38) {
            fossils(world, origin, random, seed);
        }

        state.markChunkGenerated(dimension.path(), chunk);
        state.markDirty();
    }

    /** Carves the connecting tunnels for this chunk. */
    private static void carveTunnels(ServerWorld world, ChunkPos chunk, int floor, long dimensionSeed) {
        int mask = GenerationRules.linkMask(dimensionSeed, chunk.x, chunk.z);
        int startX = chunk.getStartX();
        int startZ = chunk.getStartZ();

        for (Direction direction : Direction.Type.HORIZONTAL) {
            if ((mask & GenerationRules.bitOf(direction)) == 0) {
                continue;
            }
            switch (direction) {
                case NORTH -> clear(world, startX + 7, floor, startZ, startX + 8, floor + TUNNEL_HEIGHT - 1, startZ + 8);
                case SOUTH -> clear(world, startX + 7, floor, startZ + 8, startX + 8, floor + TUNNEL_HEIGHT - 1, startZ + 15);
                case WEST -> clear(world, startX, floor, startZ + 7, startX + 8, floor + TUNNEL_HEIGHT - 1, startZ + 8);
                default -> clear(world, startX + 8, floor, startZ + 7, startX + 15, floor + TUNNEL_HEIGHT - 1, startZ + 8);
            }
        }
        // The junction itself.
        clear(world, startX + 6, floor, startZ + 6, startX + 9, floor + TUNNEL_HEIGHT - 1, startZ + 9);
    }

    /** A cavern. Irregular, and dark. */
    private static void cavern(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random) {
        int cx = origin.getX() + 8;
        int cz = origin.getZ() + 8;
        int cy = origin.getY();
        int radius = random.between(5, 8);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // A noisy sphere rather than a real one, so it does not look made.
                    double noise = random.nextDouble() * 2.0;
                    if (x * x + y * y + z * z <= radius * radius - noise * 4) {
                        world.setBlockState(new BlockPos(cx + x, cy + y, cz + z),
                                Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }
        // Something at the bottom of it.
        world.setBlockState(new BlockPos(cx, cy - radius + 1, cz), Blocks.DEEPSLATE.getDefaultState(), 3);
        world.setBlockState(new BlockPos(cx, cy - radius + 2, cz), Blocks.SOUL_TORCH.getDefaultState(), 3);
    }

    /** An underground forest. No sky, no rain, no explanation. */
    private static void undergroundForest(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random) {
        clear(world, origin.getX() + 1, origin.getY(), origin.getZ() + 1,
                origin.getX() + 14, origin.getY() + 9, origin.getZ() + 14);
        for (int x = 1; x < 15; x++) {
            for (int z = 1; z < 15; z++) {
                world.setBlockState(origin.add(x, 0, z), Blocks.MOSS_BLOCK.getDefaultState(), 3);
            }
        }
        for (int i = 0; i < 5; i++) {
            int x = random.between(2, 13);
            int z = random.between(2, 13);
            int height = random.between(3, 6);
            for (int y = 1; y <= height; y++) {
                world.setBlockState(origin.add(x, y, z), Blocks.OAK_LOG.getDefaultState(), 3);
            }
            world.setBlockState(origin.add(x, height + 1, z), Blocks.OAK_LEAVES.getDefaultState(), 3);
            world.setBlockState(origin.add(x + 1, height + 1, z), Blocks.OAK_LEAVES.getDefaultState(), 3);
            world.setBlockState(origin.add(x - 1, height + 1, z), Blocks.OAK_LEAVES.getDefaultState(), 3);
        }
        world.setBlockState(origin.add(7, 8, 7), Blocks.SEA_LANTERN.getDefaultState(), 3);
    }

    /** A Surveyor facility: still powered, still measuring, nobody here. */
    private static void facility(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random, long seed) {
        clear(world, origin.getX() + 2, origin.getY(), origin.getZ() + 2,
                origin.getX() + 13, origin.getY() + 4, origin.getZ() + 13);
        for (int x = 2; x <= 13; x += 11) {
            for (int z = 2; z <= 13; z++) {
                world.setBlockState(origin.add(x, 0, z), Blocks.POLISHED_DEEPSLATE.getDefaultState(), 3);
            }
        }
        world.setBlockState(origin.add(7, 0, 7), Blocks.POLISHED_DEEPSLATE.getDefaultState(), 3);
        world.setBlockState(origin.add(7, 4, 7), Blocks.SEA_LANTERN.getDefaultState(), 3);
        world.setBlockState(origin.add(4, 1, 4), Blocks.CRAFTING_TABLE.getDefaultState(), 3);
        LoreManager.placeChest(world, origin.add(5, 1, 4), 2, seed);
        // The instrument. It is still running, which is somehow the worst part.
        world.setBlockState(origin.add(10, 1, 10), UncannyBlocks.SEAL_PLATE.getDefaultState(), 3);
    }

    /** A numbered room, down here, with a bed that has been slept in. */
    private static void anchorRoom(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random,
                                   long seed, UncannyWorldState state) {
        clear(world, origin.getX() + 3, origin.getY(), origin.getZ() + 3,
                origin.getX() + 12, origin.getY() + 3, origin.getZ() + 12);
        int number = AnchorManager.numberFor(seed);
        String status = AnchorManager.status(seed, number);
        world.setBlockState(origin.add(4, 1, 4), Blocks.RED_BED.getDefaultState(), 3);
        world.setBlockState(origin.add(8, 1, 4), Blocks.OAK_PLANKS.getDefaultState(), 3);
        world.setBlockState(origin.add(8, 2, 4), Blocks.TORCH.getDefaultState(), 3);
        world.setBlockState(origin.add(4, 1, 10), Blocks.WATER_CAULDRON.getDefaultState(), 3);
        world.setBlockState(origin.add(7, 3, 7), Blocks.LANTERN.getDefaultState(), 3);
        state.claimAnchor(number);
    }

    /**
     * A shaft, going down.
     *
     * It is the way to the Prison of Stars. The player can see the bottom of it,
     * which is not a floor.
     */
    private static void shaft(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random, long seed) {
        int x = origin.getX() + random.between(5, 10);
        int z = origin.getZ() + random.between(5, 10);
        for (int y = -32; y <= 12; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlockState(new BlockPos(x + dx, origin.getY() + y, z + dz),
                            Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
        // A ladder, part of the way down. Not all of the way.
        for (int y = 0; y >= -12; y--) {
            world.setBlockState(new BlockPos(x - 1, origin.getY() + y, z),
                    Blocks.LADDER.getDefaultState()
                            .with(net.minecraft.block.LadderBlock.FACING, Direction.EAST), 3);
        }
        // Register the bottom as a doorway into the Abyss. One way.
        BlockPos bottom = new BlockPos(x, origin.getY() - 32, z);
        world.setBlockState(bottom.down(), UncannyBlocks.SURVEY_MARKER.getDefaultState(), 3);
        dev.uncanny.dimension.DimensionTransitionManager.registerDoorway(world, bottom.down(),
                UncannyDimension.ABYSS);
        StarField.scatter(world, bottom.down(40), seed);
    }

    /** Bones. Too many of some of them. */
    private static void fossils(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random, long seed) {
        clear(world, origin.getX() + 4, origin.getY(), origin.getZ() + 4,
                origin.getX() + 11, origin.getY() + 3, origin.getZ() + 11);
        int count = random.between(3, 7);
        for (int i = 0; i < count; i++) {
            world.setBlockState(origin.add(4 + i, 1, 4 + (i % 3)), Blocks.BONE_BLOCK.getDefaultState(), 3);
        }
    }

    /** Carves a box. Inclusive coordinates, world space. */
    private static void clear(ServerWorld world, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }
}
