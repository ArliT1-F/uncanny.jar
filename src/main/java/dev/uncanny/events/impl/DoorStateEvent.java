package dev.uncanny.events.impl;

import dev.uncanny.audio.UncannySounds;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.util.math.BlockPos;

/**
 * A door that was closed is open. Or the other way.
 *
 * The sound is played after the change, not before, and it comes from the door
 * itself. A player who checks the door finds it already in its new state, which is
 * what makes them doubt the memory rather than the door.
 */
public class DoorStateEvent extends UncannyEvent {

    public DoorStateEvent() {
        super("door_state", Severity.QUIET);
        family(Family.ARCHITECTURAL);
        when(EventCondition.and(
                EventCondition.playedMinutes(10),
                EventCondition.doorNearby(10)));
        chance(0.3);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos door = EventCondition.findDoor(context, 10);
        if (door == null) {
            return;
        }
        var state = context.world.getBlockState(door);
        boolean open = state.get(DoorBlock.OPEN);
        context.world.setBlockState(door, state.with(DoorBlock.OPEN, !open), Block.NOTIFY_ALL);

        // The sound of a door that has already moved.
        UncannySounds.schedule(context.server, context.tick + 6, context.world, door,
                open ? net.minecraft.sound.SoundEvents.BLOCK_WOODEN_DOOR_CLOSE
                        : net.minecraft.sound.SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
                0.6F, 0.9F);
        context.data.markClue("door_changed");
    }
}
