package dev.uncanny.player;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.events.UncannyEvent;

/**
 * How the anomalies bend towards one particular player.
 *
 * The rule the design doc insists on: do not punish anyone for their playstyle.
 * So every bias here is a mild multiplier, clamped to [0.7, 1.7], applied to the
 * PICK WEIGHT of an eligible event - never to eligibility itself. An event the
 * player's behaviour does not favour can still happen; it just loses the vote a
 * little more often. Nothing is ever switched off.
 *
 * The biases, and what feeds them (all from {@link PlayerMemory} and the
 * existing counters in {@link UncannyPlayerData}):
 *
 *   lives underground      -> environmental and auditory anomalies matter more
 *   builds a lot           -> architectural anomalies and Reality Echoes matter more
 *   investigates anomalies -> dimensional and identity families open up
 *   revisits one area      -> anomalies concentrate where they keep returning
 *   revisits one layer     -> that layer becomes more responsive to them
 */
public final class PlayerBehavior {

    private PlayerBehavior() {
    }

    /**
     * The pick-weight multiplier for one event, for one player.
     *
     * @param event       the eligible event being considered
     * @param data        the player's persistent data
     * @param areaKey     the coarse area the player is standing in right now
     * @param favoriteArea the coarse area this player visits the most, or null
     */
    public static double selectionBias(UncannyEvent event, UncannyPlayerData data,
                                       String areaKey, String favoriteArea) {
        PlayerMemory memory = data.memory;
        UncannyEvent.Family family = event.family();
        double bias = 1.0;

        // Underground dwellers get a world that is wrong more often down there.
        if (family == UncannyEvent.Family.ENVIRONMENTAL
                || family == UncannyEvent.Family.AUDITORY) {
            bias *= 0.85 + 0.7 * memory.undergroundRatio();
        }

        // Builders get architecture that argues with them.
        double building = clamp01(data.home.placements / 80.0);
        if (family == UncannyEvent.Family.ARCHITECTURAL
                || family == UncannyEvent.Family.FALSE_NORMALITY) {
            bias *= 0.85 + 0.5 * building;
        }

        // ... and their own actions coming back wrong, slightly later.
        if (family == UncannyEvent.Family.ECHO) {
            bias *= 0.75 + 0.75 * building;
        }

        // Players who investigate get shown the deeper machinery.
        double investigated = clamp01(data.anomaliesSeen / 25.0);
        if (family == UncannyEvent.Family.DIMENSIONAL
                || family == UncannyEvent.Family.IDENTITY
                || family == UncannyEvent.Family.LEDGER) {
            bias *= 0.8 + 0.5 * investigated;
        }

        // Anomalies begin appearing where the player keeps returning.
        if (areaKey != null && areaKey.equals(favoriteArea)) {
            bias *= 1.3;
        }

        // A layer the player revisits often becomes more responsive to them.
        if (data.currentDimension != null && memory.visitsIn(data.currentDimension) >= 4) {
            bias *= 1.2;
        }

        return clamp(bias, 0.7, 1.7);
    }

    /**
     * Whether this player has investigated enough to be shown the advanced
     * families (Reality Echoes, identity events, deeper overlap). A player who
     * never goes looking is never forced down that road.
     */
    public static boolean advancedFamilies(UncannyPlayerData data, UncannyWorldState state) {
        return data.anomaliesSeen >= 6
                || PlayerProgress.stage(data, state).atLeast(
                        dev.uncanny.lore.LoreStage.REVELATION.level());
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
