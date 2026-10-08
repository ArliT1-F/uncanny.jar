package dev.uncanny.lore;

import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Documents that are written rather than loaded.
 *
 * Most Surveyor records are the same shape over and over: a heading, a
 * measurement, a signature. Generating them from a seed means a world can be full
 * of them without the mod shipping hundreds of near-identical JSON files, and it
 * means the numbers inside them are stable - a record you read twice has not
 * quietly changed its figures.
 *
 * The writing style here is the house style for the whole mod: no adjectives, no
 * warnings, no emotion. Only measurements, and the occasional sentence that turns
 * out to be impossible.
 */
public final class SurveyorRecord {

    /** Surveyor names. Kept short and ordinary on purpose. */
    private static final String[] NAMES = {
            "T. ASHEN", "R. VELL", "M. OKONKWO", "H. BRAND", "E. SORRELL",
            "J. MARROW", "P. ILV", "A. GRAYNE", "S. TOLM", "D. REEVE"
    };

    /** Boundary identifiers. Never explained in game. */
    private static final String[] BOUNDARIES = {
            "BOUNDARY 4-A", "BOUNDARY 12-C", "BOUNDARY 7", "SECTION 19",
            "SECTION 22-E", "CORRIDOR 8", "APERTURE 3", "STATION 6"
    };

    private SurveyorRecord() {
    }

    /** Builds one record appropriate to a lore stage. */
    public static LoreEntry compose(long seed, int stage) {
        RandomUtil.UncannyRandom random = RandomUtil.of(seed);
        String name = NAMES[random.nextInt(NAMES.length)];
        String boundary = BOUNDARIES[random.nextInt(BOUNDARIES.length)];
        int room = 1 + random.nextInt(40);
        int day = 1 + random.nextInt(400);
        double drift = random.nextInt(900) / 100.0;

        List<String> pages = new ArrayList<>();
        String title;

        if (stage <= 1) {
            // STAGE 1: this is a boring document. It should read as filler.
            title = "BOUNDARY MEASUREMENT";
            pages.add(boundary);
            pages.add("DRIFT " + drift + "mm PER DAY");
            pages.add("WITHIN TOLERANCE.");
            pages.add(name);
        } else if (stage == 2) {
            title = "SURVEY NOTE " + (100 + room);
            pages.add("ROOM " + room + " DOES NOT APPEAR");
            pages.add("ON ANY MAP.");
            pages.add("MEASURED AGAINST THREE");
            pages.add("SEPARATE SURVEYS.");
        } else if (stage == 3) {
            title = "SURVEY NOTE " + (100 + room) + " (AMENDED)";
            pages.add("ROOM " + room + " HAS NOW BEEN FOUND");
            pages.add("IN THREE LOCATIONS.");
            pages.add("ALL THREE ARE INTERIOR.");
            pages.add("ALL THREE ARE OCCUPIED.");
        } else if (stage == 4) {
            title = "PERSONNEL DISCREPANCY";
            pages.add(name.split(" ")[0] + " DIED ON DAY " + day + ".");
            pages.add(name.split(" ")[0] + " IS CURRENTLY IN ROOM " + room + ".");
            pages.add("BOTH STATEMENTS ARE SIGNED.");
            pages.add("NEITHER HAS BEEN WITHDRAWN.");
        } else if (stage == 5) {
            title = "ON THE LIGHTS";
            pages.add("THE STARS ARE NOT OUTSIDE");
            pages.add("THE PARTITION.");
            pages.add("THEY ARE BEHIND IT.");
            pages.add("SEE ATTACHED PLATE.");
        } else if (stage == 6) {
            title = "COMPARATIVE TEXTS";
            pages.add("THE OLDER ACCOUNTS DESCRIBE");
            pages.add("WHEELS WITHIN WHEELS,");
            pages.add("AND RIMS FULL OF EYES.");
            pages.add("RING CHAMBER 3 MATCHES.");
        } else {
            title = "UNFILED";
            pages.add("THIS DOCUMENT WAS FOUND");
            pages.add("INSIDE A SEALED DRAWER.");
            pages.add("IT IS WRITTEN IN A HAND");
            pages.add("WE HAVE NOT IDENTIFIED.");
        }
        return new LoreEntry("generated:" + SeedUtil.mix(seed, stage), title, pages,
                LoreEntry.Category.SURVEY, stage, 1.0, false);
    }

    /** A page describing an anchor room's occupant, for the room itself. */
    public static List<String> anchorPlaque(int number, String status, long seed) {
        RandomUtil.UncannyRandom random = RandomUtil.of(seed);
        List<String> lines = new ArrayList<>();
        lines.add("ANCHOR " + String.format("%03d", number));
        lines.add("STATUS: " + status);
        if (random.nextBoolean()) {
            lines.add("TENURE: " + (100 + random.nextInt(9000)) + " DAYS");
        } else {
            lines.add("TENURE: UNRECORDED");
        }
        return lines;
    }
}
