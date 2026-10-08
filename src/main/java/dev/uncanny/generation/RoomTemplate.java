package dev.uncanny.generation;

/**
 * One reusable room.
 *
 * A template is a description, not a map. It says how big the room is, which
 * connection patterns it can sit on, how often it should be chosen, and which
 * builder draws it. The generator does the rest.
 *
 * Everything the design brief asks a template to declare is a field here:
 * identifier, size, entrances, weight, category, rarity, whether it may hold
 * anomalies, whether it may hold lore, and whether it can be a landmark.
 */
public final class RoomTemplate {

    /** What kind of room this is. Used for weighting and for landmark selection. */
    public enum Category {
        /** Plain corridors. The bulk of any dimension. */
        CORRIDOR,
        /** A room you can stand in. */
        ROOM,
        /** A named place: anchor rooms, ring chambers, the Ledger hall. */
        LANDMARK,
        /** Something that should not be there. */
        ANOMALY
    }

    /** How unusual the template is. Rarity is checked before weight. */
    public enum Rarity {
        COMMON(1.0),
        UNCOMMON(0.5),
        RARE(0.12),
        VERY_RARE(0.03);

        private final double roll;

        Rarity(double roll) {
            this.roll = roll;
        }

        public double roll() {
            return this.roll;
        }
    }

    public final String id;
    public final int width;
    public final int height;
    public final int depth;
    public final Category category;
    public final Rarity rarity;
    public final double baseWeight;
    public final boolean canContainAnomalies;
    public final boolean canContainLore;
    public final boolean isLandmark;
    /** False for templates that would rather draw their own paths than have them cut. */
    public final boolean ensuresOwnAccess;
    public final TemplateBuilder builder;

    /**
     * Which connection patterns this template can be placed on, as
     * {@link GenerationRules} bitmasks. A template with {1,2,4,8} accepts any
     * single doorway; a straight corridor accepts only 5 (N|S) and 10 (E|W).
     */
    private final int[] acceptedMasks;

    private RoomTemplate(Builder builder) {
        this.id = builder.id;
        this.width = builder.width;
        this.height = builder.height;
        this.depth = builder.depth;
        this.category = builder.category;
        this.rarity = builder.rarity;
        this.baseWeight = builder.baseWeight;
        this.canContainAnomalies = builder.canContainAnomalies;
        this.canContainLore = builder.canContainLore;
        this.isLandmark = builder.isLandmark;
        this.ensuresOwnAccess = builder.ensuresOwnAccess;
        this.builder = builder.builder;
        this.acceptedMasks = builder.acceptedMasks;
    }

    /** True when this template fits a chunk with the given connection pattern. */
    public boolean accepts(int linkMask) {
        for (int accepted : this.acceptedMasks) {
            if (accepted == linkMask) {
                return true;
            }
        }
        return false;
    }

    public int[] acceptedMasks() {
        return this.acceptedMasks.clone();
    }

    /** Readable form, used in logs and in the debug command. */
    @Override
    public String toString() {
        return "RoomTemplate[" + this.id + " " + this.width + "x" + this.height + "x" + this.depth
                + " " + this.category + " " + this.rarity + " w=" + this.baseWeight + "]";
    }

    /** Small builder so template definitions stay readable. */
    public static final class Builder {
        private String id = "unnamed";
        private int width = 16;
        private int height = 8;
        private int depth = 16;
        private Category category = Category.ROOM;
        private Rarity rarity = Rarity.COMMON;
        private double baseWeight = 1.0;
        private boolean canContainAnomalies = true;
        private boolean canContainLore = false;
        private boolean isLandmark = false;
        private boolean ensuresOwnAccess = false;
        private int[] acceptedMasks = {1, 2, 4, 8, 3, 5, 6, 9, 10, 12, 7, 11, 13, 14, 15};
        private TemplateBuilder builder = context -> {
        };

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder size(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            return this;
        }

        public Builder category(Category value) {
            this.category = value;
            return this;
        }

        public Builder rarity(Rarity value) {
            this.rarity = value;
            return this;
        }

        public Builder weight(double value) {
            this.baseWeight = value;
            return this;
        }

        public Builder anomalies(boolean value) {
            this.canContainAnomalies = value;
            return this;
        }

        public Builder lore(boolean value) {
            this.canContainLore = value;
            return this;
        }

        public Builder landmark(boolean value) {
            this.isLandmark = value;
            return this;
        }

        /**
         * Declares that this template draws its own walkable paths.
         *
         * The generator will then leave the interior alone instead of cutting
         * access spokes through it. Only do this if you have checked that every
         * doorway you can be placed on actually connects.
         */
        public Builder ensuresOwnAccess() {
            this.ensuresOwnAccess = true;
            return this;
        }

        /** Restricts the connection patterns this template may be placed on. */
        public Builder masks(int... values) {
            this.acceptedMasks = values;
            return this;
        }

        /** Accepts any pattern whose doorway count is one of the given values. */
        public Builder links(int... counts) {
            java.util.Set<Integer> wanted = new java.util.HashSet<>();
            for (int count : counts) {
                wanted.add(count);
            }
            int[] values = new int[16];
            int n = 0;
            for (int mask = 0; mask < 16; mask++) {
                if (wanted.contains(Integer.bitCount(mask))) {
                    values[n++] = mask;
                }
            }
            this.acceptedMasks = java.util.Arrays.copyOf(values, n);
            return this;
        }

        public Builder draw(TemplateBuilder value) {
            this.builder = value;
            return this;
        }

        public RoomTemplate build() {
            return new RoomTemplate(this);
        }
    }
}
