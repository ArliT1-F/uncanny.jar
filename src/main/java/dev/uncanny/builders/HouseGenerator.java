package dev.uncanny.builders;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.player.HomeFingerprint;
import dev.uncanny.player.UncannyPlayerData;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;
import dev.uncanny.util.SignUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;

/**
 * THE HOUSE.
 *
 * Built from what the mod remembers about one player: what they placed, how often,
 * where they slept, what they kept in chests. It is not a copy of their house,
 * because the mod never scanned their house. It is a house that could only have
 * been built by someone who has been watching them build.
 *
 * One room in it does not exist in the original. The mod does not say which.
 */
public final class HouseGenerator {

    private HouseGenerator() {
    }

    public static void generateChunk(MinecraftServer server, ServerWorld world,
                                     UncannyDimension dimension, ChunkPos chunk) {
        UncannyWorldState state = UncannyWorldState.get(server);
        if (state.isChunkGenerated(dimension.path(), chunk)) {
            return;
        }

        UncannyPlayerData subject = subjectOf(state);
        long seed = SeedUtil.forChunk(world.getSeed(), DimensionManager.seedOf(dimension, server),
                chunk.x, chunk.z);
        BlockPos origin = new BlockPos(chunk.getStartX(), dimension.connectorY(), chunk.getStartZ());

        // The floor, everywhere. A house needs a floor even where it has no walls.
        fill(world, origin.add(0, -1, 0), origin.add(15, -1, 15), Blocks.OAK_PLANKS.getDefaultState());

        if (chunk.x == 0 && chunk.z == 0) {
            buildHouse(world, origin, subject, seed);
        } else if (chunk.x == 1 && chunk.z == 0) {
            buildTheExtraRoom(world, origin, subject, seed);
        } else if (Math.abs(chunk.x) <= 3 && Math.abs(chunk.z) <= 3) {
            scatterYard(world, origin, subject, seed);
        }

        state.markChunkGenerated(dimension.path(), chunk);
        state.markDirty();
    }

    /** Whose house this is. The first player to progress becomes the subject. */
    private static UncannyPlayerData subjectOf(UncannyWorldState state) {
        var subject = state.subject();
        if (subject != null && state.hasPlayer(subject)) {
            return state.player(subject);
        }
        for (UncannyPlayerData data : state.allPlayers()) {
            return data;
        }
        return null;
    }

    private static void buildHouse(ServerWorld world, BlockPos origin, UncannyPlayerData subject, long seed) {
        HomeFingerprint home = subject != null ? subject.home : new HomeFingerprint();
        Block wall = materialFor(home);
        RandomUtil.UncannyRandom random = RandomUtil.of(seed);

        // Walls, 9 by 4 by 11.
        for (int x = 3; x < 12; x++) {
            for (int z = 2; z < 13; z++) {
                boolean edge = x == 3 || x == 11 || z == 2 || z == 12;
                for (int y = 0; y < 4; y++) {
                    BlockPos at = origin.add(x, y, z);
                    if (edge || y == 3) {
                        world.setBlockState(at, y == 3 ? wall.getDefaultState()
                                : wall.getDefaultState(), 3);
                    } else {
                        world.setBlockState(at, Blocks.AIR.getDefaultState(), 3);
                    }
                }
                world.setBlockState(origin.add(x, -1, z), Blocks.OAK_PLANKS.getDefaultState(), 3);
            }
        }

        // Two windows and a door, where a house would put them.
        world.setBlockState(origin.add(5, 2, 2), Blocks.GLASS.getDefaultState(), 3);
        world.setBlockState(origin.add(9, 2, 2), Blocks.GLASS.getDefaultState(), 3);
        world.setBlockState(origin.add(7, 0, 2), Blocks.AIR.getDefaultState(), 3);
        world.setBlockState(origin.add(7, 1, 2), Blocks.AIR.getDefaultState(), 3);
        world.setBlockState(origin.add(7, 0, 2), Blocks.OAK_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.FACING, Direction.SOUTH), 3);
        world.setBlockState(origin.add(7, 1, 2), Blocks.OAK_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.FACING, Direction.SOUTH)
                .with(net.minecraft.block.DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER), 3);

        // A bed, if the player has ever slept. Where they slept, more or less.
        if (home.bedPosition != null) {
            world.setBlockState(origin.add(9, 0, 10), Blocks.RED_BED.getDefaultState(), 3);
            world.setBlockState(origin.add(9, 1, 10), Blocks.TORCH.getDefaultState(), 3);
        }

        // Storage. The count comes from what the mod recorded.
        int chests = Math.min(4, Math.max(1, home.containers.size()));
        for (int i = 0; i < chests; i++) {
            world.setBlockState(origin.add(4 + i, 0, 11), Blocks.CHEST.getDefaultState(), 3);
        }
        if (chests > 0 && world.getBlockEntity(origin.add(4, 0, 11))
                instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
            ItemStack stack = new ItemStack(itemFor(home));
            stack.setCustomName(Text.literal("YOURS"));
            chest.setStack(13, stack);
        }

        // A light, and a table.
        world.setBlockState(origin.add(7, 3, 7), Blocks.LANTERN.getDefaultState(), 3);
        world.setBlockState(origin.add(6, 0, 6), Blocks.CRAFTING_TABLE.getDefaultState(), 3);

        if (random.chance(0.5)) {
            world.setBlockState(origin.add(10, 0, 6), Blocks.BOOKSHELF.getDefaultState(), 3);
        }
    }

    /**
     * The room that is not in the original.
     *
     * It is furnished exactly like the rest of the house, which is what makes it
     * impossible to dismiss. The bed is made. There is a chair facing the wall.
     */
    private static void buildTheExtraRoom(ServerWorld world, BlockPos origin,
                                          UncannyPlayerData subject, long seed) {
        HomeFingerprint home = subject != null ? subject.home : new HomeFingerprint();
        Block wall = materialFor(home);
        for (int x = 0; x < 8; x++) {
            for (int z = 4; z < 12; z++) {
                boolean edge = x == 0 || x == 7 || z == 4 || z == 11;
                for (int y = 0; y < 4; y++) {
                    world.setBlockState(origin.add(x, y, z),
                            edge ? wall.getDefaultState() : Blocks.AIR.getDefaultState(), 3);
                }
                world.setBlockState(origin.add(x, -1, z), Blocks.OAK_PLANKS.getDefaultState(), 3);
            }
        }
        world.setBlockState(origin.add(0, 0, 7), Blocks.AIR.getDefaultState(), 3);
        world.setBlockState(origin.add(0, 1, 7), Blocks.AIR.getDefaultState(), 3);
        world.setBlockState(origin.add(5, 0, 9), Blocks.RED_BED.getDefaultState(), 3);
        world.setBlockState(origin.add(2, 0, 5), Blocks.OAK_STAIRS.getDefaultState(), 3);
        world.setBlockState(origin.add(4, 3, 8), Blocks.TORCH.getDefaultState(), 3);
        // A tally. Longer than any of the others.
        world.setBlockState(origin.add(6, 1, 5), Blocks.OAK_SIGN.getDefaultState(), 3);
        if (world.getBlockEntity(origin.add(6, 1, 5))
                instanceof net.minecraft.block.entity.SignBlockEntity sign) {
            SignUtil.setLines(sign, "||||||||||||||||", "");
        }
    }

    /** A yard. A few of the things the player places most, standing outside. */
    private static void scatterYard(ServerWorld world, BlockPos origin,
                                    UncannyPlayerData subject, long seed) {
        HomeFingerprint home = subject != null ? subject.home : new HomeFingerprint();
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "yard"));
        int count = random.between(1, 4);
        for (int i = 0; i < count; i++) {
            int x = random.between(1, 14);
            int z = random.between(1, 14);
            Block block = Registries.BLOCK.get(new Identifier(home.dominantMaterial()));
            if (block == Blocks.AIR) {
                block = Blocks.OAK_LOG;
            }
            world.setBlockState(origin.add(x, 0, z), block.getDefaultState(), 3);
        }
    }

    private static Block materialFor(HomeFingerprint home) {
        Block block = Registries.BLOCK.get(new Identifier(home.dominantMaterial()));
        return block == Blocks.AIR ? Blocks.OAK_PLANKS : block;
    }

    private static net.minecraft.item.Item itemFor(HomeFingerprint home) {
        if (home.firstSignificantItem != null) {
            var item = Registries.ITEM.get(new Identifier(home.firstSignificantItem));
            if (item != Items.AIR) {
                return item;
            }
        }
        return Items.CLOCK;
    }

    private static void fill(ServerWorld world, BlockPos from, BlockPos to,
                             net.minecraft.block.BlockState state) {
        for (int x = from.getX(); x <= to.getX(); x++) {
            for (int y = from.getY(); y <= to.getY(); y++) {
                for (int z = from.getZ(); z <= to.getZ(); z++) {
                    world.setBlockState(new BlockPos(x, y, z), state, 3);
                }
            }
        }
    }
}
