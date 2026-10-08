package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.BlockVariant;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

/**
 * A staircase with one additional step.
 *
 * The step is a bottom slab of the same material, one block further along than
 * the staircase goes, sitting on ground that was always there. It is the least
 * dramatic thing in the mod and one of the hardest to disprove: the staircase is
 * one step longer than the player remembers, and it has always been that way.
 */
public class ExtraStepEvent extends UncannyEvent {

    public ExtraStepEvent() {
        super("extra_step", Severity.QUIET);
        family(Family.FALSE_NORMALITY);
        when(EventCondition.and(
                EventCondition.playedMinutes(12),
                EventCondition.stairsNearby(5)));
        chance(0.18);
        fromInstability(0.05);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos stairs = EventCondition.findStairsWithRoom(context, 5);
        if (stairs == null) {
            return;
        }
        var state = context.world.getBlockState(stairs);
        Block slab = BlockVariant.slabFor(state.getBlock());
        if (slab == null) {
            return;
        }
        BlockPos target = stairs.offset(state.get(Properties.HORIZONTAL_FACING));
        if (!context.world.getBlockState(target).isAir()
                || context.world.getBlockState(target.down()).isAir()) {
            return;
        }
        PositionUtil.setQuietly(context.world, target, slab.getDefaultState());
    }
}
