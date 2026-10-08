package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * The grave is gone.
 *
 * It runs only after {@link WrongGraveEvent} has placed one, and only once the
 * player has had time to think about it. The ground is left as it was, so there is
 * no hole and no disturbed earth: the correct explanation - that the player
 * imagined it - is available, and unprovable either way.
 */
public class GraveGoneEvent extends UncannyEvent {

    public GraveGoneEvent() {
        super("grave_gone", Severity.MAJOR);
        when(EventCondition.and(
                EventCondition.overworld(),
                GraveGoneEvent::graveExists));
        chance(0.4);
        fromStage(5);
        onlyOnce();
    }

    private static boolean graveExists(EventContext context) {
        return context.data.markPos("grave") != null;
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos sign = context.data.markPos("grave");
        if (sign == null) {
            return;
        }
        // Sign, headstone, mound. In that order, so nothing floats mid-removal.
        PositionUtil.setQuietly(context.world, sign, Blocks.AIR.getDefaultState());
        PositionUtil.setQuietly(context.world, sign.down(), Blocks.AIR.getDefaultState());
        BlockPos mound = sign.down().south();
        PositionUtil.setQuietly(context.world, mound, Blocks.GRASS_BLOCK.getDefaultState());
        PositionUtil.setQuietly(context.world, mound.up(), Blocks.AIR.getDefaultState());

        context.data.marks.remove("grave");
        context.data.markClue("grave_gone");
    }
}
