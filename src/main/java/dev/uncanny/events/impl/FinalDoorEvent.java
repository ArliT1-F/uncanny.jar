package dev.uncanny.events.impl;

import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.DimensionTransitionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.RandomUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * The last thing that happens.
 *
 * After the observation closes, a door appears in the Partition. It is the door to
 * the player's own house - the same material, the same colour, the same side of the
 * wall - and it opens onto the Overworld.
 *
 * The player is not pushed through it. Nothing happens if they do not use it. They
 * can stand in the room where they were declared vacant for as long as they like,
 * and the door will wait.
 */
public class FinalDoorEvent extends UncannyEvent {

    public FinalDoorEvent() {
        super("final_door", Severity.MAJOR);
        when(EventCondition.and(
                EventCondition.inDimension(UncannyDimension.PARTITION),
                FinalDoorEvent::observationClosed));
        chance(1.0);
        onlyOnce();
    }

    private static boolean observationClosed(EventContext context) {
        return context.data.observationComplete;
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        RandomUtil.UncannyRandom random = context.random();
        BlockPos door = centre.offset(random.pick(java.util.List.of(
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)), 4);

        // The frame, in whatever the player builds with most often.
        net.minecraft.block.Block frame = net.minecraft.registry.Registries.BLOCK.get(
                new net.minecraft.util.Identifier(context.data.home.dominantMaterial()));
        if (frame == Blocks.AIR) {
            frame = Blocks.OAK_PLANKS;
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 3; y++) {
                BlockPos at = door.offset(Direction.WEST, x).up(y);
                if (x == -1 || x == 1 || y == 2) {
                    context.world.setBlockState(at, frame.getDefaultState(), 3);
                } else {
                    context.world.setBlockState(at, Blocks.AIR.getDefaultState(), 3);
                }
            }
        }

        context.world.setBlockState(door, Blocks.OAK_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH)
                .with(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        context.world.setBlockState(door.up(), Blocks.OAK_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH)
                .with(DoorBlock.OPEN, false)
                .with(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

        DimensionTransitionManager.registerDoorway(context.world, door, UncannyDimension.OVERWORLD);
        context.data.markPos("final_door", door);
        context.data.markClue("final_door");
    }

    /** True when the player has been shown the last page and not yet left. */
    public static boolean standingInPartition(EventContext context) {
        return DimensionManager.current(context.player) == UncannyDimension.PARTITION;
    }
}
