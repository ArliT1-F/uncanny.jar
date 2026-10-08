package dev.uncanny.events.impl;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.item.UncannyBlocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * A plate in a wall, showing how many boundaries are open.
 *
 * The mod never explains the plate. It appears in a corridor the player has
 * already walked down, it is made of the same stone as the corridor, and it has
 * seven marks on it. Players who come back to it later find that the marks have
 * changed, which is the only information the mod is willing to give.
 */
public class SealPlateEvent extends UncannyEvent {

    public SealPlateEvent() {
        super("seal_plate", Severity.NOTICED);
        when(EventCondition.and(
                EventCondition.or(
                        EventCondition.inDimension(UncannyDimension.HALL),
                        EventCondition.inDimension(UncannyDimension.ARCHIVE)),
                EventCondition.stage(3)));
        chance(0.35);
        fromStage(3);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        // Attach it to the nearest solid block at eye height. Bounded search.
        for (int radius = 2; radius <= 5; radius++) {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos at = centre.offset(direction, radius);
                if (!context.world.getBlockState(at).isAir()
                        && context.world.getBlockState(at.offset(direction.getOpposite())).isAir()) {
                    context.world.setBlockState(at, UncannyBlocks.SEAL_PLATE.getDefaultState()
                            .with(UncannyBlocks.OPENED, context.state.openedSealCount()), 3);
                    context.data.markClue("seal_plate_seen");
                    return;
                }
            }
        }
        // No wall nearby: put it on the floor. It will still be found.
        context.world.setBlockState(centre.offset(Direction.NORTH, 2),
                UncannyBlocks.SEAL_PLATE.getDefaultState(), 3);
        context.data.markClue("seal_plate_seen");
    }

    /** Kept here so the intent is documented next to the block it uses. */
    static UncannyWorldState stateOf(EventContext context) {
        return DimensionManager.current(context.player) == UncannyDimension.PARTITION
                ? context.state : context.state;
    }
}
