package dev.uncanny.player;

import dev.uncanny.util.NbtUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the mod remembers about one player.
 *
 * This is the memory of the mod. Nothing here is stored on the client, so a
 * player cannot lose their progress by clearing a cache, and a server never has
 * to trust what a client tells it.
 *
 * Instances live inside {@link dev.uncanny.data.UncannyWorldState} and are saved
 * with the world.
 */
public final class UncannyPlayerData {

    /** The player's uuid, as a string, because that is how NBT wants it. */
    public final UUID uuid;

    /** Last known username. Updated on every join. */
    public String username = "unknown";

    /**
     * The Anchor number. Deterministic from the uuid and assigned once, so it is
     * the same in every world the player joins and it never changes mid-game.
     * It is a three digit number, the same shape as the Anchor rooms the player
     * finds earlier: 003, 017, 041, 102, 318, 740.
     */
    public final int entryNumber;

    // ------------------------------------------------------------- counters

    public long firstSeenTick = 0;
    public long playTicks = 0;
    public int sleepCount = 0;
    public int deathCount = 0;
    public int anomaliesSeen = 0;
    public int majorAnomaliesSeen = 0;

    /** Where the player first died, for the Ledger. */
    public BlockPos firstDeathPosition = null;

    /** Where the player first slept, for the Ledger. */
    public BlockPos firstSleepPosition = null;

    /** The tick of the first entry into each dimension, by dimension id path. */
    public final Map<String, Long> firstDimensionEntry = new HashMap<>();

    // --------------------------------------------------------------- memory

    /** Lore entry ids the player has actually read. */
    public final Set<String> discoveredLore = new LinkedHashSet<>();

    /** Clue ids. A clue is something the player noticed, not something they read. */
    public final Set<String> clues = new LinkedHashSet<>();

    /** Event ids that must never repeat for this player. */
    public final Set<String> completedEvents = new HashSet<>();

    /** Anchor room numbers the player has stood inside, e.g. "ANCHOR 041". */
    public final Set<String> anchorsSeen = new LinkedHashSet<>();

    /** Flags for major one-off anomalies: wrong_grave, other_house, room_that_remembers. */
    public final Set<String> majorAnomalies = new HashSet<>();

    /**
     * Positions an event needs to find again later, by name.
     *
     * The wrong grave has to be removed, and the room that remembers has to be
     * where the player left it. Storing a handful of packed block positions is
     * cheap; searching the world for them later would not be.
     */
    public final Map<String, Long> marks = new HashMap<>();

    // ---------------------------------------------------------- the Ledger

    /**
     * 0 = the Ledger will not open for this player yet
     * 1 = it opened; the first page is a name
     * 2 = it is answering back
     * 3 = STATUS: ACTIVE
     * 4 = STATUS: ANCHOR
     * 5 = STATUS: VACANT
     */
    public int ledgerState = 0;

    /** Anchor status line, kept as text so the Ledger and the rooms agree. */
    public String anchorStatus = "UNKNOWN";

    /** Set once the player has been shown the final sequence. */
    public boolean observationComplete = false;

    // -------------------------------------------------------------- timing

    public long lastAnomalyTick = Long.MIN_VALUE;
    public long lastMajorAnomalyTick = Long.MIN_VALUE;
    public long lastLoudEventTick = Long.MIN_VALUE;

    // ------------------------------------------------------------ home data

    public final HomeFingerprint home = new HomeFingerprint();

    public UncannyPlayerData(UUID uuid) {
        this.uuid = uuid;
        // Deterministic but stable: same player, same number, forever.
        long hash = SeedUtil.forString(uuid.toString());
        this.entryNumber = 100 + Math.floorMod((int) hash, 900);
    }

    /** Three digit form used in every record: 741, 003, 041. */
    public String entryLabel() {
        return String.format("%03d", this.entryNumber);
    }

    /** "ANCHOR 741". */
    public String anchorLabel() {
        return "ANCHOR " + entryLabel();
    }

    public void markLore(String loreId) {
        this.discoveredLore.add(loreId);
    }

    public boolean knowsLore(String loreId) {
        return this.discoveredLore.contains(loreId);
    }

    public void markClue(String clueId) {
        this.clues.add(clueId);
    }

    public boolean hasClue(String clueId) {
        return this.clues.contains(clueId);
    }

    public void markEventDone(String eventId) {
        this.completedEvents.add(eventId);
    }

    public boolean eventDone(String eventId) {
        return this.completedEvents.contains(eventId);
    }

    /** Remembers a position for later. */
    public void markPos(String name, BlockPos pos) {
        this.marks.put(name, pos == null ? 0L : pos.asLong());
    }

    /** Recovers a remembered position, or null if there is none. */
    public BlockPos markPos(String name) {
        Long packed = this.marks.get(name);
        if (packed == null || packed == 0L) {
            return null;
        }
        return BlockPos.fromLong(packed);
    }

    public void enteredDimension(String dimensionPath, long tick) {
        this.firstDimensionEntry.putIfAbsent(dimensionPath, tick);
    }

    public long totalDiscoveries() {
        return this.discoveredLore.size() + this.clues.size() + this.anchorsSeen.size();
    }

    // ------------------------------------------------------------------ NBT

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("username", this.username);
        nbt.putInt("entry", this.entryNumber);
        nbt.putLong("first_seen", this.firstSeenTick);
        nbt.putLong("play_ticks", this.playTicks);
        nbt.putInt("sleeps", this.sleepCount);
        nbt.putInt("deaths", this.deathCount);
        nbt.putInt("anomalies", this.anomaliesSeen);
        nbt.putInt("majors", this.majorAnomaliesSeen);
        if (this.firstDeathPosition != null) {
            nbt.putLong("first_death", this.firstDeathPosition.asLong());
        }
        if (this.firstSleepPosition != null) {
            nbt.putLong("first_sleep", this.firstSleepPosition.asLong());
        }
        NbtCompound dims = new NbtCompound();
        for (Map.Entry<String, Long> entry : this.firstDimensionEntry.entrySet()) {
            dims.putLong(entry.getKey(), entry.getValue());
        }
        nbt.put("dimensions", dims);
        NbtUtil.writeStringSet(nbt, "lore", this.discoveredLore);
        NbtUtil.writeStringSet(nbt, "clues", this.clues);
        NbtUtil.writeStringSet(nbt, "events", this.completedEvents);
        NbtUtil.writeStringSet(nbt, "anchors", this.anchorsSeen);
        NbtUtil.writeStringSet(nbt, "majors", this.majorAnomalies);
        NbtCompound marks = new NbtCompound();
        for (Map.Entry<String, Long> entry : this.marks.entrySet()) {
            marks.putLong(entry.getKey(), entry.getValue());
        }
        nbt.put("marks", marks);
        nbt.putInt("ledger_state", this.ledgerState);
        nbt.putString("anchor_status", this.anchorStatus);
        nbt.putBoolean("observation_complete", this.observationComplete);
        nbt.putLong("last_anomaly", this.lastAnomalyTick == Long.MIN_VALUE ? Long.MIN_VALUE / 2 : this.lastAnomalyTick);
        nbt.putLong("last_major", this.lastMajorAnomalyTick == Long.MIN_VALUE ? Long.MIN_VALUE / 2 : this.lastMajorAnomalyTick);
        nbt.put("home", this.home.toNbt());
        return nbt;
    }

    public static UncannyPlayerData fromNbt(NbtCompound nbt, UUID uuid) {
        UncannyPlayerData data = new UncannyPlayerData(uuid);
        data.username = NbtUtil.readString(nbt, "username", "unknown");
        data.firstSeenTick = NbtUtil.readLong(nbt, "first_seen", 0);
        data.playTicks = NbtUtil.readLong(nbt, "play_ticks", 0);
        data.sleepCount = NbtUtil.readInt(nbt, "sleeps", 0);
        data.deathCount = NbtUtil.readInt(nbt, "deaths", 0);
        data.anomaliesSeen = NbtUtil.readInt(nbt, "anomalies", 0);
        data.majorAnomaliesSeen = NbtUtil.readInt(nbt, "majors", 0);
        if (nbt.contains("first_death")) {
            data.firstDeathPosition = BlockPos.fromLong(nbt.getLong("first_death"));
        }
        if (nbt.contains("first_sleep")) {
            data.firstSleepPosition = BlockPos.fromLong(nbt.getLong("first_sleep"));
        }
        NbtCompound dims = NbtUtil.child(nbt, "dimensions");
        for (String key : dims.getKeys()) {
            data.firstDimensionEntry.put(key, dims.getLong(key));
        }
        data.discoveredLore.addAll(NbtUtil.readStringSet(nbt, "lore"));
        data.clues.addAll(NbtUtil.readStringSet(nbt, "clues"));
        data.completedEvents.addAll(NbtUtil.readStringSet(nbt, "events"));
        data.anchorsSeen.addAll(NbtUtil.readStringSet(nbt, "anchors"));
        data.majorAnomalies.addAll(NbtUtil.readStringSet(nbt, "majors"));
        NbtCompound marks = NbtUtil.child(nbt, "marks");
        for (String key : marks.getKeys()) {
            data.marks.put(key, marks.getLong(key));
        }
        data.ledgerState = NbtUtil.readInt(nbt, "ledger_state", 0);
        data.anchorStatus = NbtUtil.readString(nbt, "anchor_status", "UNKNOWN");
        data.observationComplete = NbtUtil.readBool(nbt, "observation_complete", false);
        long lastAnomaly = NbtUtil.readLong(nbt, "last_anomaly", Long.MIN_VALUE / 2);
        long lastMajor = NbtUtil.readLong(nbt, "last_major", Long.MIN_VALUE / 2);
        data.lastAnomalyTick = lastAnomaly <= Long.MIN_VALUE / 2 + 1 ? Long.MIN_VALUE : lastAnomaly;
        data.lastMajorAnomalyTick = lastMajor <= Long.MIN_VALUE / 2 + 1 ? Long.MIN_VALUE : lastMajor;
        if (nbt.contains("home", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            HomeFingerprint home = HomeFingerprint.fromNbt(nbt.getCompound("home"));
            data.home.placedBlocks.putAll(home.placedBlocks);
            data.home.containers.addAll(home.containers);
            data.home.deathPositions.addAll(home.deathPositions);
            data.home.bedPosition = home.bedPosition;
            data.home.spawnPosition = home.spawnPosition;
            data.home.firstSignificantItem = home.firstSignificantItem;
            data.home.placements = home.placements;
            data.home.removals = home.removals;
        }
        return data;
    }
}
