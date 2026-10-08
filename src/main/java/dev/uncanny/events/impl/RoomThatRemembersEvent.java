package dev.uncanny.events.impl;

import dev.uncanny.events.EventContext;
import dev.uncanny.events.EventCondition;
import dev.uncanny.events.UncannyEvent;
import dev.uncanny.util.SignUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * THE ROOM THAT REMEMBERS.
 *
 * A small sealed room, built near the player, containing one object for each
 * significant thing the mod has recorded: a bed if they slept, a chest for each
 * container it remembers, a torch for each death. Not everything - only the things
 * that mattered.
 *
 * The horror is in the selection. The room has been keeping notes, and the notes
 * are accurate, and the player can check them.
 */
public class RoomThatRemembersEvent extends UncannyEvent {

    public RoomThatRemembersEvent() {
        super("room_that_remembers", Severity.MAJOR);
        when(EventCondition.and(
                EventCondition.playedMinutes(45),
                EventCondition.stage(5)));
        chance(0.3);
        onlyOnce();
    }

    @Override
    protected void perform(EventContext context) {
        BlockPos spot = findSite(context);
        if (spot == null) {
            return;
        }

        // A 7x5x7 sealed box. One doorway, which closes behind nobody because the
        // player is already inside by the time they notice the room exists.
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                for (int y = 0; y <= 4; y++) {
                    boolean shell = x == 0 || x == 6 || z == 0 || z == 6 || y == 0 || y == 4;
                    BlockPos at = spot.add(x, y, z);
                    context.world.setBlockState(at,
                            shell ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
        context.world.setBlockState(spot.add(3, 1, 0), Blocks.AIR.getDefaultState(), 3);
        context.world.setBlockState(spot.add(3, 2, 0), Blocks.AIR.getDefaultState(), 3);

        int y = 1;

        // One object per recorded fact. The counts are real.
        if (context.data.sleepCount > 0) {
            context.world.setBlockState(spot.add(5, y, 5), Blocks.RED_BED.getDefaultState(), 3);
        }
        int chests = Math.min(3, context.data.home.containers.size());
        for (int i = 0; i < chests; i++) {
            context.world.setBlockState(spot.add(1 + i, y, 1), Blocks.CHEST.getDefaultState(), 3);
        }
        int torches = Math.min(6, context.data.deathCount);
        for (int i = 0; i < torches; i++) {
            context.world.setBlockState(spot.add(1 + i, y, 3), Blocks.TORCH.getDefaultState(), 3);
        }

        // The log. Written in the Archive's voice, about the player's own week.
        BlockPos log = spot.add(3, y, 6);
        context.world.setBlockState(log, Blocks.CHEST.getDefaultState(), 3);
        if (context.world.getBlockEntity(log) instanceof ChestBlockEntity chest) {
            chest.setStack(13, logBook(context));
        }

        BlockPos sign = spot.add(3, y + 1, 5);
        context.world.setBlockState(sign, Blocks.OAK_SIGN.getDefaultState(), 3);
        if (context.world.getBlockEntity(sign) instanceof SignBlockEntity entity) {
            SignUtil.setLines(entity, "OBSERVATION", context.data.entryLabel(),
                    "ITEMS: " + (chests + torches + (context.data.sleepCount > 0 ? 1 : 0)), "ONGOING");
        }

        context.data.markPos("memory_room", spot);
        context.data.markClue("room_remembers");
    }

    /** The book in the room. It is a record of the player, not a story. */
    private ItemStack logBook(EventContext context) {
        List<String> pages = new ArrayList<>();
        pages.add(String.join("\n", "ENTRY " + context.data.entryLabel(), "", "FIRST SLEEP",
                context.data.sleepCount > 0 ? "RECORDED" : "NOT RECORDED"));
        pages.add(String.join("\n", "ENTRY " + context.data.entryLabel(), "", "TERMINATIONS",
                Integer.toString(context.data.deathCount)));
        pages.add(String.join("\n", "ENTRY " + context.data.entryLabel(), "", "LAYERS ENTERED",
                Integer.toString(context.data.firstDimensionEntry.size())));
        pages.add(String.join("\n", "SUBJECT HAS BEGUN", "SEARCHING."));

        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        var nbt = stack.getOrCreateNbt();
        nbt.putString("title", "OBSERVATION");
        nbt.putString("author", "");
        NbtList list = new NbtList();
        for (String page : pages) {
            list.add(NbtString.of(page));
        }
        nbt.put("pages", list);
        return stack;
    }

    /** Prefers somewhere underground or enclosed; falls back to anywhere clear. */
    private BlockPos findSite(EventContext context) {
        BlockPos centre = context.player.getBlockPos();
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = context.random().between(8, 16) * (context.random().nextBoolean() ? 1 : -1);
            int dz = context.random().between(8, 16) * (context.random().nextBoolean() ? 1 : -1);
            BlockPos at = centre.add(dx, context.random().between(-4, 2), dz);
            boolean clear = true;
            for (int x = 0; x < 7 && clear; x++) {
                for (int y = 0; y <= 4 && clear; y++) {
                    for (int z = 0; z < 7 && clear; z++) {
                        if (!context.world.getBlockState(at.add(x, y, z)).isAir()) {
                            clear = false;
                        }
                    }
                }
            }
            if (clear) {
                return at;
            }
        }
        return null;
    }
}
