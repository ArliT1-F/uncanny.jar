package dev.uncanny.events.impl;

import dev.uncanny.events.AnomalyChain;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * The final stage of THE TREE: the Woods stops agreeing with itself.
 *
 * A tree in the WOODS grows a trunk that does not connect - a log floating a
 * block above the rest, held up by nothing, with the crown continuing above it.
 * It is the same wrongness as the missing tree, made structural: the place the
 * chain began in is now producing anomalies of its own kind without waiting for
 * a player to break anything.
 *
 * Requires the whole chain. If the player never saw the wrong tree, this stage
 * does not exist for them, and the Woods stays a forest.
 */
public class WoodsConvergenceEvent extends UncannyEvent {

    public WoodsConvergenceEvent() {
        super("woods_convergence", Severity.IMPOSSIBLE);
        family(Family.DIMENSIONAL);
        when(EventCondition.and(
                EventCondition.inDimension(dev.uncanny.dimension.UncannyDimension.WOODS),
                EventCondition.playedMinutes(45),
                EventCondition.treeNearby(7)));
        chance(0.3);
        onlyOnce();
        fromInstability(0.25);
        advanced();
        advances(AnomalyChain.TREE);
        after(AnomalyChain.TREE, "wrong_tree");
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos trunk = EventCondition.findTree(context, 7);
        if (trunk == null) {
            return;
        }
        // Climb to the top of this trunk, then build the broken continuation:
        // log, gap, log, crown. Deterministic, small, permanent.
        BlockPos top = trunk;
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos above = trunk.up(dy);
            if (context.world.getBlockState(above).isAir()) {
                break;
            }
            top = above;
        }
        BlockPos gap = top.up();
        BlockPos floating = top.up(2);
        BlockPos crown = top.up(3);
        if (!context.world.getBlockState(gap).isAir()
                || !context.world.getBlockState(floating).isAir()
                || !context.world.getBlockState(crown).isAir()) {
            return;
        }
        PositionUtil.setQuietly(context.world, floating, Blocks.OAK_LOG.getDefaultState());
        PositionUtil.setQuietly(context.world, crown, Blocks.OAK_LEAVES.getDefaultState());
        for (net.minecraft.util.math.Direction direction
                : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos leaf = crown.offset(direction);
            if (context.world.getBlockState(leaf).isAir()) {
                PositionUtil.setQuietly(context.world, leaf, Blocks.OAK_LEAVES.getDefaultState());
            }
        }
        context.data.markClue("woods_impossible");
    }
}
