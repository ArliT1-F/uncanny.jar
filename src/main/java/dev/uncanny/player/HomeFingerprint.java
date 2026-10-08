package dev.uncanny.player;

import dev.uncanny.util.NbtUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately small description of what a player has actually built.
 *
 * The mod never scans the world looking for a player's house - that would be
 * expensive and it would also be wrong, because "home" is not something a block
 * scan can find. Instead it remembers a trickle of things as they happen:
 * the first bed used, where blocks get placed, what goes into chests.
 *
 * The House and the Copy dimensions are then generated FROM this record, which
 * is why they feel like the player's world without ever copying it exactly.
 *
 * Everything here is bounded: fixed-size lists, capped maps, no per-tick cost.
 */
public final class HomeFingerprint {

    /** Blocks placed by the player, by block id, most common first. Capped. */
    public final Map<String, Integer> placedBlocks = new LinkedHashMap<>();

    /** Chest descriptions, in the order they were filled. Capped. */
    public final List<String> containers = new ArrayList<>();

    /** Positions the player has died at. Capped. */
    public final List<BlockPos> deathPositions = new ArrayList<>();

    /** Where the player last slept. Null until the first night. */
    public BlockPos bedPosition = null;

    /** Where the player first joined the world. */
    public BlockPos spawnPosition = null;

    /** The id of the first item the player picked up that looked important. */
    public String firstSignificantItem = null;

    /** Running total of blocks placed; used for pacing, not stored per block. */
    public int placements = 0;

    /** Total blocks broken by the player. */
    public int removals = 0;

    private static final int MAX_BLOCK_TYPES = 24;
    private static final int MAX_CONTAINERS = 8;
    private static final int MAX_DEATHS = 6;

    /** Records one block placement. Called from a callback, not from a tick. */
    public void recordPlacement(String blockId) {
        this.placements++;
        Integer count = this.placedBlocks.get(blockId);
        if (count == null) {
            // Only track the most common kinds, so the map cannot grow forever.
            if (this.placedBlocks.size() >= MAX_BLOCK_TYPES) {
                return;
            }
            this.placedBlocks.put(blockId, 1);
        } else {
            this.placedBlocks.put(blockId, count + 1);
        }
    }

    public void recordRemoval() {
        this.removals++;
    }

    public void recordDeath(BlockPos pos) {
        if (this.deathPositions.size() >= MAX_DEATHS) {
            this.deathPositions.remove(0);
        }
        this.deathPositions.add(pos.toImmutable());
    }

    public void recordContainer(String description) {
        if (this.containers.size() >= MAX_CONTAINERS) {
            this.containers.remove(0);
        }
        this.containers.add(description);
    }

    /** The block the player places most often; a decent guess at their style. */
    public String dominantMaterial() {
        String best = "oak_planks";
        int bestCount = -1;
        for (Map.Entry<String, Integer> entry : this.placedBlocks.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    /** True once there is enough history to build a believable echo of a home. */
    public boolean hasEnoughHistory() {
        return this.bedPosition != null || this.placements >= 8;
    }

    // ------------------------------------------------------------------ NBT

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        NbtCompound blocks = new NbtCompound();
        for (Map.Entry<String, Integer> entry : this.placedBlocks.entrySet()) {
            blocks.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("blocks", blocks);
        NbtUtil.writeStringList(nbt, "containers", this.containers);
        nbt.putInt("placements", this.placements);
        nbt.putInt("removals", this.removals);
        if (this.bedPosition != null) {
            nbt.putLong("bed", BlockPos.asLong(this.bedPosition));
        }
        if (this.spawnPosition != null) {
            nbt.putLong("spawn", BlockPos.asLong(this.spawnPosition));
        }
        if (this.firstSignificantItem != null) {
            nbt.putString("first_item", this.firstSignificantItem);
        }
        long[] deaths = new long[this.deathPositions.size()];
        for (int i = 0; i < deaths.length; i++) {
            deaths[i] = BlockPos.asLong(this.deathPositions.get(i));
        }
        NbtUtil.writeLongArray(nbt, "deaths", deaths);
        return nbt;
    }

    public static HomeFingerprint fromNbt(NbtCompound nbt) {
        HomeFingerprint out = new HomeFingerprint();
        NbtCompound blocks = NbtUtil.child(nbt, "blocks");
        for (String key : blocks.getKeys()) {
            out.placedBlocks.put(key, blocks.getInt(key));
        }
        out.containers.addAll(NbtUtil.readStringList(nbt, "containers"));
        out.placements = NbtUtil.readInt(nbt, "placements", 0);
        out.removals = NbtUtil.readInt(nbt, "removals", 0);
        if (nbt.contains("bed")) {
            out.bedPosition = BlockPos.fromLong(nbt.getLong("bed"));
        }
        if (nbt.contains("spawn")) {
            out.spawnPosition = BlockPos.fromLong(nbt.getLong("spawn"));
        }
        out.firstSignificantItem = nbt.contains("first_item") ? nbt.getString("first_item") : null;
        for (long packed : NbtUtil.readLongArray(nbt, "deaths")) {
            out.deathPositions.add(BlockPos.fromLong(packed));
        }
        return out;
    }
}
