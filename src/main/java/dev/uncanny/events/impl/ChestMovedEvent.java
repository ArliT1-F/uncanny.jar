package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * A chest facing the wrong way.
 *
 * Nothing is taken and nothing is added. Rotation is the smallest possible change
 * that a player can verify, which makes it the most effective one: they know
 * exactly which way it was facing.
 */
public class ChestMovedEvent extends UncannyEvent {

    public ChestMovedEvent() {
        super("chest_moved", Severity.NOTICED);
        when(EventCondition.and(EventCondition.playedMinutes(20), ChestMovedEvent::chestNearby));
        chance(0.2);
    }

    /** Bounded search for a chest. Six blocks in each direction, once per check. */
    private static boolean chestNearby(EventContext context) {
        return findChest(context) != null;
    }

    private static BlockPos findChest(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    BlockState state = context.world.getBlockState(pos);
                    if (state.getBlock() instanceof ChestBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos chest = findChest(context);
        if (chest == null) {
            return;
        }
        BlockState state = context.world.getBlockState(chest);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return;
        }
        Direction facing = state.get(ChestBlock.FACING);
        Direction turned = facing.rotateYClockwise();
        context.world.setBlockState(chest, state.with(ChestBlock.FACING, turned), 3);
        context.data.markClue("chest_rotated");
    }
}
