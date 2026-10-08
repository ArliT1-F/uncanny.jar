package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.net.UncannyPayloads;

/**
 * The sky, briefly, is not the sky.
 *
 * A client-side effect only. Nothing is changed in the world, which means a player
 * who looks away and back will find everything exactly as it should be, and will
 * have no evidence at all.
 */
public class SkyGlitchEvent extends UncannyEvent {

    public SkyGlitchEvent() {
        super("sky_glitch", Severity.NOTICED);
        when(EventCondition.and(
                EventCondition.overworld(),
                EventCondition.underOpenSky(),
                EventCondition.night()));
        chance(0.18);
        fromStage(3);
    }

    @Override
    protected void perform(EventContext context) {
        UncannyPayloads.sendVisual(context.player, UncannyPayloads.VISUAL_SKY,
                0.4F + context.random().nextFloat() * 0.4F);
        context.data.markClue("sky_wrong");
    }
}
