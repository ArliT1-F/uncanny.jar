package dev.uncanny.lore;

/**
 * The nine stages the writing moves through.
 *
 * The stage is never shown to the player. It is only used to decide which
 * documents may appear, which anomalies are allowed, and how the Ledger phrases
 * itself. The point is that the same room can hold a boring measurement in an
 * early world and the same room can hold a name in a late one.
 */
public enum LoreStage {

    /** Boundary measurements remain stable. */
    NORMAL(1, "NORMAL"),
    /** Room 18 does not appear on any map. */
    STRANGE(2, "STRANGE"),
    /** Room 18 has now been found in three different locations. */
    IMPOSSIBLE(3, "IMPOSSIBLE"),
    /** Thomas died yesterday. Thomas is currently in Room 18. */
    IDENTITY(4, "IDENTITY"),
    /** The stars are not outside the Partition. They are behind it. */
    COSMIC(5, "COSMIC"),
    /** Seals, the abyss, watchers, wheels, eyes, judgment, stars, the book. */
    BIBLICAL(6, "BIBLICAL"),
    /** The documents start referring to the player. */
    PERSONAL(7, "PERSONAL"),
    /** The player finds their own entry in the Ledger. */
    REVELATION(8, "REVELATION"),
    /** Nothing is confirmed. Nothing is ruled out. */
    UNCERTAINTY(9, "UNCERTAINTY");

    private final int level;
    private final String label;

    LoreStage(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int level() {
        return this.level;
    }

    public String label() {
        return this.label;
    }

    /** Looks a stage up by number, clamped into range so config typos cannot crash. */
    public static LoreStage of(int level) {
        for (LoreStage stage : values()) {
            if (stage.level == level) {
                return stage;
            }
        }
        return level < 1 ? NORMAL : UNCERTAINTY;
    }

    /** True when this stage is at least as advanced as the given level. */
    public boolean atLeast(int level) {
        return this.level >= level;
    }
}
