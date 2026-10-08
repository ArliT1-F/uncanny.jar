package dev.uncanny.events.impl;

import dev.uncanny.audio.UncannySounds;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * One tree, gone.
 *
 * This is the first anomaly most players will ever experience and almost none of
 * them will notice it happen. The log is removed, the leaves are left, and a
 * footstep is played somewhere out of sight a few seconds later.
 *
 * It is listed first in the design document's example for a reason: it is the
 * cheapest way to make a player stop trusting a place they have already been.
 */
public class MissingTreeEvent extends UncannyEvent {

    public MissingTreeEvent() {
        super("missing_tree", Severity.QUIET);
        when(EventCondition.and(
                EventCondition.overworld(),
                EventCondition.playedMinutes(8),
                EventCondition.inForest(),
                EventCondition.treeNearby(6)));
        chance(0.35);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos tree = EventCondition.findTree(context, 6);
        if (tree == null) {
            return;
        }
        // Remove the trunk but leave the crown. A floating canopy is wrong in a way
        // that is easy to misremember, which is exactly what we want.
        PositionUtil.clearQuietly(context.world, tree);
        PositionUtil.setQuietly(context.world, tree, Blocks.AIR.getDefaultState());

        // Leave the leaves, then take the lowest layer a moment later.
        BlockPos leaves = tree.up(2);
        if (!context.world.getBlockState(leaves).isAir()) {
            UncannySounds.schedule(context.server, context.tick + 40, context.world, leaves,
                    net.minecraft.sound.SoundEvents.BLOCK_GRASS_BREAK, 0.3F, 0.8F);
        }

        UncannySounds.schedule(context.server, context.tick + 60, context.world,
                tree.add(4, 0, 4), net.minecraft.sound.SoundEvents.BLOCK_GRAVEL_STEP, 0.4F, 0.9F);
        context.data.markClue("tree_missing");
    }
}
