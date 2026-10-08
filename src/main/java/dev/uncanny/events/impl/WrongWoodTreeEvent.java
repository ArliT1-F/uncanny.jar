package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.BlockVariant;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * A tree with the wrong wood.
 *
 * Not a missing tree and not an extra one - the tree that has always been there
 * is suddenly a different species. The shape is right, the position is right,
 * and one detail disagrees with the player's memory of the woods. There is no
 * sound and no effect: if they do not look up from what they were doing, the
 * change simply becomes the truth.
 */
public class WrongWoodTreeEvent extends UncannyEvent {

    public WrongWoodTreeEvent() {
        super("wrong_wood_tree", Severity.QUIET);
        family(Family.FALSE_NORMALITY);
        when(EventCondition.and(
                EventCondition.overworld(),
                EventCondition.playedMinutes(25),
                EventCondition.inForest(),
                EventCondition.treeNearby(6)));
        chance(0.2);
        fromInstability(0.05);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos trunk = EventCondition.findTree(context, 6);
        if (trunk == null) {
            return;
        }
        BlockState log = context.world.getBlockState(trunk);
        Block swapped = BlockVariant.woodVariantOf(log.getBlock(), context.random());
        if (swapped == null) {
            return;
        }
        PositionUtil.setQuietly(context.world, trunk, swapped.getDefaultState());

        // The crown follows the trunk - but only one block of it, because a whole
        // crown changing species is a spectacle. Walk up the rest of the trunk
        // first: findTree can land on any log of the tree.
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos above = trunk.up(dy);
            BlockState crown = context.world.getBlockState(above);
            if (crown.isAir()) {
                return;
            }
            boolean isLog = crown.isOf(Blocks.OAK_LOG) || crown.isOf(Blocks.BIRCH_LOG)
                    || crown.isOf(Blocks.SPRUCE_LOG) || crown.isOf(Blocks.DARK_OAK_LOG);
            if (isLog) {
                continue; // still climbing the trunk
            }
            if (crown.isOf(Blocks.OAK_LEAVES) || crown.isOf(Blocks.BIRCH_LEAVES)
                    || crown.isOf(Blocks.SPRUCE_LEAVES) || crown.isOf(Blocks.DARK_OAK_LEAVES)) {
                String path = BlockVariant.pathOf(swapped).replace("_log", "_leaves");
                Block leaves = BlockVariant.byId(path);
                if (leaves != Blocks.AIR) {
                    PositionUtil.setQuietly(context.world, above, leaves.getDefaultState());
                }
            }
            return;
        }
    }
}
