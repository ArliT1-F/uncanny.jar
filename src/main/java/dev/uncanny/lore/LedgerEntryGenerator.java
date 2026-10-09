package dev.uncanny.lore;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.events.AnomalyChain;
import dev.uncanny.player.RealityInstability;
import dev.uncanny.player.UncannyPlayerData;
import dev.uncanny.util.RandomUtil;
import dev.uncanny.util.SeedUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * FIELD LOG: the Ledger, written from what the player actually did.
 *
 * The Surveyors record in their own vocabulary - ANCHOR 741, ENTERED, OBSERVED -
 * and every normal line is true: these are the dimension entries the player made,
 * in the order they happened, with the repetition the Ledger insists on once a
 * place is visited often enough:
 *
 *   ANCHOR 741 ENTERED WOODS.
 *   ANCHOR 741 ENTERED WOODS.
 *   ANCHOR 741 ENTERED WOODS.
 *
 * Then, rarely, and only once the player's reality has thinned (MEDIUM+), the
 * register says something that cannot be true while the player is holding the
 * book:
 *
 *   ANCHOR 741 IS CURRENTLY IN WOODS.        (they are somewhere else)
 *   ANCHOR 741 WILL ENTER THE ARCHIVE.       (they have not)
 *   ANCHOR 741 HAS NEVER ENTERED THE HALL.   (they have)
 *
 * One impossible line per opening at most, rolled low, never the same trick
 * twice in a row if we can help it. The Ledger must never become a quest log:
 * it is a register that has started to disagree with reality.
 *
 * This is also where THE TREE chain's document stage lives: once the player has
 * seen a missing tree, the next opening carries a canopy line, and marks the
 * chain stage that lets the wrong tree come back later.
 */
public final class LedgerEntryGenerator {

    /** The chain stage this file is the middle of. */
    private static final String TREE_LEDGER_STAGE = "ledger_reference";

    private LedgerEntryGenerator() {
    }

    /** Builds the FIELD LOG pages for one opening of the Ledger. */
    public static List<List<String>> fieldLog(UncannyPlayerData data, UncannyWorldState state) {
        List<List<String>> pages = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        String entry = "ENTRY " + data.entryLabel();

        // ---- normal entries, in the order they happened ----
        List<Map.Entry<String, Long>> entries = new ArrayList<>(data.firstDimensionEntry.entrySet());
        entries.sort(Comparator.comparingLong(Map.Entry::getValue));
        for (Map.Entry<String, Long> dimension : entries) {
            String name = displayName(dimension.getKey());
            int visits = Math.max(1, data.memory.visitsIn(dimension.getKey()));
            int copies = Math.min(3, visits);
            for (int i = 0; i < copies; i++) {
                lines.add(entry + " ENTERED " + name + ".");
            }
        }

        // ---- the chain's document stage: a canopy line, once ----
        if (data.hasClue("tree_missing") && !data.memory.hasFlag(
                AnomalyChain.TREE + ":" + TREE_LEDGER_STAGE)) {
            lines.add(entry + ": TREES MISSING: NONE.");
            lines.add(entry + ": CANOPY COUNT: CORRECT.");
            AnomalyChain.mark(data, AnomalyChain.TREE, TREE_LEDGER_STAGE);
            state.markDirty();
        }

        // ---- and then, rarely, something impossible ----
        String impossible = impossibleLine(data);
        if (impossible != null) {
            lines.add(impossible);
        }

        if (lines.isEmpty()) {
            return pages;
        }
        // Eight lines a page, like every other page in the book.
        for (int start = 0; start < lines.size(); start += 8) {
            List<String> page = new ArrayList<>();
            if (start == 0) {
                page.add("FIELD LOG");
            }
            page.addAll(lines.subList(start, Math.min(lines.size(), start + 8)));
            pages.add(page);
        }
        return pages;
    }

    /**
     * The one line that cannot be true, or null.
     *
     * Rolled per opening with a time-seeded stream: this is a moment, not world
     * generation, and it must vary between openings rather than being fixed by
     * the seed forever.
     */
    private static String impossibleLine(UncannyPlayerData data) {
        RealityInstability.Band band = RealityInstability.band(data);
        if (band == RealityInstability.Band.LOW) {
            return null;
        }
        RandomUtil.UncannyRandom random = RandomUtil.of(SeedUtil.mix(
                data.uuid.getLeastSignificantBits(), System.currentTimeMillis()));
        String current = displayName(data.currentDimension);
        String entry = "ENTRY " + data.entryLabel();

        if (random.chance(band == RealityInstability.Band.HIGH ? 0.18 : 0.10)) {
            // "IS CURRENTLY IN ..." - the player is holding the book somewhere else.
            String claimed = mostVisitedOther(data);
            if (claimed != null) {
                return entry + " IS CURRENTLY IN " + claimed + ".";
            }
        }
        if (random.chance(0.08)) {
            // "HAS NEVER ENTERED ..." - for a place they have entered.
            String visited = randomOtherVisited(data, random);
            if (visited != null) {
                return entry + " HAS NEVER ENTERED " + visited + ".";
            }
        }
        if (random.chance(0.08)) {
            // "WILL ENTER ..." - for a place they have not.
            String unvisited = randomUnvisited(data, random);
            if (unvisited != null) {
                return entry + " WILL ENTER " + unvisited + ".";
            }
        }
        if (band == RealityInstability.Band.HIGH && random.chance(0.06)) {
            // An entry that begins appearing and becomes unreadable.
            return entry + " IS CU" + "████████";
        }
        return null;
    }

    // ------------------------------------------------------------- helpers

    /** "minecraft:overworld" -> "OVERWORLD", "hall" -> "HALL". */
    public static String displayName(String path) {
        int colon = path.indexOf(':');
        return (colon >= 0 ? path.substring(colon + 1) : path).toUpperCase(java.util.Locale.ROOT);
    }

    private static String mostVisitedOther(UncannyPlayerData data) {
        String best = null;
        int bestVisits = 0;
        for (Map.Entry<String, Integer> entry : data.memory.dimensionVisits.entrySet()) {
            if (entry.getKey().equals(data.currentDimension)) {
                continue;
            }
            if (entry.getValue() > bestVisits) {
                best = entry.getKey();
                bestVisits = entry.getValue();
            }
        }
        if (best == null) {
            // Only the current dimension has visits; fall back to anything else
            // they have entered at least once.
            for (String key : data.firstDimensionEntry.keySet()) {
                if (!key.equals(data.currentDimension)) {
                    return displayName(key);
                }
            }
            return null;
        }
        return displayName(best);
    }

    private static String randomOtherVisited(UncannyPlayerData data, RandomUtil.UncannyRandom random) {
        List<String> options = new ArrayList<>();
        for (String key : data.firstDimensionEntry.keySet()) {
            if (!key.equals(data.currentDimension)) {
                options.add(displayName(key));
            }
        }
        return options.isEmpty() ? null : random.pick(options);
    }

    private static String randomUnvisited(UncannyPlayerData data, RandomUtil.UncannyRandom random) {
        String[] all = {"hall", "house", "copy", "woods", "archive", "deep", "abyss", "partition"};
        List<String> options = new ArrayList<>();
        for (String dim : all) {
            if (!data.firstDimensionEntry.containsKey(dim)) {
                options.add(displayName(dim));
            }
        }
        return options.isEmpty() ? null : random.pick(options);
    }
}
