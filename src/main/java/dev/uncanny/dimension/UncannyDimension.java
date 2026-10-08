package dev.uncanny.dimension;

import dev.uncanny.Uncanny;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The layers of the same reality.
 *
 * Each one is a real Minecraft dimension, defined in
 * src/main/resources/data/uncanny/dimension/<path>.json. The JSON decides the
 * boring parts (height limits, chunk generator, biome); this enum decides the
 * parts the mod cares about: how it is filled in, where you arrive, and whether
 * it is allowed to misbehave.
 *
 * Adding a new layer means adding one entry here plus one JSON file. Nothing else
 * in the mod needs to know.
 */
public enum UncannyDimension {

    /** Normal Minecraft. Everything starts here and nothing is changed quickly. */
    OVERWORLD("minecraft:overworld", "OVERWORLD", Mode.NONE, 64, false),

    /** A corridor network built from modules. The first thing most players find. */
    HALL("hall", "THE HALL", Mode.MODULE, 32, true),

    /** An echo of the player's own home, assembled from what they built. */
    HOUSE("house", "THE HOUSE", Mode.ECHO, 64, true),

    /** Almost the player's world. Not quite. */
    COPY("copy", "THE COPY", Mode.NATURE, 68, true),

    /** A forest whose geography does not behave. */
    WOODS("woods", "THE WOODS", Mode.NATURE, 70, true),

    /** The Archive: shelves, reading rooms, and the Ledger. */
    ARCHIVE("archive", "THE ARCHIVE", Mode.MODULE, 40, true),

    /** Underground, going down further than it should. Ends at the Prison of Stars. */
    DEEP("deep", "THE DEEP", Mode.CARVE, 48, true),

    /** Below the Deep. Unfinished. */
    ABYSS("abyss", "BELOW", Mode.VOID, 32, false),

    /** The last place. Fragments of every other layer, and the Ledger. */
    PARTITION("partition", "THE PARTITION", Mode.PATCHWORK, 48, false);

    /**
     * How a layer gets its blocks.
     *
     * MODULE     - one room template per chunk, from the template library
     * CARVE      - solid rock, with tunnels and rooms cut out of it
     * ECHO       - built from the player's remembered home, not from a seed
     * NATURE     - vanilla terrain generation, with the mod interfering afterwards
     * PATCHWORK  - module templates borrowed from every other layer
     * VOID       - almost nothing, deliberately
     * NONE       - the mod does not generate anything here (the Overworld)
     */
    public enum Mode {
        NONE, MODULE, CARVE, ECHO, NATURE, PATCHWORK, VOID
    }

    private final String path;
    private final String displayName;
    private final Mode mode;
    private final int connectorY;
    private final boolean anomaliesAllowed;

    UncannyDimension(String path, String displayName, Mode mode, int connectorY, boolean anomaliesAllowed) {
        this.path = path;
        this.displayName = displayName;
        this.mode = mode;
        this.connectorY = connectorY;
        this.anomaliesAllowed = anomaliesAllowed;
    }

    /** The dimension id path, e.g. "hall". The Overworld is special-cased. */
    public String path() {
        return this.path;
    }

    public Identifier id() {
        if (this == OVERWORLD) {
            return new Identifier("minecraft", "overworld");
        }
        return Uncanny.id(this.path);
    }

    /** The key used to look the dimension up on a server. */
    public RegistryKey<World> key() {
        if (this == OVERWORLD) {
            return World.OVERWORLD;
        }
        return RegistryKey.of(RegistryKeys.WORLD, id());
    }

    public RegistryKey<net.minecraft.world.dimension.DimensionType> typeKey() {
        return RegistryKey.of(RegistryKeys.DIMENSION_TYPE, id());
    }

    public String displayName() {
        return this.displayName;
    }

    public Mode mode() {
        return this.mode;
    }

    /** Height at which corridors meet between chunks. Fixed per layer on purpose:
     *  every module puts its doorways here, so modules always line up. */
    public int connectorY() {
        return this.connectorY;
    }

    public boolean anomaliesAllowed() {
        return this.anomaliesAllowed;
    }

    /** Where a player arrives. Deterministic, and near the layer's origin. */
    public BlockPos arrival() {
        return new BlockPos(8, this.connectorY + 1, 8);
    }

    /** Looks a layer up from a world key, or null for a dimension we do not own. */
    public static UncannyDimension fromPath(String path) {
        for (UncannyDimension dimension : values()) {
            if (dimension.path.equals(path)) {
                return dimension;
            }
        }
        return null;
    }

    /** Convenience: the namespace-stripped path of a world key. */
    public static String pathOf(RegistryKey<World> key) {
        return key.getValue().getPath();
    }

    /** True for every layer this mod generates, including the Overworld check. */
    public static boolean isUncanny(String path) {
        return fromPath(path) != null && fromPath(path) != OVERWORLD;
    }

    @Override
    public String toString() {
        return Uncanny.MOD_ID + ":" + this.path;
    }
}
