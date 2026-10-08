package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * THE WRONG GRAVE.
 *
 * A grave with the player's own name on it. The date is tomorrow.
 *
 * The mod does not kill the player. It does not damage them, it does not chase
 * them, and it does not say anything. It builds a small mound with a headstone and
 * leaves it there, and the player is free to walk away - which they will, quickly,
 * and then spend the next hour not going back.
 *
 * The position is remembered so that {@link GraveGoneEvent} can remove it later.
 */
public class WrongGraveEvent extends UncannyEvent {

    public WrongGraveEvent() {
        super("wrong_grave", Severity.MAJOR);
        when(EventCondition.and(
                EventCondition.overworld(),
                EventCondition.playedMinutes(40),
                EventCondition.stage(4),
                EventCondition.not(EventCondition.raining())));
        chance(0.25);
        onlyOnce();
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos spot = findSpot(context);
        if (spot == null) {
            return;
        }

        // A mound. Two blocks, dug into the ground rather than placed on it.
        context.world.setBlockState(spot, Blocks.DIRT.getDefaultState(), 3);
        context.world.setBlockState(spot.up(), Blocks.DIRT.getDefaultState(), 3);
        context.world.setBlockState(spot.north(), Blocks.COARSE_DIRT.getDefaultState(), 3);

        // The headstone.
        BlockPos stone = spot.up().north();
        context.world.setBlockState(stone, Blocks.POLISHED_ANDESITE.getDefaultState(), 3);
        BlockPos sign = stone.up();
        context.world.setBlockState(sign, Blocks.OAK_WALL_SIGN.getDefaultState()
                .with(net.minecraft.block.WallSignBlock.FACING, Direction.SOUTH), 3);

        // The name is the player's. The date is tomorrow's.
        long day = context.world.getTimeOfDay() / 24000L;
        if (context.world.getBlockEntity(sign) instanceof SignBlockEntity entity) {
            entity.setTextOnRow(0, Text.literal(context.player.getName().getString()));
            entity.setTextOnRow(1, Text.literal("DAY " + (day + 1)));
            entity.setTextOnRow(2, Text.literal(""));
            entity.setTextOnRow(3, Text.literal(""));
        }

        context.data.markPos("grave", sign);
        context.data.markClue("grave_found");
    }

    /** Finds open ground a little way off, so the grave is discovered, not spawned on. */
    private BlockPos findSpot(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = context.random().between(12, 22) * (context.random().nextBoolean() ? 1 : -1);
            int dz = context.random().between(12, 22) * (context.random().nextBoolean() ? 1 : -1);
            BlockPos candidate = centre.add(dx, 0, dz);
            // Search down for ground rather than assuming the surface height.
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos at = candidate.up(dy);
                if (!context.world.getBlockState(at).isAir()
                        && context.world.getBlockState(at.up()).isAir()
                        && context.world.getBlockState(at.up(2)).isAir()) {
                    return at.up();
                }
            }
        }
        return null;
    }
}
