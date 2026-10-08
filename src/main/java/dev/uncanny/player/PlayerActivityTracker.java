package dev.uncanny.player;

import dev.uncanny.data.UncannyWorldState;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/**
 * Watching the player build, without watching the world.
 *
 * The mod needs to know what a player's home looks like, and the only cheap way to
 * find out is to notice while it happens. So:
 *
 *   - when they place a block, remember what kind of block it was;
 *   - when they break one, count it;
 *   - when they use a chest, remember that there is a chest.
 *
 * That is a handful of map writes over an entire session. It is also more accurate
 * than scanning, because it records intent: a block the player placed is theirs,
 * while a block a scan found might be a village.
 */
public final class PlayerActivityTracker {

    private PlayerActivityTracker() {
    }

    /** Called from the use-block callback, on the server, for every right click. */
    public static void onUseBlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Hand hand) {
        UncannyWorldState state = UncannyWorldState.get(world);
        UncannyPlayerData data = state.player(player.getUuid());

        var stack = player.getStackInHand(hand);
        if (stack.getItem() instanceof BlockItem blockItem) {
            var id = net.minecraft.registry.Registries.BLOCK.getId(blockItem.getBlock());
            data.home.recordPlacement(id.toString());

            // The first interesting thing the player picked up. "Interesting" is
            // deliberately vague: anything that is not a building block counts.
            if (data.home.firstSignificantItem == null && !isBuildingMaterial(id.getPath())) {
                data.home.firstSignificantItem = net.minecraft.registry.Registries.ITEM
                        .getId(stack.getItem()).toString();
            }
            state.markDirty();
        }

        BlockState state2 = world.getBlockState(pos);
        if (state2.getBlock() instanceof DoorBlock) {
            // Frequently used doors are behaviour the mod can lean on later:
            // a door you live behind is a door an anomaly can afford to touch.
            data.memory.noteDoorUse(pos);
            state.markDirty();
        }
        if (state2.getBlock() instanceof ChestBlock) {
            int items = 0;
            if (world.getBlockEntity(pos) instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
                for (int slot = 0; slot < chest.size(); slot++) {
                    if (!chest.getStack(slot).isEmpty()) {
                        items++;
                    }
                }
            }
            data.home.recordContainer(net.minecraft.registry.Registries.BLOCK
                    .getId(state2.getBlock()).getPath() + ", " + items + " items");
            state.markDirty();
        }
    }

    /** Called when a player breaks a block. The position and block are kept for
     * Reality Echoes: this is the only record of what the player actually did. */
    public static void onBreakBlock(ServerPlayerEntity player, ServerWorld world,
                                    BlockPos pos, BlockState state) {
        UncannyWorldState worldState = UncannyWorldState.get(world);
        UncannyPlayerData data = worldState.player(player.getUuid());
        data.home.recordRemoval();
        String blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
        data.memory.noteRemoval(pos, blockId, world.getTime());
        // Breaking is common; only write to disk occasionally. The ring fills
        // after 16 breaks, so that is the first moment the record can change shape.
        if (data.home.removals <= 16 || data.home.removals % 16 == 0) {
            worldState.markDirty();
        }
    }

    /** Rough filter so the "significant item" is not another cobblestone. */
    private static boolean isBuildingMaterial(String path) {
        return path.endsWith("_planks") || path.endsWith("_log") || path.endsWith("_cobblestone")
                || path.equals("dirt") || path.equals("stone") || path.equals("gravel")
                || path.equals("sand") || path.endsWith("_bricks") || path.endsWith("_concrete")
                || path.endsWith("_wool") || path.endsWith("_terracotta") || path.endsWith("_glass");
    }
}
