package dev.uncanny.block;

import dev.uncanny.data.UncannyWorldState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A seal, in the physical sense: a plate set into a wall that shows how much of a
 * boundary is still holding.
 *
 * The property is how many seals have given way, 0..7. It is a display, not a
 * control - the player cannot repair a seal and the mod never suggests they can.
 * The plate only ever gets worse, and only ever when the world decides.
 */
public class SealPlateBlock extends Block {

    public static final IntProperty OPENED = IntProperty.of("opened", 0, UncannyWorldState.SEAL_COUNT);

    public SealPlateBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(OPENED, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(OPENED);
    }

    /** Brings the plate in line with the world. Called by the seal manager. */
    public static void sync(World world, BlockPos pos, UncannyWorldState state) {
        BlockState current = world.getBlockState(pos);
        if (current.contains(OPENED)) {
            world.setBlockState(pos, current.with(OPENED, state.openedSealCount()), Block.NOTIFY_ALL);
        }
    }

    /**
     * Right-clicking a seal plate tells the player, in the only language the mod
     * uses for this, how many boundaries are open. No chat message: the plate
     * simply updates, and the player learns to look at it.
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world instanceof ServerWorld serverWorld) {
            sync(serverWorld, pos, UncannyWorldState.get(serverWorld));
            return ActionResult.SUCCESS;
        }
        return ActionResult.CONSUME;
    }
}
