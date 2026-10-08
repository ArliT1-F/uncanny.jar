package dev.uncanny.item;

import dev.uncanny.net.UncannyPayloads;
import dev.uncanny.nbt.UncannyNbtKeys;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A Surveyor record you can hold.
 *
 * It is not a written book. It has no author and it cannot be signed, because it
 * should not feel like an item the player made. The pages are read from the stack's
 * own NBT and shown on the mod's own screen, which is what lets the same record
 * read differently for two different players.
 */
public class SurveyRecordItem extends Item {

    public SurveyRecordItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            NbtCompound nbt = stack.getNbt();
            if (nbt != null) {
                UncannyPayloads.sendRecord(serverPlayer, stack);
                // Opening a record is a discovery, and discoveries move the story.
                UncannyPayloads.noteRead(serverPlayer, nbt.getString(UncannyNbtKeys.LORE_ID));
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        // No enchantment shimmer. These documents are not special, they are filed.
        return false;
    }
}
