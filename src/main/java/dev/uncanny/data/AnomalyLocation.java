package dev.uncanny.data;

import dev.uncanny.util.NbtUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * One place where an anomaly happened, remembered by the world.
 *
 * The world remembering is the point: returning to a place where something was
 * wrong makes the place more wrong, related events can appear nearby, and a
 * Reality Echo at a spot the player already distrusts lands completely
 * differently from one in a field they have never visited.
 *
 * The record is tiny on purpose - a dimension, a packed position, a type, two
 * timestamps. There is no per-block history anywhere in this mod; the list is
 * capped and the changes that produced these records were single blocks.
 */
public final class AnomalyLocation {

    /** The dimension path, e.g. "overworld" or "hall". */
    public final String dimension;

    /** The packed position (BlockPos.asLong) the anomaly centred on. */
    public final long pos;

    /** The event id, e.g. "missing_tree". */
    public final String anomalyType;

    /** World tick of the first occurrence here. */
    public final long firstSeen;

    /** World tick of the most recent occurrence here (updated on revisits). */
    public long lastSeen;

    /** Severity ordinal at first sighting. */
    public final int severity;

    /** Reserved: whether the player has moved on from it. Not yet used. */
    public boolean resolved = false;

    public AnomalyLocation(String dimension, long pos, String anomalyType,
                           long firstSeen, int severity) {
        this.dimension = dimension;
        this.pos = pos;
        this.anomalyType = anomalyType;
        this.firstSeen = firstSeen;
        this.lastSeen = firstSeen;
        this.severity = severity;
    }

    public BlockPos position() {
        return BlockPos.fromLong(this.pos);
    }

    // ----------------------------------------------------------------- NBT

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("dim", this.dimension);
        nbt.putLong("pos", this.pos);
        nbt.putString("type", this.anomalyType);
        nbt.putLong("first", this.firstSeen);
        nbt.putLong("last", this.lastSeen);
        nbt.putInt("severity", this.severity);
        nbt.putBoolean("resolved", this.resolved);
        return nbt;
    }

    public static AnomalyLocation fromNbt(NbtCompound nbt) {
        AnomalyLocation location = new AnomalyLocation(
                NbtUtil.readString(nbt, "dim", "minecraft:overworld"),
                NbtUtil.readLong(nbt, "pos", 0L),
                NbtUtil.readString(nbt, "type", "unknown"),
                NbtUtil.readLong(nbt, "first", 0L),
                NbtUtil.readInt(nbt, "severity", 0));
        location.lastSeen = NbtUtil.readLong(nbt, "last", location.firstSeen);
        location.resolved = NbtUtil.readBool(nbt, "resolved", false);
        return location;
    }
}
