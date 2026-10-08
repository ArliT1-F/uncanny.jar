package dev.uncanny.events.impl;

import dev.uncanny.audio.UncannySounds;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import net.minecraft.util.math.BlockPos;

/**
 * Three knocks.
 *
 * Placed on whatever is behind the player, which in a corridor means a wall. The
 * spacing is real: three scheduled sounds, not one sound played three times, so
 * the player hears a rhythm rather than a stutter.
 *
 * This is the sound the anchor journals describe, and the mod never connects them.
 */
public class DistantKnockEvent extends UncannyEvent {

    public DistantKnockEvent() {
        super("distant_knock", Severity.NOTICED);
        when(EventCondition.and(
                EventCondition.playedMinutes(25),
                EventCondition.or(EventCondition.underground(), EventCondition.inDimension(
                        dev.uncanny.dimension.UncannyDimension.HALL))));
        chance(0.2);
        fromStage(2);
    }

    @Override
    protected void perform(EventContext context) {
        var look = context.player.getRotationVector();
        BlockPos target = BlockPos.ofFloored(context.player.getPos().subtract(look.multiply(4.0)));
        UncannySounds.knock(context.server, context.world, target);
        context.data.markClue("knocking");
    }
}
