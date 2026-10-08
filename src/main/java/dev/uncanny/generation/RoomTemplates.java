package dev.uncanny.generation;

import dev.uncanny.builders.ArchiveTemplates;
import dev.uncanny.builders.HallTemplates;
import dev.uncanny.builders.PartitionTemplates;
import dev.uncanny.dimension.UncannyDimension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The library of rooms.
 *
 * Rooms are registered here once at startup, grouped by the layer they belong to.
 * A template can be registered for several layers, which is how the Archive gets
 * corridor pieces that look like the Hall's, and how the Partition ends up holding
 * fragments of everywhere.
 *
 * Adding a room means writing one builder method and one register() line. Nothing
 * else in the mod has to change - that is the point of the template system.
 */
public final class RoomTemplates {

    private static final Map<String, RoomTemplate> BY_ID = new LinkedHashMap<>();
    private static final Map<UncannyDimension, List<RoomTemplate>> BY_DIMENSION = new EnumMap<>(UncannyDimension.class);

    private RoomTemplates() {
    }

    /** Registers a template for one layer. */
    public static void register(UncannyDimension dimension, RoomTemplate template) {
        BY_ID.put(template.id, template);
        BY_DIMENSION.computeIfAbsent(dimension, key -> new ArrayList<>()).add(template);
    }

    /** Registers the same template for several layers at once. */
    public static void register(RoomTemplate template, UncannyDimension... dimensions) {
        for (UncannyDimension dimension : dimensions) {
            register(dimension, template);
        }
    }

    public static RoomTemplate byId(String id) {
        return BY_ID.get(id);
    }

    public static List<RoomTemplate> forDimension(UncannyDimension dimension) {
        return BY_DIMENSION.getOrDefault(dimension, Collections.emptyList());
    }

    public static int count() {
        return BY_ID.size();
    }

    /** Every registered id, for the debug command and the docs. */
    public static List<String> ids() {
        return new ArrayList<>(BY_ID.keySet());
    }

    /**
     * Builds the whole library. Called once from the mod initialiser.
     *
     * Split into one method per layer so it is obvious where to add things.
     */
    public static void registerAll() {
        BY_ID.clear();
        BY_DIMENSION.clear();
        HallTemplates.register();
        ArchiveTemplates.register();
        PartitionTemplates.register();
    }
}
