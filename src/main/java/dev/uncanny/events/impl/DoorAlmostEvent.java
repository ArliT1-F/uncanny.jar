package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.Scheduler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.util.math.BlockPos;

/**
 * A door that starts to change and does not.
 *
 * The door swings partway through a state change - visibly, if the player is
 * looking - and then is back where it was four ticks later, with no sound
 * attached to either movement. If they were watching, they saw it. If they were
 * not, nothing happened. Both readings have to stay possible.
 */
public class DoorAlmostEvent extends UncannyEvent {

    public DoorAlmostEvent() {
        super("door_almost", Severity.QUIET);
        family(Family.NEAR_MISS);
        nearMiss();
        when(EventCondition.and(
                EventCondition.playedMinutes(15),
                EventCondition.doorNearby(8)));
        chance(0.15);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos door = EventCondition.findDoor(context, 8);
        if (door == null) {
            return;
        }
        BlockState original = context.world.getBlockState(door);
        if (!(original.getBlock() instanceof DoorBlock)) {
            return;
        }
        boolean open = original.get(DoorBlock.OPEN);
        // Swing it, notify so the animation plays, then put it back four ticks
        // later - quietly, with no sound either way.
        BlockState flipped = original.with(DoorBlock.OPEN, !open);
        context.world.setBlockState(door, flipped, Block.NOTIFY_LISTENERS);
        Scheduler.runAt(context.tick + 4, () -> {
            if (context.world.getBlockState(door) == flipped) {
                context.world.setBlockState(door, original, Block.NOTIFY_LISTENERS);
            }
        });
    }
}
