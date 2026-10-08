package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * A torch that is no longer there.
 *
 * Nothing is said and nothing is heard. The player notices because the room is
 * darker than it was, and the most common reaction is to assume they never placed
 * it - which is the reaction the mod is fishing for.
 */
public class MissingLightEvent extends UncannyEvent {

    public MissingLightEvent() {
        super("missing_light", Severity.QUIET);
        when(EventCondition.and(
                EventCondition.playedMinutes(15),
                EventCondition.underground()));
        chance(0.3);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        // A small, bounded search for the nearest player-placed light.
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    if (context.world.getBlockState(pos).isOf(Blocks.TORCH)) {
                        PositionUtil.clearQuietly(context.world, pos);
                        context.data.markClue("light_missing");
                        return;
                    }
                }
            }
        }
    }
}
