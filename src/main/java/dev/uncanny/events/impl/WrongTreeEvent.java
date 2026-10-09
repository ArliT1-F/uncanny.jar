package dev.uncanny.events.impl;

import dev.uncanny.events.AnomalyChain;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.BlockVariant;
import dev.uncanny.util.PositionUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * The tree comes back - incorrectly.
 *
 * The second half of THE TREE chain. This can only fire where the original
 * missing tree was, which means the player has to return to the place they
 * half-remembered wrong: the world waits for them rather than hunting them.
 * The trunk is rebuilt under the floating crown, in a wood that tree was never
 * made of, chosen deterministically from the player's uuid and the position so
 * it is always the same wrongness for the same person at the same spot.
 *
 * Requires both earlier stages: the missing tree itself, and the Ledger's
 * canopy line. A player who never opens the book never gets this stage - the
 * design says the chain must not be guaranteed.
 */
public class WrongTreeEvent extends UncannyEvent {

    public WrongTreeEvent() {
        super("wrong_tree", Severity.NOTICED);
        family(Family.FALSE_NORMALITY);
        when(EventCondition.and(
                EventCondition.playedMinutes(40),
                EventCondition.named("missing tree not nearby",
                        WrongTreeEvent::siteIsClose)));
        chance(0.3);
        onlyOnce();
        fromInstability(0.15);
        advances(AnomalyChain.TREE);
        after(AnomalyChain.TREE, "missing_tree");
        after(AnomalyChain.TREE, "ledger_reference");
    }

    /**
     * The remembered site exists, is loaded, and the player is within 48 blocks
     * of it. Chunk-loaded is checked explicitly so the condition can never force
     * a synchronous chunk load - the whole search is two reads.
     */
    private static boolean siteIsClose(EventContext context) {
        BlockPos site = context.data.markPos("missing_tree_pos");
        if (site == null) {
            return false;
        }
        ServerWorld world = context.world;
        if (!world.isChunkLoaded(site.getX() >> 4, site.getZ() >> 4)) {
            return false;
        }
        return PositionUtil.distanceSquared(site, context.player.getBlockPos()) <= 48.0 * 48.0;
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos site = context.data.markPos("missing_tree_pos");
        if (site == null || !siteIsClose(context)) {
            return;
        }
        // Which wrong wood, for this player, at this spot: always the same answer.
        long salt = SeedUtil.mix(context.data.uuid.getLeastSignificantBits(),
                site.getX(), site.getY(), site.getZ());
        Block[] woods = {Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.DARK_OAK_LOG};
        Block wrong = woods[Math.floorMod((int) salt, woods.length)];

        // Rebuild the trunk under the crown that never fell. Air or leaves only:
        // if something else grew there, the tree does not force the issue.
        for (int dy = 0; dy < 3; dy++) {
            BlockPos at = site.up(dy);
            var state = context.world.getBlockState(at);
            boolean leaf = state.getBlock() instanceof net.minecraft.block.LeavesBlock;
            if (!state.isAir() && !leaf) {
                if (dy == 0) {
                    return; // the site is taken; nothing comes back
                }
                break;
            }
            PositionUtil.setQuietly(context.world, at, wrong.getDefaultState());
        }
        // Silence, then nothing: the return is not announced. The crown simply has
        // a trunk again, and it is not the right one.
        context.data.markClue("tree_returned_wrong");
    }
}
