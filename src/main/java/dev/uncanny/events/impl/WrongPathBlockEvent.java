package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * A familiar path with one incorrect block.
 *
 * One block of a dirt path has become coarse dirt. It is the kind of thing a
 * player patches without thinking, and the kind of thing they are certain they
 * already patched last week. No sound, no effect, no clue recorded: if they
 * never notice, the world keeps the change anyway.
 */
public class WrongPathBlockEvent extends UncannyEvent {

    public WrongPathBlockEvent() {
        super("wrong_path_block", Severity.QUIET);
        family(Family.FALSE_NORMALITY);
        when(EventCondition.and(
                EventCondition.playedMinutes(20),
                EventCondition.pathNearby(6)));
        chance(0.18);
        fromInstability(0.05);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos path = EventCondition.findDirtPath(context, 6);
        if (path == null) {
            return;
        }
        PositionUtil.setQuietly(context.world, path, Blocks.COARSE_DIRT.getDefaultState());
    }
}
