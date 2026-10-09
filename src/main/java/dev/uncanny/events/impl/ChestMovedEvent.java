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
 *
 * If this is the chest a near miss already selected (see SelectedTargetEvent),
 * the rotation is a full about-face instead of a quarter turn: an interrupted
 * attempt, finishing later, on its own.
 */
public class ChestMovedEvent extends UncannyEvent {

    public ChestMovedEvent() {
        super("chest_moved", Severity.NOTICED);
        family(Family.ARCHITECTURAL);
        when(EventCondition.and(
                EventCondition.playedMinutes(20),
                EventCondition.chestNearby(6)));
        chance(0.2);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos chest = EventCondition.findChest(context, 6);
        if (chest == null) {
            return;
        }
        BlockState state = context.world.getBlockState(chest);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return;
        }
        boolean armed = chest.equals(context.data.markPos("near_miss_target"));
        Direction facing = state.get(ChestBlock.FACING);
        Direction turned = facing.rotateYClockwise();
        if (armed) {
            // The attempt that was selected but never happened.
            turned = turned.rotateYClockwise();
            context.data.markPos("near_miss_target", null);
        }
        context.world.setBlockState(chest, state.with(ChestBlock.FACING, turned), 3);
        context.data.markClue("chest_rotated");
    }
}
