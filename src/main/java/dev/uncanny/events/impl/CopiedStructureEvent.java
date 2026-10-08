package dev.uncanny.events.impl;

import dev.uncanny.events.EventCondition;
import dev.uncanny.events.EventContext;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.BlockVariant;
import dev.uncanny.util.PositionUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * REALITY ECHO: a structure that corresponds to what the player built, but
 * slightly differently.
 *
 * The player builds a lot; somewhere they have been, there is now a short wall
 * of their dominant material with exactly one block of something else in it.
 * It is not a copy of anything specific - it is the shape of "someone who builds
 * like you" left in a place you did not build in.
 *
 * Only players with real building history get this: the echo needs something to
 * echo. The Copy layer favours it strongly, and instability scales it.
 */
public class CopiedStructureEvent extends UncannyEvent {

    public CopiedStructureEvent() {
        super("copied_structure", Severity.NOTICED);
        family(Family.ECHO);
        when(EventCondition.and(
                EventCondition.playedMinutes(45),
                EventCondition.named("not enough building history",
                        context -> context.data.home.placements >= 30),
                EventCondition.named("no room for a structure",
                        context -> findWallSite(context) != null)));
        chance(0.12);
        fromInstability(0.12);
    }

    /** A 3x2 face of air, with solid ground beneath, away from the player. */
    private static BlockPos findWallSite(EventContext context) {
        ServerWorld world = context.world;
        BlockPos centre = context.player.getBlockPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                BlockPos base = centre.add(dx, 0, dz);
                if (PositionUtil.distanceSquared(base, centre) < 4.0 * 4.0) {
                    continue; // not in the player's own space
                }
                if (world.getBlockState(base.down()).isAir()
                        || !world.getBlockState(base).isAir()
                        || !world.getBlockState(base.up()).isAir()) {
                    continue;
                }
                boolean clear = true;
                for (int ox = 0; ox < 3 && clear; ox++) {
                    for (int oy = 0; oy < 2; oy++) {
                        if (!world.getBlockState(base.add(ox, oy, 0)).isAir()) {
                            clear = false;
                            break;
                        }
                    }
                }
                if (clear) {
                    return base;
                }
            }
        }
        return null;
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos base = findWallSite(context);
        if (base == null) {
            return;
        }
        Block material = BlockVariant.byId(context.data.home.dominantMaterial());
        if (material == Blocks.AIR) {
            material = Blocks.OAK_PLANKS;
        }
        Block wrong = BlockVariant.wrongWoodOf(material);
        if (wrong == null || wrong == material) {
            wrong = material == Blocks.COBBLESTONE ? Blocks.STONE : Blocks.COBBLESTONE;
        }
        int wrongAt = context.random().nextInt(6);
        int i = 0;
        for (int ox = 0; ox < 3; ox++) {
            for (int oy = 0; oy < 2; oy++) {
                Block block = i == wrongAt ? wrong : material;
                PositionUtil.setQuietly(context.world, base.add(ox, oy, 0),
                        block.getDefaultState());
                i++;
            }
        }
        context.data.markClue("structure_copied");
    }
}
