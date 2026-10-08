package dev.uncanny.item;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.lore.LoreStage;
import dev.uncanny.player.PlayerProgress;
import dev.uncanny.util.TextUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Surveyors' compass.
 *
 * It points at boundaries, which means in practice that it points at the nearest
 * thing the mod has recorded: a Ledger, a seal plate, a doorway. Early on it
 * behaves like a compass. Later it does not, and the player is left holding an
 * instrument that has started to answer a different question.
 */
public class SurveyorsCompassItem extends Item {

    public SurveyorsCompassItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(stack);
        }

        UncannyWorldState state = UncannyWorldState.get(serverWorld);
        LoreStage stage = PlayerProgress.stage(state.player(serverPlayer.getUuid()), state);
        BlockPos ledger = state.ledgerPosition();

        String message;
        if (stage.atLeast(LoreStage.PERSONAL.level())) {
            message = "THE NEEDLE IS NOT MOVING.";
        } else if (ledger != null) {
            double distance = Math.sqrt(ledger.getSquaredDistance(player.getBlockPos()));
            if (distance < 8) {
                message = "IT IS HERE.";
            } else {
                message = ((int) distance) + "m  " + bearing(serverPlayer, ledger);
            }
        } else {
            message = "NO BOUNDARY RECORDED.";
        }

        // Action bar rather than chat: this is an instrument reading, not a message.
        serverPlayer.sendMessage(Text.literal(TextUtil.spaced(message)), true);
        return TypedActionResult.success(stack, world.isClient());
    }

    /** Rough compass bearing, in the eight directions a player understands. */
    private static String bearing(ServerPlayerEntity player, BlockPos target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) {
            angle += 360;
        }
        String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return points[(int) Math.round(angle / 45.0) % 8];
    }
}
