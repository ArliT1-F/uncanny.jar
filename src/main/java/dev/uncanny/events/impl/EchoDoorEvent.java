package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * REALITY ECHO: another version of the player's door, nearby.
 *
 * The player builds a door; some time later, a few blocks away, there is a
 * second door of the same kind, facing the same way, standing in open air with
 * no frame. It does not lead anywhere. It is not a threshold (nothing is
 * registered - this is not a way in, it is a leftover).
 *
 * This is duplication as the Copy layer practices it: almost but not exactly
 * identical, and useless in exactly the way the original is useful.
 */
public class EchoDoorEvent extends UncannyEvent {

    public EchoDoorEvent() {
        super("echo_door", Severity.NOTICED);
        family(Family.ECHO);
        when(EventCondition.and(
                EventCondition.playedMinutes(45),
                EventCondition.named("no door nearby",
                        context -> EventCondition.findDoor(context, 10) != null),
                EventCondition.named("no room beside it",
                        context -> findCopySpot(context) != null)));
        chance(0.12);
        fromInstability(0.15);
    }

    /** Somewhere near the player (not near the original) to stand a copy. */
    private static BlockPos findCopySpot(EventContext context) {
        ServerWorld world = context.world;
        BlockPos source = EventCondition.findDoor(context, 10);
        if (source == null) {
            return null;
        }
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    if (PositionUtil.distanceSquared(pos, source) < 3.0 * 3.0) {
                        continue;
                    }
                    if (!world.getBlockState(pos).isAir()
                            || !world.getBlockState(pos.up()).isAir()
                            || world.getBlockState(pos.down()).isAir()) {
                        continue;
                    }
                    return pos;
                }
            }
        }
        return null;
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos source = EventCondition.findDoor(context, 10);
        BlockPos target = findCopySpot(context);
        if (source == null || target == null) {
            return;
        }
        BlockState original = context.world.getBlockState(source);
        if (!(original.getBlock() instanceof DoorBlock)) {
            return;
        }
        Direction facing = original.get(Properties.HORIZONTAL_FACING);
        BlockState lower = original.getBlock().getDefaultState()
                .with(Properties.HORIZONTAL_FACING, facing);
        BlockState upper = lower.with(Properties.DOUBLE_BLOCK_HALF,
                net.minecraft.block.enums.DoubleBlockHalf.UPPER);
        PositionUtil.setQuietly(context.world, target, lower);
        PositionUtil.setQuietly(context.world, target.up(), upper);
        context.data.markClue("door_copied");
    }
}
