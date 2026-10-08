package dev.uncanny.data;

import dev.uncanny.player.UncannyPlayerData;
import dev.uncanny.util.NbtUtil;
import dev.uncanny.util.PositionUtil;
import dev.uncanny.util.SeedUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the mod remembers about a WORLD, saved with the world.
 *
 * Split deliberately into two halves:
 *
 *   WORLD STATE  - seals, generated chunks, lore that everyone shares, where the
 *                  Ledger is. Consistent for every player on a server.
 *   PLAYER STATE - one {@link UncannyPlayerData} per uuid. Different per player,
 *                  which is why two people can be in the same corridor and only
 *                  one of them hears the knock.
 *
 * In Minecraft, {@link PersistentState} is written to
 * <world>/data/uncanny_world.dat when the world saves, so nothing here is lost
 * when the server stops.
 */
public final class UncannyWorldState extends PersistentState {

    /** The save file id. Changing this would abandon every existing world's memory. */
    public static final String ID = "uncanny_world";

    /** How many seals exist. Seven, and that number is not negotiable. */
    public static final int SEAL_COUNT = 7;

    // ---------------------------------------------------------- world state

    /** Integrity of each seal, 100 = intact, 0 = open. Index 0 is SEAL I. */
    private final int[] seals = new int[SEAL_COUNT];

    /** Lore every player has effectively shared (records read by anyone). */
    private final Set<String> worldLore = new LinkedHashSet<>();

    /** Anchors the world has already assigned a room to, by anchor number. */
    private final Set<Integer> assignedAnchors = new HashSet<>();

    /**
     * Chunks this mod has already filled in, per dimension. Without this the mod
     * would rebuild corridors every time a chunk reloads, which would both waste
     * time and let the player watch a room rebuild itself.
     */
    private final Map<String, Set<Long>> generatedChunks = new HashMap<>();

    /** Doorways that lead somewhere else: packed BlockPos -> target dimension path. */
    private final Map<Long, String> thresholds = new HashMap<>();

    /** The uuid whose home drives the House and the Copy. */
    private UUID subject = null;

    /** Where the Ledger was placed, or null until the Archive generates it. */
    private BlockPos ledgerPosition = null;

    /** A per-world seed for the eye motif, so patterns differ between worlds. */
    private long eyeSeed = 0;

    /** The centre of the ring chamber, once one has been built. */
    private BlockPos ringPosition = null;

    /** Set once the final area has been generated. */
    private boolean partitionBuilt = false;

    /** Set once any player has been told they are vacant. */
    private boolean observationClosed = false;

    /** Tick the world first noticed the mod, used for slow-burn pacing. */
    private long startedAtTick = 0;

    // --------------------------------------------------------- player state

    private final Map<UUID, UncannyPlayerData> players = new HashMap<>();

    public UncannyWorldState() {
        for (int i = 0; i < SEAL_COUNT; i++) {
            this.seals[i] = 100;
        }
    }

    // -------------------------------------------------------------- access

    /** Gets (or creates) the state for a world. Called from server code only. */
    public static UncannyWorldState get(ServerWorld world) {
        return get(world.getServer());
    }

    public static UncannyWorldState get(MinecraftServer server) {
        // The Overworld is the right home for this: it outlives every other
        // dimension and it is always loaded, so the memory cannot go missing.
        ServerWorld overworld = server.getOverworld();
        PersistentStateManager manager = overworld.getPersistentStateManager();
        return manager.getOrCreate(UncannyWorldState::fromNbt, UncannyWorldState::new, ID);
    }

    /** Gets a player's data, creating a blank record the first time. */
    public UncannyPlayerData player(UUID uuid) {
        return this.players.computeIfAbsent(uuid, UncannyPlayerData::new);
    }

    public boolean hasPlayer(UUID uuid) {
        return this.players.containsKey(uuid);
    }

    public Iterable<UncannyPlayerData> allPlayers() {
        return this.players.values();
    }

    // --------------------------------------------------------------- seals

    /** Integrity of one seal, 0..100. Index 0 is SEAL I. */
    public int sealIntegrity(int index) {
        if (index < 0 || index >= SEAL_COUNT) {
            return 100;
        }
        return this.seals[index];
    }

    /** Damages a seal and marks the world dirty. Returns the new integrity. */
    public int weakenSeal(int index, int amount) {
        if (index < 0 || index >= SEAL_COUNT) {
            return 100;
        }
        this.seals[index] = Math.max(0, this.seals[index] - amount);
        markDirty();
        return this.seals[index];
    }

    /** True once a seal has given way entirely. */
    public boolean sealOpen(int index) {
        return sealIntegrity(index) <= 0;
    }

    public int openedSealCount() {
        int count = 0;
        for (int seal : this.seals) {
            if (seal <= 0) {
                count++;
            }
        }
        return count;
    }

    /** Average integrity across all seals, 0..100. */
    public int averageIntegrity() {
        int total = 0;
        for (int seal : this.seals) {
            total += seal;
        }
        return total / SEAL_COUNT;
    }

    /** Roman numeral used everywhere in the writing: SEAL IV. */
    public static String sealName(int index) {
        String[] names = {"I", "II", "III", "IV", "V", "VI", "VII"};
        return index >= 0 && index < names.length ? names[index] : String.valueOf(index + 1);
    }

    // ------------------------------------------------------------ world lore

    public void markWorldLore(String loreId) {
        if (this.worldLore.add(loreId)) {
            markDirty();
        }
    }

    public boolean knowsWorldLore(String loreId) {
        return this.worldLore.contains(loreId);
    }

    public Set<String> worldLore() {
        return this.worldLore;
    }

    /**
     * Reserves an anchor number for the world, so two anchor rooms in one world
     * never claim the same person. Deterministic in the sense that the same call
     * order gives the same answer, and stable afterwards because it is saved.
     */
    public boolean claimAnchor(int number) {
        if (this.assignedAnchors.add(number)) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean anchorTaken(int number) {
        return this.assignedAnchors.contains(number);
    }

    // -------------------------------------------------------- chunk marking

    private Set<Long> chunkSet(String dimensionPath) {
        return this.generatedChunks.computeIfAbsent(dimensionPath, k -> new HashSet<>());
    }

    /** Remembers that a chunk has been filled in. */
    public void markChunkGenerated(String dimensionPath, ChunkPos pos) {
        chunkSet(dimensionPath).add(PositionUtil.pack(pos));
    }

    public boolean isChunkGenerated(String dimensionPath, ChunkPos pos) {
        return chunkSet(dimensionPath).contains(PositionUtil.pack(pos));
    }

    public int generatedChunkCount(String dimensionPath) {
        return chunkSet(dimensionPath).size();
    }

    // ----------------------------------------------------------- thresholds

    /** Registers a doorway. Walking through it moves the player. */
    public void addThreshold(BlockPos pos, String targetDimensionPath) {
        this.thresholds.put(pos.asLong(), targetDimensionPath);
        markDirty();
    }

    public void removeThreshold(BlockPos pos) {
        this.thresholds.remove(pos.asLong());
        markDirty();
    }

    /** The dimension a doorway leads to, or null if this is not a doorway. */
    public String thresholdTarget(BlockPos pos) {
        return this.thresholds.get(pos.asLong());
    }

    public int thresholdCount() {
        return this.thresholds.size();
    }

    // --------------------------------------------------------------- subject

    public UUID subject() {
        return this.subject;
    }

    /** The first player to progress far enough becomes the subject of the House. */
    public void setSubject(UUID uuid) {
        if (this.subject == null && uuid != null) {
            this.subject = uuid;
            markDirty();
        }
    }

    public BlockPos ledgerPosition() {
        return this.ledgerPosition;
    }

    public void setLedgerPosition(BlockPos pos) {
        this.ledgerPosition = pos == null ? null : pos.toImmutable();
        markDirty();
    }

    /** Per-world seed for eye arrangements. Set on first use, then fixed. */
    public long eyeSeed(long worldSeed) {
        if (this.eyeSeed == 0) {
            this.eyeSeed = SeedUtil.derive(worldSeed, "eyes");
            markDirty();
        }
        return this.eyeSeed;
    }

    public BlockPos ringPosition() {
        return this.ringPosition;
    }

    /** Records the ring chamber. The first one built wins; there is only one. */
    public void setRingPosition(BlockPos pos) {
        if (this.ringPosition == null && pos != null) {
            this.ringPosition = pos.toImmutable();
            markDirty();
        }
    }

    public boolean isPartitionBuilt() {
        return this.partitionBuilt;
    }

    public void setPartitionBuilt(boolean value) {
        this.partitionBuilt = value;
        markDirty();
    }

    public boolean isObservationClosed() {
        return this.observationClosed;
    }

    public void closeObservation() {
        this.observationClosed = true;
        markDirty();
    }

    public long startedAtTick() {
        return this.startedAtTick;
    }

    public void noteStart(long tick) {
        if (this.startedAtTick == 0) {
            this.startedAtTick = tick;
            markDirty();
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putIntArray("seals", this.seals);
        NbtUtil.writeStringSet(nbt, "world_lore", this.worldLore);

        int[] anchors = new int[this.assignedAnchors.size()];
        int i = 0;
        for (Integer anchor : this.assignedAnchors) {
            anchors[i++] = anchor;
        }
        nbt.putIntArray("anchors", anchors);

        NbtCompound chunks = new NbtCompound();
        for (Map.Entry<String, Set<Long>> entry : this.generatedChunks.entrySet()) {
            long[] packed = new long[entry.getValue().size()];
            int j = 0;
            for (Long value : entry.getValue()) {
                packed[j++] = value;
            }
            NbtUtil.writeLongArray(chunks, entry.getKey(), packed);
        }
        nbt.put("chunks", chunks);

        NbtCompound doors = new NbtCompound();
        for (Map.Entry<Long, String> entry : this.thresholds.entrySet()) {
            doors.putString(Long.toString(entry.getKey()), entry.getValue());
        }
        nbt.put("thresholds", doors);

        if (this.subject != null) {
            nbt.putUuid("subject", this.subject);
        }
        if (this.ledgerPosition != null) {
            nbt.putLong("ledger", this.ledgerPosition.asLong());
        }
        nbt.putLong("eye_seed", this.eyeSeed);
        if (this.ringPosition != null) {
            nbt.putLong("ring", this.ringPosition.asLong());
        }
        nbt.putBoolean("partition_built", this.partitionBuilt);
        nbt.putBoolean("observation_closed", this.observationClosed);
        nbt.putLong("started_at", this.startedAtTick);

        NbtCompound playerNbt = new NbtCompound();
        for (Map.Entry<UUID, UncannyPlayerData> entry : this.players.entrySet()) {
            playerNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put("players", playerNbt);
        return nbt;
    }

    /** Called by Minecraft when the world loads. */
    public static UncannyWorldState fromNbt(NbtCompound nbt) {
        UncannyWorldState state = new UncannyWorldState();

        int[] seals = nbt.contains("seals") ? nbt.getIntArray("seals") : new int[0];
        for (int i = 0; i < seals.length && i < SEAL_COUNT; i++) {
            state.seals[i] = seals[i];
        }

        state.worldLore.addAll(NbtUtil.readStringSet(nbt, "world_lore"));

        for (int anchor : (nbt.contains("anchors") ? nbt.getIntArray("anchors") : new int[0])) {
            state.assignedAnchors.add(anchor);
        }

        NbtCompound chunks = NbtUtil.child(nbt, "chunks");
        for (String key : chunks.getKeys()) {
            Set<Long> set = state.chunkSet(key);
            for (long packed : NbtUtil.readLongArray(chunks, key)) {
                set.add(packed);
            }
        }

        NbtCompound doors = NbtUtil.child(nbt, "thresholds");
        for (String key : doors.getKeys()) {
            try {
                state.thresholds.put(Long.parseLong(key), doors.getString(key));
            } catch (NumberFormatException ignored) {
                // A malformed key is dropped rather than crashing world load.
            }
        }

        if (nbt.containsUuid("subject")) {
            state.subject = nbt.getUuid("subject");
        }
        if (nbt.contains("ledger")) {
            state.ledgerPosition = BlockPos.fromLong(nbt.getLong("ledger"));
        }
        state.eyeSeed = NbtUtil.readLong(nbt, "eye_seed", 0);
        if (nbt.contains("ring")) {
            state.ringPosition = BlockPos.fromLong(nbt.getLong("ring"));
        }
        state.partitionBuilt = NbtUtil.readBool(nbt, "partition_built", false);
        state.observationClosed = NbtUtil.readBool(nbt, "observation_closed", false);
        state.startedAtTick = NbtUtil.readLong(nbt, "started_at", 0);

        NbtCompound players = NbtUtil.child(nbt, "players");
        for (String key : players.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                state.players.put(uuid, UncannyPlayerData.fromNbt(players.getCompound(key), uuid));
            } catch (IllegalArgumentException ignored) {
                // Same reasoning as above.
            }
        }
        return state;
    }
}
