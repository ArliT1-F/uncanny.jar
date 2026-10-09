package dev.uncanny.player;

import dev.uncanny.util.NbtUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the mod remembers about HOW this player plays.
 *
 * This is not a statistics screen and nothing here is ever shown to anyone. It
 * exists so that the world can react differently to different players: the one
 * who lives underground gets different anomalies from the one who builds, and
 * neither of them is punished for their style.
 *
 * Everything is bounded on purpose. Every map has a hard cap and evicts its
 * least-used entry, so a long save cannot grow this file without limit:
 *
 *   dimension ticks   time spent in each layer (long ticks per dimension path)
 *   area visits       how often each 256x256 area was entered (capped at 64)
 *   door uses         most-used doors, by packed position (capped at 24)
 *   underground       ticks spent below the sky, out of all observed ticks
 *   recent removals   the last 16 blocks the player broke, for Reality Echoes
 *   flags             chain states such as the_tree:missing_tree
 *
 * Nothing here is written per block, per tick, or per inventory change.
 */
public final class PlayerMemory {

    // ---------------------------------------------------------- dimensions

    /** Ticks spent in each dimension, by dimension path. */
    public final Map<String, Long> dimensionTicks = new LinkedHashMap<>();

    /** How many times the player has entered each dimension (re-entries count). */
    public final Map<String, Integer> dimensionVisits = new LinkedHashMap<>();

    /** The dimension at the last accrual, so time is attributed where it happened. */
    public String lastDimension = null;

    // --------------------------------------------------------------- areas

    /** Visits per coarse area key, most recently used last. Capped. */
    public final Map<String, Integer> areaVisits = new LinkedHashMap<>();

    /** The coarse area the player was in at the last accrual. */
    public String lastArea = "";

    // -------------------------------------------------------------- doors

    /** Uses per door (packed position as string), least used first. Capped. */
    public final Map<String, Integer> doorUses = new LinkedHashMap<>();

    // ----------------------------------------------------------- behaviour

    /** Total observed ticks this memory has been accruing. */
    public long observedTicks = 0;

    /** Of those, ticks spent underground (no sky, below y 50). */
    public long undergroundTicks = 0;

    /** Tick of the most recent time the player lay down in a bed. */
    public long lastSleepTick = 0;

    // -------------------------------------------------------------- echoes

    /**
     * The last blocks the player broke: "packedPos|blockId|tick". A Reality Echo
     * restores one of these much later, so only the recent ring matters. Capped.
     */
    public final List<String> recentRemovals = new ArrayList<>();

    // -------------------------------------------------------------- flags

    /** Chain states and other one-bit facts, e.g. "the_tree:missing_tree". */
    public final Set<String> flags = new LinkedHashSet<>();

    // --------------------------------------------------------------- caps

    private static final int MAX_AREAS = 64;
    private static final int MAX_DOORS = 24;
    private static final int MAX_REMOVALS = 16;

    private PlayerMemory() {
    }

    public static PlayerMemory newMemory() {
        return new PlayerMemory();
    }

    // ----------------------------------------------------------- accruing

    /**
     * Adds one event-check interval of observed time.
     *
     * Called from the event director once per check (not per tick): the delta
     * comes in already computed, the time is attributed to the dimension the
     * player was in, and if they changed area the new area gets a visit. This is
     * the only place time is counted, so it costs one map write per check.
     */
    public void accrue(long delta, long tick, String dimension, BlockPos pos, boolean underground) {
        if (delta <= 0) {
            return;
        }
        if (this.lastDimension == null) {
            this.lastDimension = dimension;
        } else if (!this.lastDimension.equals(dimension)) {
            this.dimensionVisits.merge(dimension, 1, Integer::sum);
            this.lastDimension = dimension;
        }
        this.dimensionTicks.merge(this.lastDimension, delta, Long::sum);
        this.observedTicks += delta;
        if (underground) {
            this.undergroundTicks += delta;
        }

        String area = areaKey(dimension, pos);
        if (!area.equals(this.lastArea)) {
            noteArea(area);
            this.lastArea = area;
        }
    }

    /** The coarse region a position belongs to: one key per 256x256 column. */
    public static String areaKey(String dimension, BlockPos pos) {
        return dimension + ":" + (pos.getX() >> 8) + "," + (pos.getZ() >> 8);
    }

    private void noteArea(String area) {
        if (!this.areaVisits.containsKey(area) && this.areaVisits.size() >= MAX_AREAS) {
            evictLeast(this.areaVisits);
        }
        this.areaVisits.merge(area, 1, Integer::sum);
    }

    /** Records one use of a door. Called from the use-block callback. */
    public void noteDoorUse(BlockPos pos) {
        String key = Long.toString(pos.asLong());
        if (!this.doorUses.containsKey(key) && this.doorUses.size() >= MAX_DOORS) {
            evictLeast(this.doorUses);
        }
        this.doorUses.merge(key, 1, Integer::sum);
    }

    /**
     * Records one broken block for later Reality Echoes.
     *
     * The block id and the tick are stored with the position, because the whole
     * point of an echo is that it comes back MUCH later and in a slightly wrong
     * version. The ring never holds more than {@value #MAX_REMOVALS} entries.
     */
    public void noteRemoval(BlockPos pos, String blockId, long tick) {
        if (this.recentRemovals.size() >= MAX_REMOVALS) {
            this.recentRemovals.remove(0);
        }
        this.recentRemovals.add(pos.asLong() + "|" + blockId + "|" + tick);
    }

    public void noteSleep(long tick) {
        this.lastSleepTick = tick;
    }

    // --------------------------------------------------------------- flags

    public void markFlag(String flag) {
        this.flags.add(flag);
    }

    public boolean hasFlag(String flag) {
        return this.flags.contains(flag);
    }

    // ------------------------------------------------------------ queries

    /** Ticks spent in a dimension, or 0 if never entered. */
    public long ticksIn(String dimension) {
        Long value = this.dimensionTicks.get(dimension);
        return value == null ? 0L : value;
    }

    public int visitsIn(String dimension) {
        Integer value = this.dimensionVisits.get(dimension);
        return value == null ? 0 : value;
    }

    /** Fraction of observed time spent underground, 0.0 .. 1.0. */
    public double undergroundRatio() {
        if (this.observedTicks <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) this.undergroundTicks / (double) this.observedTicks);
    }

    /** How often the player sleeps, in sleeps per in-game hour observed, capped. */
    public double sleepRate(UncannyPlayerData data) {
        double hours = data.playTicks / 72000.0;
        if (hours < 0.5) {
            return 0.0;
        }
        return Math.min(3.0, data.sleepCount / hours);
    }

    /** The area this player has entered the most, or null if none recorded. */
    public String favoriteArea() {
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : this.areaVisits.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    /** The door this player uses most, or null. */
    public String favoriteDoor() {
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : this.doorUses.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private static void evictLeast(Map<String, Integer> map) {
        String worst = null;
        int worstCount = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getValue() < worstCount) {
                worst = entry.getKey();
                worstCount = entry.getValue();
            }
        }
        if (worst != null) {
            map.remove(worst);
        }
    }

    // ----------------------------------------------------------------- NBT

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        NbtCompound dimTicks = new NbtCompound();
        for (Map.Entry<String, Long> entry : this.dimensionTicks.entrySet()) {
            dimTicks.putLong(entry.getKey(), entry.getValue());
        }
        nbt.put("dim_ticks", dimTicks);
        NbtCompound dimVisits = new NbtCompound();
        for (Map.Entry<String, Integer> entry : this.dimensionVisits.entrySet()) {
            dimVisits.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("dim_visits", dimVisits);
        NbtCompound areas = new NbtCompound();
        for (Map.Entry<String, Integer> entry : this.areaVisits.entrySet()) {
            areas.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("areas", areas);
        NbtCompound doors = new NbtCompound();
        for (Map.Entry<String, Integer> entry : this.doorUses.entrySet()) {
            doors.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("doors", doors);
        nbt.putString("last_area", this.lastArea);
        if (this.lastDimension != null) {
            nbt.putString("last_dim", this.lastDimension);
        }
        nbt.putLong("observed", this.observedTicks);
        nbt.putLong("underground", this.undergroundTicks);
        nbt.putLong("last_sleep", this.lastSleepTick);
        NbtUtil.writeStringList(nbt, "removals", this.recentRemovals);
        NbtUtil.writeStringSet(nbt, "flags", this.flags);
        return nbt;
    }

    public static PlayerMemory fromNbt(NbtCompound nbt) {
        PlayerMemory memory = new PlayerMemory();
        NbtCompound dimTicks = NbtUtil.child(nbt, "dim_ticks");
        for (String key : dimTicks.getKeys()) {
            memory.dimensionTicks.put(key, dimTicks.getLong(key));
        }
        NbtCompound dimVisits = NbtUtil.child(nbt, "dim_visits");
        for (String key : dimVisits.getKeys()) {
            memory.dimensionVisits.put(key, dimVisits.getInt(key));
        }
        NbtCompound areas = NbtUtil.child(nbt, "areas");
        for (String key : areas.getKeys()) {
            if (memory.areaVisits.size() >= MAX_AREAS) {
                break;
            }
            memory.areaVisits.put(key, areas.getInt(key));
        }
        NbtCompound doors = NbtUtil.child(nbt, "doors");
        for (String key : doors.getKeys()) {
            if (memory.doorUses.size() >= MAX_DOORS) {
                break;
            }
            memory.doorUses.put(key, doors.getInt(key));
        }
        memory.lastArea = NbtUtil.readString(nbt, "last_area", "");
        memory.lastDimension = nbt.contains("last_dim") ? nbt.getString("last_dim") : null;
        memory.observedTicks = NbtUtil.readLong(nbt, "observed", 0);
        memory.undergroundTicks = NbtUtil.readLong(nbt, "underground", 0);
        memory.lastSleepTick = NbtUtil.readLong(nbt, "last_sleep", 0);
        List<String> removals = NbtUtil.readStringList(nbt, "removals");
        memory.recentRemovals.addAll(removals.subList(0, Math.min(removals.size(), MAX_REMOVALS)));
        memory.flags.addAll(NbtUtil.readStringSet(nbt, "flags"));
        return memory;
    }
}
