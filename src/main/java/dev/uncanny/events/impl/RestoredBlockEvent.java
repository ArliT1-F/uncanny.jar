package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.player.RealityInstability;
import dev.uncanny.util.BlockVariant;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * REALITY ECHO: a block the player broke, restored much later.
 *
 * The player mines a block, leaves, comes back an hour later, and the block is
 * there. Not copied into their inventory - the world simply has it again, in
 * the state it was recorded in. This is not a ghost and not a hallucination:
 * it is what the layer across the boundary did with the same position, written
 * back into this one.
 *
 * At MEDIUM instability the block comes back in a slightly wrong wood version -
 * the other layer's version - which is the first time the player can prove the
 * difference to themselves and the first time they should want to.
 *
 * The source is the ring of the last 16 blocks this player broke. The echo only
 * fires when they are 8-48 blocks from it, it is still air, and at least ten
 * minutes have passed: the block comes back when they RETURN, never while they
 * are still digging.
 */
public class RestoredBlockEvent extends UncannyEvent {

    public RestoredBlockEvent() {
        super("restored_block", Severity.NOTICED);
        family(Family.ECHO);
        when(EventCondition.and(
                EventCondition.playedMinutes(40),
                EventCondition.named("no broken block nearby",
                        context -> findEcho(context) >= 0)));
        chance(0.12);
        fromInstability(0.12);
        advanced();
    }

    /** Parses the removal ring; returns the index of the block to restore, or -1. */
    private static int findEcho(EventContext context) {
        ServerWorld world = context.world;
        long now = context.tick;
        BlockPos player = context.player.getBlockPos();
        var removals = context.data.memory.recentRemovals;
        for (int i = removals.size() - 1; i >= 0; i--) {
            String entry = removals.get(i);
            String[] parts = entry.split("\\|", -1);
            if (parts.length != 3) {
                continue;
            }
            try {
                long packed = Long.parseLong(parts[0]);
                long brokeAt = Long.parseLong(parts[2]);
                if (now - brokeAt < 20L * 60 * 10) {
                    continue; // too recent: they are still standing there
                }
                BlockPos pos = BlockPos.fromLong(packed);
                if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    continue;
                }
                double distance = PositionUtil.distanceSquared(pos, player);
                if (distance < 8.0 * 8.0 || distance > 48.0 * 48.0) {
                    continue;
                }
                if (world.getBlockState(pos).isAir()) {
                    return i;
                }
            } catch (NumberFormatException ignored) {
                // A malformed entry is skipped rather than crashing the check.
            }
        }
        return -1;
    }

    @Override
    protected void perform(EventContext context) {
        int index = findEcho(context);
        if (index < 0) {
            return;
        }
        String entry = context.data.memory.recentRemovals.get(index);
        String[] parts = entry.split("\\|", -1);
        if (parts.length != 3) {
            return;
        }
        try {
            BlockPos pos = BlockPos.fromLong(Long.parseLong(parts[0]));
            Block recorded = BlockVariant.byId(parts[1]);
            if (recorded == Blocks.AIR) {
                return;
            }
            Block restored = recorded;
            if (RealityInstability.atLeast(context.data, RealityInstability.Band.MEDIUM)) {
                // The version from the other side: right block, wrong wood.
                Block wrong = BlockVariant.wrongWoodOf(recorded);
                if (wrong != null) {
                    restored = wrong;
                }
            }
            PositionUtil.setQuietly(context.world, pos, restored.getDefaultState());
            context.data.markClue("block_returned");
        } catch (NumberFormatException ignored) {
            // Same as above: skip, never throw into the tick loop.
        }
    }
}
