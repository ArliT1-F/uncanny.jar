package dev.uncanny.block;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.DimensionManager;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.lore.LedgerManager;
import dev.uncanny.lore.LoreStage;
import dev.uncanny.net.UncannyPayloads;
import dev.uncanny.player.PlayerProgress;
import dev.uncanny.player.UncannyPlayerData;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * THE LEDGER.
 *
 * A huge book on a plinth in the Archive. For most of the game it does nothing at
 * all: no particles, no sound, no message telling the player it cannot be opened.
 * It is simply a block that refuses.
 *
 * When it does open, it opens for one person, and the first page is their name.
 *
 * Everything the book shows is generated from the reader's own history by
 * {@link LedgerManager}. Nothing on those pages is invented.
 */
public class LedgerBlock extends BlockWithEntity {

    public LedgerBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LedgerBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        // BlockWithEntity defaults to INVISIBLE; we want the model to render.
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.CONSUME;
        }

        UncannyWorldState worldState = UncannyWorldState.get(serverWorld);
        UncannyPlayerData data = worldState.player(serverPlayer.getUuid());
        boolean inPartition = DimensionManager.current(serverPlayer) == UncannyDimension.PARTITION;

        // ---- refusal ----
        if (!LedgerManager.opensFor(data, worldState)) {
            // The player has tried. That itself is remembered, and it is the clue
            // that later documents refer to.
            data.markClue("ledger_refused");
            worldState.markDirty();
            return ActionResult.SUCCESS;
        }

        // ---- it opens ----
        if (world.getBlockEntity(pos) instanceof LedgerBlockEntity ledger) {
            ledger.open(data.entryLabel());
        }

        // The book answers a little more each time the player has earned it.
        LoreStage stage = PlayerProgress.stage(data, worldState);
        if (data.ledgerState == 0) {
            LedgerManager.advance(data, worldState);
            data.markClue("ledger_opened");
        } else if (stage.atLeast(LoreStage.REVELATION.level()) && data.ledgerState < 2) {
            LedgerManager.advance(data, worldState);
        } else if (inPartition && data.ledgerState < 3) {
            LedgerManager.advance(data, worldState);
        }

        UncannyPayloads.sendLedger(serverPlayer, worldState, inPartition);
        return ActionResult.SUCCESS;
    }
}
