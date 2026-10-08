package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * One tree too many.
 *
 * The mirror of {@link MissingTreeEvent}, and harder to notice, because a player
 * has no inventory of the trees they have walked past. It only lands later, when
 * they come back along the same path and something is different.
 */
public class ExtraTreeEvent extends UncannyEvent {

    public ExtraTreeEvent() {
        super("extra_tree", Severity.QUIET);
        when(EventCondition.and(
                EventCondition.overworld(),
                EventCondition.playedMinutes(12),
                EventCondition.inForest(),
                EventCondition.openGroundNearby(7)));
        chance(0.3);
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos ground = EventCondition.findOpenGround(context, 7);
        if (ground == null) {
            return;
        }
        // A sapling-sized tree. Small enough to be plausible, tall enough that it
        // will still be there in an hour.
        for (int i = 0; i < 3; i++) {
            context.world.setBlockState(ground.up(i), Blocks.OAK_LOG.getDefaultState(), 3);
        }
        context.world.setBlockState(ground.up(3), Blocks.OAK_LEAVES.getDefaultState(), 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    context.world.setBlockState(ground.up(3).add(dx, 0, dz),
                            Blocks.OAK_LEAVES.getDefaultState(), 3);
                }
            }
        }
        context.data.markClue("tree_added");
    }
}
