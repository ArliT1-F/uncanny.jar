package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.Scheduler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * A structure that briefly appears incorrect, then is not.
 *
 * One block of something solid within a few cubes goes air for six ticks and
 * returns with its original state. Long enough to catch peripherally, far too
 * short to walk over and verify. No particles, no sound: the event borrows
 * nothing from Minecraft's vocabulary for "effect", because there is no effect.
 * There is only a gap in the shape of the world, closing.
 */
public class StructureFlickerEvent extends UncannyEvent {

    public StructureFlickerEvent() {
        super("structure_flicker", Severity.QUIET);
        family(Family.NEAR_MISS);
        nearMiss();
        when(EventCondition.and(
                EventCondition.playedMinutes(15),
                EventCondition.named("no structure nearby",
                        context -> findFlickerTarget(context) != null)));
        chance(0.12);
    }

    /** One solid, entity-free block within four, never the block underfoot. */
    private static BlockPos findFlickerTarget(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 3) {
                        continue; // too close to matter, or inside the player
                    }
                    BlockPos pos = centre.add(dx, dy, dz);
                    BlockState state = context.world.getBlockState(pos);
                    if (state.isAir() || state.isOf(Blocks.BEDROCK)
                            || state.isOf(Blocks.WATER)) {
                        continue;
                    }
                    if (context.world.getBlockEntity(pos) instanceof BlockEntity) {
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
        BlockPos target = findFlickerTarget(context);
        if (target == null) {
            return;
        }
        BlockState original = context.world.getBlockState(target);
        if (original.isAir()) {
            return;
        }
        context.world.setBlockState(target, Blocks.AIR.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        Scheduler.runAt(context.tick + 6, () -> {
            if (context.world.getBlockState(target).isAir()) {
                context.world.setBlockState(target, original,
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
        });
    }
}
