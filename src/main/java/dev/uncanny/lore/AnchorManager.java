package dev.uncanny.lore;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The numbered rooms and the people who were in them.
 *
 * An anchor room is a small, furnished, lived-in room with a number on it. The
 * mod never explains the numbering system, and it never shows the player a list.
 * It only ever shows one room at a time, so the player has to notice for
 * themselves that the numbers are close together.
 *
 * The journals follow one shape: a person who was told this would be temporary.
 */
public final class AnchorManager {

    /** Statuses an anchor room can report. Never explained in game. */
    public static final String PRESENT = "PRESENT";
    public static final String ABSENT = "ABSENT";
    public static final String DECEASED = "DECEASED";
    public static final String VACANT = "VACANT";
    public static final String UNRECORDED = "UNRECORDED";
    public static final String ACTIVE = "ACTIVE";

    private AnchorManager() {
    }

    /**
     * Reserves an anchor number for a room.
     *
     * Numbers are claimed on the world state, so two rooms in the same world never
     * describe the same person. Numbers near the player's own are deliberately
     * more common late in the game, which is how the player ends up standing in
     * ANCHOR 740 without yet knowing what 741 means.
     */
    public static int reserve(UncannyWorldState state, long seed, int playerEntry) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "anchor"));
        for (int attempt = 0; attempt < 40; attempt++) {
            int number;
            if (attempt > 24 && playerEntry > 0) {
                // Late attempts cluster near the player's own number.
                number = Math.max(1, playerEntry - random.nextInt(4));
            } else if (attempt > 12 && playerEntry > 0) {
                number = Math.max(1, playerEntry - 1 - random.nextInt(40));
            } else {
                number = 1 + random.nextInt(999);
            }
            if (state.claimAnchor(number)) {
                return number;
            }
        }
        return 1 + random.nextInt(999);
    }

    /**
     * The anchor number for a room, as a pure function of the room's seed.
     *
     * Pure on purpose: the number has to be recomputable later, when a player is
     * standing in the room, without having been stored anywhere. Two rooms in a
     * world can therefore share a number. That is not a bug - "the same room found
     * in three locations" is one of the things the Surveyors wrote down.
     */
    public static int numberFor(long seed) {
        long roll = SeedUtil.derive(seed, "anchor_number");
        return 1 + Math.floorMod((int) roll, 999);
    }

    public static String label(int number) {
        return "ANCHOR " + String.format("%03d", number);
    }

    /** The status line for a room. Mostly unrecorded; some are worse. */
    public static String status(long seed, int number) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "status"));
        double roll = random.nextDouble();
        if (roll < 0.34) {
            return UNRECORDED;
        }
        if (roll < 0.52) {
            return ABSENT;
        }
        if (roll < 0.72) {
            return DECEASED;
        }
        if (roll < 0.88) {
            return PRESENT;
        }
        if (roll < 0.96) {
            return VACANT;
        }
        return ACTIVE;
    }

    /**
     * The journal in the room.
     *
     * The shape is always the same and it always gets worse at the same rate.
     * The knock on day 2,104 is the one players remember, and it is never
     * explained, because the room is empty and the log is unsigned.
     */
    public static List<String> journal(long seed, int number) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "journal"));
        List<String> pages = new ArrayList<>();
        String voice = random.nextBoolean() ? "I" : "THE OCCUPANT";

        pages.add(String.join("\n",
                label(number),
                "DAY 12",
                "They said this would only",
                "last until the boundary",
                "stabilized."));

        pages.add(String.join("\n",
                "DAY 311",
                voice + (voice.equals("I") ? " don't" : " does not"),
                "remember what outside",
                "looks like."));

        if (random.nextBoolean()) {
            pages.add(String.join("\n",
                    "DAY 1,044",
                    "The measurements are",
                    "mine now. Nobody has",
                    "come to collect them."));
        }

        pages.add(String.join("\n",
                "DAY 2,104",
                "Someone knocked today."));

        pages.add(String.join("\n",
                "DAY 2,104",
                voice + (voice.equals("I") ? " was" : " was"),
                "alone."));

        pages.add(String.join("\n",
                "DAY 2,105",
                "Someone knocked again."));

        return pages;
    }

    /** Short wall text. Used for the tally marks and the scratched notes. */
    public static List<String> wallText(long seed, int number) {
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.derive(seed, "wall"));
        List<String> options = List.of(
                "NOT THE FIRST ROOM",
                "THE WINDOW IS NOT A WINDOW",
                "COUNTED 1,412 DAYS",
                "THEY STOPPED ANSWERING",
                "I CAN HEAR THE OTHER ROOMS",
                "DO NOT KNOCK BACK",
                "THE NUMBER CHANGED",
                "STILL HERE"
        );
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            out.add(options.get(random.nextInt(options.size())));
        }
        return out;
    }

    /**
     * The plaque outside the door. This is the only place a status is stated
     * plainly, and it is always short.
     */
    public static List<String> plaque(int number, String status) {
        return SurveyorRecord.anchorPlaque(number, status, SeedUtil.mix(number, 0xA11C40L));
    }
}
