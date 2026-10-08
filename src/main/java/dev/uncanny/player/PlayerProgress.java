package dev.uncanny.player;

import dev.uncanny.config.UncannyConfig;
import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.dimension.UncannyDimension;
import dev.uncanny.lore.LoreStage;

/**
 * Turns raw counters into "how far in is this player".
 *
 * Kept separate from the data class on purpose: the data is what happened, this
 * file is what it means. Changing the pacing of the whole mod should mean editing
 * one method, not hunting through event classes.
 */
public final class PlayerProgress {

    private PlayerProgress() {
    }

    /**
     * The current lore stage for one player.
     *
     * It rises from three sources at once so that no single activity can rush it:
     * things read, places been, and how weak the seals have become.
     */
    public static LoreStage stage(UncannyPlayerData data, UncannyWorldState world) {
        UncannyConfig config = UncannyConfig.get();
        int perStage = Math.max(1, config.discoveriesPerStage);

        // Reading things is worth the most, because reading is a choice.
        int fromDiscoveries = (int) (data.totalDiscoveries() / perStage);

        // Having actually been somewhere counts once, no matter how long you stay.
        int fromDimensions = Math.max(0, data.firstDimensionEntry.size() - 1);

        // The world moving on without you still moves you along, just more slowly.
        int fromSeals = world.openedSealCount() / 2;

        int stage = config.startingStage + fromDiscoveries + fromDimensions + fromSeals;

        // The last two stages are never handed out for free.
        if (stage >= LoreStage.REVELATION.level() && data.ledgerState < 1) {
            stage = LoreStage.PERSONAL.level();
        }
        if (stage >= LoreStage.UNCERTAINTY.level() && !data.observationComplete) {
            stage = LoreStage.REVELATION.level();
        }
        return LoreStage.of(stage);
    }

    public static boolean hasEntered(UncannyPlayerData data, String dimensionPath) {
        return data.firstDimensionEntry.containsKey(dimensionPath);
    }

    public static int dimensionsVisited(UncannyPlayerData data) {
        return data.firstDimensionEntry.size();
    }

    /** Minutes played, for grace periods and pacing. */
    public static double minutesPlayed(UncannyPlayerData data) {
        return data.playTicks / 1200.0;
    }

    /**
     * Whether the Ledger will open for this player.
     *
     * Deliberately not simply "stage 8": the player has to have been to the
     * Archive and to at least one other layer, so finding it is never the first
     * strange thing that happens to them.
     */
    public static boolean ledgerOpensFor(UncannyPlayerData data, UncannyWorldState world) {
        return stage(data, world).atLeast(LoreStage.PERSONAL.level())
                && hasEntered(data, UncannyDimension.ARCHIVE.path())
                && dimensionsVisited(data) >= 3;
    }

    /** True when the endgame sequence may begin. */
    public static boolean mayEnterPartition(UncannyPlayerData data, UncannyWorldState world) {
        return data.ledgerState >= 3 && world.openedSealCount() >= 5;
    }
}
