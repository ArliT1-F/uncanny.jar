package dev.uncanny.builders;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.DimensionTransitionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.item.UncannyBlocks;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;

/**
 * BELOW.
 *
 * The layer under the Deep, and the one place the mod stops pretending to build
 * anything. Chunks here are mostly nothing: unfinished terrain, islands of stone
 * hanging in the dark, and one door.
 *
 * The door says ABYSS. Opening it is not a fight. What is behind it is the Prison
 * of Stars, and the player can stand at the edge of it for as long as they like.
 */
public final class AbyssGenerator {

    private AbyssGenerator() {
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
        int floor = dimension.connectorY();
        BlockPos origin = new BlockPos(chunk.getStartX(), floor, chunk.getStartZ());

        // Most chunks are simply empty. That is the point of the layer.
        double roll = random.nextDouble();
        if (roll < 0.18) {
            island(world, origin, random);
        } else if (roll < 0.26) {
            brokenStair(world, origin, random);
        } else if (roll < 0.30) {
            // An exposed edge: terrain that stops mid-block-row.
            halfChunk(world, origin, random);
        }

        // The door exists exactly once, in the chunk at the origin.
        if (chunk.x == 0 && chunk.z == 0) {
            theDoor(world, origin);
        }

        // The Prison of Stars is below everything, and always was.
        if (chunk.x == 0 && chunk.z == 0) {
            StarField.scatter(world, origin.down(96), seed);
        }

        state.markChunkGenerated(dimension.path(), chunk);
        state.markDirty();
    }

    /** A piece of ground, hanging. */
    private static void island(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random) {
        int size = random.between(3, 6);
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                if (x * x + z * z <= size * size) {
                    int thickness = random.between(1, 3);
                    for (int y = 0; y < thickness; y++) {
                        world.setBlockState(origin.add(x + 8, -y, z + 8),
                                Blocks.DEEPSLATE.getDefaultState(), 3);
                    }
                }
            }
        }
        if (random.chance(0.4)) {
            world.setBlockState(origin.add(8, 1, 8), Blocks.SOUL_TORCH.getDefaultState(), 3);
        }
    }

    /** Stairs that go up and stop. */
    private static void brokenStair(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random) {
        int steps = random.between(8, 16);
        for (int i = 0; i < steps; i++) {
            world.setBlockState(origin.add(8, i, 8 + i / 2),
                    Blocks.DEEPSLATE_BRICK_STAIRS.getDefaultState()
                            .with(net.minecraft.block.StairsBlock.FACING, Direction.SOUTH), 3);
        }
    }

    /** A chunk that was not finished. Half of it is simply absent. */
    private static void halfChunk(ServerWorld world, BlockPos origin, RandomUtil.UncannyRandom random) {
        int split = random.between(4, 11);
        for (int x = 0; x < split; x++) {
            for (int z = 0; z < 16; z++) {
                world.setBlockState(origin.add(x, -1, z), Blocks.DEEPSLATE.getDefaultState(), 3);
            }
        }
    }

    /**
     * The door.
     *
     * It is a door, in a frame, standing on a platform, with a sign on it. Walking
     * through it takes the player to the Prison of Stars. There is no lock, no key,
     * no sound, and nothing on the other side that wants them.
     */
    private static void theDoor(ServerWorld world, BlockPos origin) {
        // A platform to stand on.
        for (int x = 4; x <= 11; x++) {
            for (int z = 4; z <= 11; z++) {
                world.setBlockState(origin.add(x, -1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState(), 3);
            }
        }
        BlockPos door = origin.add(8, 0, 8);
        world.setBlockState(door, Blocks.IRON_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.FACING, Direction.SOUTH), 3);
        world.setBlockState(door.up(), Blocks.IRON_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.FACING, Direction.SOUTH)
                .with(net.minecraft.block.DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER), 3);
        world.setBlockState(door.up(2), Blocks.POLISHED_DEEPSLATE.getDefaultState(), 3);

        BlockPos sign = door.up(2).north();
        world.setBlockState(sign, Blocks.OAK_WALL_SIGN.getDefaultState()
                .with(net.minecraft.block.WallSignBlock.FACING, Direction.SOUTH), 3);
        if (world.getBlockEntity(sign) instanceof net.minecraft.block.entity.SignBlockEntity entity) {
            entity.setTextOnRow(0, net.minecraft.text.Text.literal("ABYSS"));
        }

        // The marker makes the doorway work.
        world.setBlockState(door.north(), UncannyBlocks.SURVEY_MARKER.getDefaultState(), 3);
        DimensionTransitionManager.registerDoorway(world, door.north(), UncannyDimension.PARTITION);
    }
}
