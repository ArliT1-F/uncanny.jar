package dev.uncanny.block;

import dev.uncanny.item.UncannyBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * The Ledger's own memory.
 *
 * It stores almost nothing, because the Ledger's contents are not properties of
 * the book: they are properties of whoever is reading it. What it does store is
 * whether it has ever opened, and for whom, so that the same book in the same
 * world behaves consistently if two players find it.
 */
public class LedgerBlockEntity extends BlockEntity {

    /** True once the Ledger has opened for anybody. */
    private boolean opened = false;

    /** The entry number of the reader it opened for. Shown on the spine. */
    private String lastEntry = "";

    public LedgerBlockEntity(BlockPos pos, BlockState state) {
        super(UncannyBlocks.LEDGER_ENTITY_TYPE, pos, state);
    }

    public boolean isOpened() {
        return this.opened;
    }

    public String lastEntry() {
        return this.lastEntry;
    }

    /** Records that the Ledger opened. Returns true if this was the first time. */
    public boolean open(String entryLabel) {
        boolean first = !this.opened;
        this.opened = true;
        this.lastEntry = entryLabel;
        markDirty();
        return first;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("opened", this.opened);
        nbt.putString("last_entry", this.lastEntry);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.opened = nbt.getBoolean("opened");
        this.lastEntry = nbt.getString("last_entry");
    }
}
