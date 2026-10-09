package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * A familiar structure missing exactly one block.
 *
 * One block of a wall the player has walked past a hundred times is gone. Not
 * blown up (the neighbours are intact), not dropped (nothing was mined): simply
 * never there. The player's first explanation will be that they never finished
 * that wall, and the second explanation arrives later, in a quieter moment.
 *
 * The search is bounded to a small cube, runs only when every other gate has
 * passed, and the result is always exactly one block.
 */
public class MissingBlockEvent extends UncannyEvent {

    public MissingBlockEvent() {
        super("missing_block", Severity.NOTICED);
        family(Family.FALSE_NORMALITY);
        when(EventCondition.and(
                EventCondition.playedMinutes(35),
                EventCondition.named("no repeated pattern nearby",
                        context -> findPatternBlock(context) != null)));
        chance(0.15);
        fromInstability(0.25);
    }

    /**
     * A block that belongs to a repeated pattern: at least three of its four
     * horizontal neighbours are the identical block, and it carries no
     * block-entity (we do not delete chests, spawners or anything with an
     * inventory). One bounded cube of reads.
     */
    private static BlockPos findPatternBlock(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos pos = centre.add(dx, dy, dz);
                    BlockState state = context.world.getBlockState(pos);
                    if (state.isAir() || state.isOf(Blocks.BEDROCK)) {
                        continue;
                    }
                    if (context.world.getBlockEntity(pos) != null) {
                        continue;
                    }
                    int same = 0;
                    for (net.minecraft.util.math.Direction direction
                            : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                        if (context.world.getBlockState(pos.offset(direction)) == state) {
                            same++;
                        }
                    }
                    if (same >= 3) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos target = findPatternBlock(context);
        if (target == null) {
            return;
        }
        PositionUtil.clearQuietly(context.world, target);
        context.data.markClue("block_missing");
    }
}
