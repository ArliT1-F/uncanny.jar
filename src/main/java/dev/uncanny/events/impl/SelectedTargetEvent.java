package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import net.minecraft.block.ChestBlock;
import net.minecraft.util.math.BlockPos;

/**
 * An anomaly target is selected, and nothing happens.
 *
 * The mod picks a chest - this chest, right here - and then does not move it.
 * Nothing changes, ever, by itself. But the target is remembered
 * (marks/near_miss_target), and if chest_moved later happens to choose the same
 * chest, the payoff is a little stronger than usual: an attempt that was
 * interrupted finishes on its own schedule.
 *
 * This is the event that makes the system not feel deterministic. Most players
 * will experience it as nothing at all, which is correct: "did I actually see
 * that?" needs a lot of practice runs of silence.
 */
public class SelectedTargetEvent extends UncannyEvent {

    public SelectedTargetEvent() {
        super("selected_target", Severity.QUIET);
        family(Family.NEAR_MISS);
        nearMiss();
        when(EventCondition.and(
                EventCondition.playedMinutes(20),
                EventCondition.chestNearby(6)));
        chance(0.1);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos chest = EventCondition.findChest(context, 6);
        if (chest == null) {
            return;
        }
        // Armed, and left alone. The world can come back for it later.
        context.data.markPos("near_miss_target", chest);
        context.state.markDirty();
    }
}
