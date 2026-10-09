package dev.uncanny.lore;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.player.UncannyPlayerData;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * THE LEDGER.
 *
 * The Ledger is not a book of facts. It is a register, and it contains people.
 *
 * Everything on its pages is derived from what actually happened to the player:
 * where they first slept, where they first died, which layers they entered and in
 * what order, how many anchor rooms they stood in. The mod does not invent events
 * to be creepy - it reports real ones in a voice that makes them sound like they
 * were expected.
 *
 * The Ledger has five states, and each one adds pages:
 *
 *   0  closed. It cannot be opened. Every attempt is simply refused.
 *   1  it opens. The first page is a name.
 *   2  it begins answering back.
 *   3  STATUS: ACTIVE
 *   4  STATUS: ANCHOR
 *   5  STATUS: VACANT
 *
 * Nothing in the mod ever says what VACANT means.
 */
public final class LedgerManager {

    private LedgerManager() {
    }

    /** Builds the whole book for one player at their current state. */
    public static List<List<String>> pages(UncannyPlayerData data, UncannyWorldState state) {
        List<List<String>> pages = new ArrayList<>();
        String entry = "ENTRY " + data.entryLabel();

        // Page one is always a name. Nothing else. No explanation.
        pages.add(List.of(data.username.toUpperCase()));

        if (data.ledgerState < 1) {
            return pages;
        }

        pages.add(List.of(entry, "", "OPENED", "BY THE SUBJECT"));

        // ---- real events, in the order they happened ----

        if (data.sleepCount > 0) {
            pages.add(List.of(entry, "FIRST SLEEP", where(data.firstSleepPosition),
                    data.sleepCount <= 1 ? "NO ANOMALY DETECTED" : "SLEPT " + data.sleepCount + " TIMES"));
        }

        if (data.deathCount > 0) {
            pages.add(List.of(entry, "FIRST TERMINATION", where(data.firstDeathPosition),
                    "TOTAL: " + data.deathCount));
        }

        int entered = 0;
        for (Map.Entry<String, Long> dimension : data.firstDimensionEntry.entrySet()) {
            entered++;
            if (entered == 1) {
                pages.add(List.of(entry, "FIRST ENTRY", dimension.getKey().toUpperCase(),
                        "NO ANOMALY DETECTED"));
            } else if (entered == 2) {
                pages.add(List.of(entry, "SECOND ENTRY", dimension.getKey().toUpperCase(),
                        "SUBJECT REMEMBERS THE FIRST."));
            } else if (entered <= 5) {
                pages.add(List.of(entry, "ENTRY " + entered, dimension.getKey().toUpperCase(),
                        "BOUNDARY CONTACT: CONFIRMED"));
            }
        }

        if (!data.anchorsSeen.isEmpty()) {
            pages.add(List.of(entry, "SUBJECT HAS STOOD IN", data.anchorsSeen.size()
                    + " ANCHOR ROOM(S)", "SUBJECT DID NOT ASK", "WHO THEY WERE."));
        }

        if (data.clues.size() >= 3) {
            pages.add(List.of(entry, "SUBJECT HAS BEGUN", "SEARCHING."));
        }

        if (data.discoveredLore.size() >= 5) {
            pages.add(List.of(entry, "SUBJECT HAS READ", data.discoveredLore.size() + " RECORDS.",
                    "SIX WERE NOT FILED", "BY US."));
        }

        if (data.anomaliesSeen >= 8) {
            pages.add(List.of(entry, "PRESSURE RELEASES", "OBSERVED: " + data.anomaliesSeen,
                    "ALL WITHIN", "EXPECTED RANGE."));
        }

        // ---- the register itself ----

        if (data.ledgerState >= 2) {
            pages.add(List.of(entry, "SUBJECT IS AWARE", "OF THE RECORD."));
        }

        // ---- FIELD LOG: what the subject actually did, in Surveyor voice ----
        pages.addAll(LedgerEntryGenerator.fieldLog(data, state));

        pages.add(registerPage(state));

        if (data.ledgerState >= 3) {
            pages.add(List.of(entry, "STATUS: ACTIVE"));
        }
        if (data.ledgerState >= 4) {
            pages.add(List.of(entry, "STATUS: ANCHOR"));
        }
        if (data.ledgerState >= 5) {
            // The last page. There is no page after it.
            pages.add(List.of(entry, "STATUS: VACANT"));
        }

        return pages;
    }

    /**
     * The list of previous anchors.
     *
     * The list is long on purpose. The player should have to turn the page several
     * times before they reach the bottom, and the bottom is always the same two
     * lines.
     */
    private static List<String> registerPage(UncannyWorldState state) {
        List<String> lines = new ArrayList<>();
        lines.add("REGISTER");
        lines.add("ANCHOR 738 - VACANT");
        lines.add("ANCHOR 739 - DECEASED");
        lines.add("ANCHOR 740 - DECEASED");
        return lines;
    }

    /** The final page shown in the Partition, after the observation closes. */
    public static List<List<String>> finalPages(UncannyPlayerData data, UncannyWorldState state) {
        List<List<String>> pages = pages(data, state);
        pages.add(List.of("ANCHOR 740", "DECEASED"));
        pages.add(List.of(data.anchorLabel(), "ACTIVE"));
        pages.add(List.of(data.anchorLabel(), "OBSERVATION", "COMPLETE"));
        pages.add(List.of(data.anchorLabel(), "VACANT"));
        return pages;
    }

    /** Formats a position the way the Surveyors would have written it. */
    private static String where(BlockPos pos) {
        if (pos == null) {
            return "LOCATION UNRECORDED";
        }
        return "AT " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** Advances the Ledger one state. Returns the new state. */
    public static int advance(UncannyPlayerData data, UncannyWorldState state) {
        data.ledgerState = Math.min(5, data.ledgerState + 1);
        // The Ledger noticing you thins things, quietly.
        dev.uncanny.player.RealityInstability.raise(data, 0.006);
        if (data.ledgerState >= 3) {
            data.anchorStatus = data.ledgerState >= 5 ? AnchorManager.VACANT : AnchorManager.ACTIVE;
        }
        if (data.ledgerState >= 5) {
            state.closeObservation();
            data.observationComplete = true;
        }
        state.markDirty();
        return data.ledgerState;
    }

    /**
     * Whether the Ledger will open for this player.
     *
     * Before this, every attempt to use it does nothing at all. Not a message, not
     * a sound. The book is simply a book.
     */
    public static boolean opensFor(UncannyPlayerData data, UncannyWorldState state) {
        return data.ledgerState >= 1
                || dev.uncanny.player.PlayerProgress.ledgerOpensFor(data, state);
    }
}
