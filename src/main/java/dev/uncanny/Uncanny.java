package dev.uncanny;

import net.minecraft.util.Identifier;

/**
 * The one place that knows the mod id.
 *
 * Everywhere else in the code calls Uncanny.id("thing") instead of writing
 * "uncanny:thing" by hand, so a typo cannot silently create a second namespace.
 */
public final class Uncanny {

    /** Must match the "id" field in fabric.mod.json. */
    public static final String MOD_ID = "uncanny";

    /** Human readable name, used in logs and in the debug screen. */
    public static final String NAME = "Uncanny";

    private Uncanny() {
    }

    /** Builds a namespaced identifier: Uncanny.id("hall") -> "uncanny:hall". */
    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    /** Same as {@link #id(String)} but returns the plain string, for NBT keys. */
    public static String key(String path) {
        return MOD_ID + ":" + path;
    }
}
