package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.RandomUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * THE OTHER HOUSE.
 *
 * Built from what the mod remembers the player placing. Same materials, same rough
 * shape, a bed where a bed should be, storage where storage should be. One item in
 * one chest is different: it is something the player has held, and has never put
 * down here.
 *
 * This is the anomaly that converts suspicion into certainty, because it cannot be
 * explained by misremembering. It is also the reason the mod tracks what the player
 * builds in the first place.
 */
public class OtherHouseEvent extends UncannyEvent {

    public OtherHouseEvent() {
        super("other_house", Severity.MAJOR);
        family(Family.ARCHITECTURAL);
        when(EventCondition.and(
                EventCondition.overworld(),
                EventCondition.playedMinutes(50),
                EventCondition.stage(4),
                OtherHouseEvent::hasHome));
        chance(0.3);
        onlyOnce();
    }

    private static boolean hasHome(EventContext context) {
        return context.data.home.placements >= 12;
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos spot = findSite(context);
        if (spot == null) {
            return;
        }

        Block wall = materialFor(context);
        RandomUtil.UncannyRandom random = context.random();

        // A box, 7 by 5 by 9. Not the player's house - a house like theirs.
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 9; z++) {
                context.world.setBlockState(spot.add(x, 0, z), wall.getDefaultState(), 3);
                for (int y = 1; y <= 4; y++) {
                    boolean edge = x == 0 || x == 6 || z == 0 || z == 8;
                    BlockPos at = spot.add(x, y, z);
                    if (edge) {
                        context.world.setBlockState(at, wall.getDefaultState(), 3);
                    } else {
                        context.world.setBlockState(at, Blocks.AIR.getDefaultState(), 3);
                    }
                }
                context.world.setBlockState(spot.add(x, 5, z), wall.getDefaultState(), 3);
            }
        }

        // Two windows, so it can be seen into from outside.
        context.world.setBlockState(spot.add(2, 3, 0), Blocks.GLASS.getDefaultState(), 3);
        context.world.setBlockState(spot.add(4, 3, 0), Blocks.GLASS.getDefaultState(), 3);

        // A door. Closed.
        BlockPos door = spot.add(3, 1, 0);
        context.world.setBlockState(door, Blocks.OAK_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.FACING, Direction.SOUTH), 3);
        context.world.setBlockState(door.up(), Blocks.OAK_DOOR.getDefaultState()
                .with(net.minecraft.block.DoorBlock.FACING, Direction.SOUTH)
                .with(net.minecraft.block.DoorBlock.HALF,
                        net.minecraft.block.enums.DoubleBlockHalf.UPPER), 3);

        // A bed, and a light, because the mod remembers the player slept somewhere.
        if (context.data.home.bedPosition != null) {
            context.world.setBlockState(spot.add(5, 1, 6), Blocks.RED_BED.getDefaultState(), 3);
        }
        context.world.setBlockState(spot.add(1, 3, 1), Blocks.TORCH.getDefaultState(), 3);

        // Storage. The number of chests matches the number the mod remembers.
        int chests = Math.min(3, Math.max(1, context.data.home.containers.size()));
        for (int i = 0; i < chests; i++) {
            BlockPos chestPos = spot.add(1 + i, 1, 7);
            context.world.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), 3);
            if (i == 0 && context.world.getBlockEntity(chestPos)
                    instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
                chest.setStack(13, theWrongItem(context, random));
            }
        }

        context.data.markPos("other_house", spot);
        context.data.markClue("other_house");
    }

    /**
     * The one item that should not be there.
     *
     * If the mod remembers the first interesting thing the player picked up, it
     * uses that. Otherwise it falls back to something ordinary, because an
     * ordinary object in the wrong place is worse than a strange one.
     */
    private ItemStack theWrongItem(EventContext context, RandomUtil.UncannyRandom random) {
        String remembered = context.data.home.firstSignificantItem;
        if (remembered != null) {
            var item = Registries.ITEM.get(new Identifier(remembered));
            if (item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                stack.setCustomName(Text.literal("YOURS"));
                return stack;
            }
        }
        return random.nextBoolean() ? new ItemStack(Items.CLOCK) : new ItemStack(Items.NAME_TAG);
    }

    /** The material the player uses most. A reasonable guess at their house. */
    private Block materialFor(EventContext context) {
        String id = context.data.home.dominantMaterial();
        var block = Registries.BLOCK.get(new Identifier(id));
        return block == Blocks.AIR ? Blocks.OAK_PLANKS : block;
    }

    /** Finds flat-ish ground a distance away, on the surface. */
    private BlockPos findSite(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = context.random().between(24, 44) * (context.random().nextBoolean() ? 1 : -1);
            int dz = context.random().between(24, 44) * (context.random().nextBoolean() ? 1 : -1);
            BlockPos candidate = centre.add(dx, 0, dz);
            for (int dy = 8; dy >= -8; dy--) {
                BlockPos at = candidate.up(dy);
                boolean solid = !context.world.getBlockState(at).isAir();
                boolean clear = true;
                for (int y = 1; y <= 6; y++) {
                    if (!context.world.getBlockState(at.up(y)).isAir()) {
                        clear = false;
                        break;
                    }
                }
                if (solid && clear) {
                    return at.up();
                }
            }
        }
        return null;
    }
}
