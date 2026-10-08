package dev.uncanny.events.impl;

import dev.uncanny.audio.UncannySounds;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;

/**
 * A footstep behind the player.
 *
 * One step. Never a second, never closer than the first. The player turns around
 * and there is nothing there, and the correct conclusion - that there was nothing
 * there - is the one they will not want to accept.
 */
public class DistantFootstepEvent extends UncannyEvent {

    public DistantFootstepEvent() {
        super("distant_footstep", Severity.QUIET);
        family(Family.AUDITORY);
        when(EventCondition.and(
                EventCondition.playedMinutes(20),
                EventCondition.named("it is raining",
                        EventCondition.not(EventCondition.raining()))));
        chance(0.25);
    }

    @Override
    protected void perform(EventContext context) {
        UncannySounds.behind(context.player, 0.85F + context.random().nextFloat() * 0.2F);
        context.data.markClue("footstep_behind");
    }
}
