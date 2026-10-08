package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.player.PlayerMemory;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * A chest in a place the player remembers there not being one.
 *
 * The chest is empty - it has no loot table, nothing can be taken from it or
 * duped into it - and it only appears in an area the player knows well, where
 * they have opened their own chests enough times to have an opinion about which
 * ones exist. The horror is not the chest; it is the small homework of checking
 * every other chest in the area afterwards.
 *
 * Requires a MEDIUM-thin reality: this is an architectural inconsistency, not
 * a subtle one.
 */
public class ChestThatWasntThereEvent extends UncannyEvent {

    public ChestThatWasntThereEvent() {
        super("chest_that_wasnt_there", Severity.NOTICED);
        family(Family.FALSE_NORMALITY);
        when(EventCondition.and(
                EventCondition.playedMinutes(30),
                EventCondition.openGroundNearby(8),
                EventCondition.named("no chest history",
                        context -> !context.data.home.containers.isEmpty()),
                EventCondition.named("not in a familiar area",
                        context -> {
                            String favorite = context.data.memory.favoriteArea();
                            return favorite != null && favorite.equals(PlayerMemory.areaKey(
                                    context.data.currentDimension, context.player.getBlockPos()));
                        }),
                EventCondition.named("chest already nearby", ChestThatWasntThereEvent::clearSpot)));
        chance(0.15);
        fromInstability(0.25);
    }

    /** Bounded check: the ground spot has no chest within five blocks. */
    private static boolean clearSpot(EventContext context) {
        BlockPos ground = EventCondition.findOpenGround(context, 8);
        if (ground == null) {
            return false;
        }
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    if (context.world.getBlockState(ground.add(dx, dy, dz))
                            .getBlock() instanceof net.minecraft.block.ChestBlock) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos ground = EventCondition.findOpenGround(context, 8);
        if (ground == null) {
            return;
        }
        PositionUtil.setQuietly(context.world, ground, Blocks.CHEST.getDefaultState());
        context.data.markClue("chest_appeared");
    }
}
