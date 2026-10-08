package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.net.UncannyPayloads;

/**
 * Nothing happens.
 *
 * The mod asks the client to go quiet for a while. There is no sound, no message,
 * no visual, and the player is not told that anything has started. Most players
 * will never notice this event, and that is the design: an anomaly whose only
 * observable effect is that the world stopped making its usual noise.
 */
public class SilenceEvent extends UncannyEvent {

    public SilenceEvent() {
        super("silence", Severity.QUIET);
        when(EventCondition.playedMinutes(10));
        chance(0.4);
    }

    @Override
    protected void perform(EventContext context) {
        UncannyPayloads.sendVisual(context.player, UncannyPayloads.VISUAL_DIM, 1.0F);
        // No clue is recorded. The player did not notice anything, so nothing
        // happened to them, and the Ledger should not claim otherwise.
    }
}
