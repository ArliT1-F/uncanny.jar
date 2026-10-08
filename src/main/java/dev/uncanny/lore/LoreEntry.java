package dev.uncanny.lore;

import java.util.ArrayList;
import java.util.List;

/**
 * One document.
 *
 * A lore entry is just a title, a list of pages, and the rules for when it may
 * appear. Entries are normally loaded from
 * src/main/resources/data/uncanny/lore/*.json so they can be edited, translated or
 * removed without recompiling anything.
 */
public final class LoreEntry {

    /** What kind of document it is. Used to vary where it turns up. */
    public enum Category {
        /** Measurements, logs, inventories. The scientific voice. */
        SURVEY,
        /** A person's own handwriting. */
        JOURNAL,
        /** A correction, a memo, or a note in the margin. */
        MEMO,
        /** Older than the Surveyors. Never explained. */
        ANCIENT,
        /** Refers to the player directly. Late game only. */
        PERSONAL
    }

    public final String id;
    public final String title;
    public final List<String> pages;
    public final Category category;
    /** The earliest lore stage this may appear at. */
    public final int minStage;
    /** Relative chance of being picked. */
    public final double weight;
    /** If true, a world only ever hands this out once. */
    public final boolean unique;

    public LoreEntry(String id, String title, List<String> pages, Category category,
                     int minStage, double weight, boolean unique) {
        this.id = id;
        this.title = title;
        this.pages = new ArrayList<>(pages);
        this.category = category;
        this.minStage = minStage;
        this.weight = weight;
        this.unique = unique;
    }

    /** True when this entry is allowed to appear at the given stage. */
    public boolean availableAt(int stage) {
        return stage >= this.minStage;
    }

    @Override
    public String toString() {
        return "LoreEntry[" + this.id + " stage>=" + this.minStage + " " + this.category + "]";
    }
}
