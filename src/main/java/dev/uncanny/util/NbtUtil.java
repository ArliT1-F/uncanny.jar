package dev.uncanny.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reading and writing NBT without the noise.
 *
 * Persistent state in Minecraft is NBT. Every getter here has a default value so
 * that loading a world saved by an older version of the mod never crashes: a
 * missing tag simply means "not discovered yet".
 */
public final class NbtUtil {

    private NbtUtil() {
    }

    public static int readInt(NbtCompound nbt, String key, int fallback) {
        return nbt.contains(key, NbtElement.INT_TYPE) ? nbt.getInt(key) : fallback;
    }

    public static long readLong(NbtCompound nbt, String key, long fallback) {
        return nbt.contains(key, NbtElement.LONG_TYPE) ? nbt.getLong(key) : fallback;
    }

    public static boolean readBool(NbtCompound nbt, String key, boolean fallback) {
        return nbt.contains(key, NbtElement.BYTE_TYPE) ? nbt.getBoolean(key) : fallback;
    }

    public static String readString(NbtCompound nbt, String key, String fallback) {
        return nbt.contains(key, NbtElement.STRING_TYPE) ? nbt.getString(key) : fallback;
    }

    public static void writeStringList(NbtCompound nbt, String key, List<String> values) {
        NbtList list = new NbtList();
        for (String value : values) {
            list.add(NbtString.of(value));
        }
        nbt.put(key, list);
    }

    public static List<String> readStringList(NbtCompound nbt, String key) {
        List<String> out = new ArrayList<>();
        NbtList list = nbt.getList(key, NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    public static void writeStringSet(NbtCompound nbt, String key, Set<String> values) {
        writeStringList(nbt, key, new ArrayList<>(values));
    }

    public static Set<String> readStringSet(NbtCompound nbt, String key) {
        return new HashSet<>(readStringList(nbt, key));
    }

    public static void writeLongArray(NbtCompound nbt, String key, long[] values) {
        nbt.putLongArray(key, values);
    }

    public static long[] readLongArray(NbtCompound nbt, String key) {
        return nbt.contains(key, NbtElement.LONG_ARRAY_TYPE) ? nbt.getLongArray(key) : new long[0];
    }

    /** Gets a nested compound, creating it if absent. */
    public static NbtCompound child(NbtCompound nbt, String key) {
        return nbt.contains(key, NbtElement.COMPOUND_TYPE) ? nbt.getCompound(key) : new NbtCompound();
    }
}
