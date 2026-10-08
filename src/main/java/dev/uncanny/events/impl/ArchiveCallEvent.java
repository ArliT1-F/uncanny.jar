package dev.uncanny.events.impl;

import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.DimensionTransitionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.item.UncannyBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * A door that was not there.
 *
 * The Archive has to be reachable, and the mod refuses to use a portal for it. So
 * at the right moment a doorway is quietly registered near the player: an ordinary
 * door frame, in an ordinary place, that leads somewhere it should not.
 *
 * Whether the player walks through it is entirely their decision, and the mod
 * never prompts them.
 */
public class ArchiveCallEvent extends UncannyEvent {

    public ArchiveCallEvent() {
        super("archive_call", Severity.NOTICED);
        when(EventCondition.and(
                EventCondition.overworld(),
                EventCondition.stage(5),
                EventCondition.dimensionsVisited(2)));
        chance(0.5);
        onlyOnce();
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        BlockPos door = null;
        // Find somewhere a two-block-high frame will fit.
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos at = centre.offset(direction, 3);
            if (context.world.getBlockState(at).isAir()
                    && context.world.getBlockState(at.up()).isAir()
                    && !context.world.getBlockState(at.down()).isAir()) {
                door = at;
                break;
            }
        }
        if (door == null) {
            return;
        }

        // The frame. Deepslate, because it is the material the Hall uses, and
        // because it should look like it belongs to somewhere else.
        for (int y = 0; y < 3; y++) {
            context.world.setBlockState(door.offset(Direction.WEST, 2).up(y),
                    Blocks.POLISHED_DEEPSLATE.getDefaultState(), 3);
            context.world.setBlockState(door.offset(Direction.EAST, 2).up(y),
                    Blocks.POLISHED_DEEPSLATE.getDefaultState(), 3);
        }
        context.world.setBlockState(door.up(2), Blocks.POLISHED_DEEPSLATE.getDefaultState(), 3);
        context.world.setBlockState(door, Blocks.AIR.getDefaultState(), 3);
        context.world.setBlockState(door.up(), Blocks.AIR.getDefaultState(), 3);

        // The marker is what makes the space a doorway. Invisible, and removable:
        // if the player fills the doorway with blocks, it stops being one.
        context.world.setBlockState(door, UncannyBlocks.SURVEY_MARKER.getDefaultState(), 3);
        DimensionTransitionManager.registerDoorway(context.world, door, UncannyDimension.ARCHIVE);

        context.data.markClue("door_appeared");
        context.data.markPos("archive_door", door);
    }

    /** Convenience for the debug command. */
    public static boolean hasDoor(EventContext context) {
        return DimensionManager.current(context.player) == UncannyDimension.OVERWORLD
                && context.data.markPos("archive_door") != null;
    }
}
