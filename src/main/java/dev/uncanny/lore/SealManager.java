package dev.uncanny.lore;

import dev.uncanny.data.UncannyWorldState;
import dev.uncanny.player.PlayerProgress;
import dev.uncanny.player.UncannyPlayerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * THE SEVEN SEALS.
 *
 * The Surveyors believed the seals were failing. They wrote it down several times,
 * in several documents, with measurements. They were wrong, and the mod never
 * corrects them.
 *
 * Mechanically, a seal is one integer between 0 and 100. Seals only ever go down,
 * and only when someone in the world has actually gone somewhere - which is why
 * the pacing of the whole story is tied to exploration rather than to a timer.
 *
 * A weakened seal is not a boss bar and it is not announced. The player finds out
 * from a plate in a wall, or from a document, or not at all.
 */
public final class SealManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("uncanny/seals");

    /** How often the world is considered for seal decay, in ticks. */
    public static final int DECAY_INTERVAL = 20 * 60 * 5;

    /** How much progress the world needs before the first seal gives. */
    public static final int[] THRESHOLDS = {4, 9, 15, 22, 30, 40, 52};

    private SealManager() {
    }

    /**
     * Slow decay, driven by progress rather than by the clock.
     *
     * The total progress of the world is the sum of what its players have done. On
     * a multiplayer server that means a group moves through the story faster than a
     * solo player, which is correct: the Partition has more subjects to work with.
     */
    public static void decay(MinecraftServer server, UncannyWorldState state, long tick) {
        int progress = worldProgress(server, state);
        int opened = state.openedSealCount();
        if (opened >= UncannyWorldState.SEAL_COUNT) {
            return;
        }
        if (progress >= THRESHOLDS[opened]) {
            int index = opened;
            state.weakenSeal(index, 100);
            LOGGER.info("[uncanny] SEAL {} has given way (world progress {})",
                    UncannyWorldState.sealName(index), progress);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UncannyPlayerData data = state.player(player.getUuid());
                data.markClue("seal_" + UncannyWorldState.sealName(index).toLowerCase());
            }
            state.markDirty();
        }
    }

    /** A single measure of how much has happened in this world. */
    public static int worldProgress(MinecraftServer server, UncannyWorldState state) {
        int total = 0;
        for (UncannyPlayerData data : state.allPlayers()) {
            total += data.discoveredLore.size()
                    + data.clues.size()
                    + (data.anchorsSeen.size() * 2)
                    + (data.firstDimensionEntry.size() * 3)
                    + data.majorAnomaliesSeen * 4;
        }
        return total;
    }

    /** Weakens a specific seal, used by events that want to move the story. */
    public static void weaken(UncannyWorldState state, int index, int amount) {
        state.weakenSeal(index, amount);
    }

    /** True when a given seal is at least partly damaged. */
    public static boolean damaged(UncannyWorldState state, int index) {
        return state.sealIntegrity(index) < 100;
    }

    /**
     * The line a document uses when a seal gives.
     *
     * Never "the seal has broken". Always a measurement.
     */
    public static String reportLine(int index) {
        return "SEAL " + UncannyWorldState.sealName(index) + " - PRESSURE RELEASE OBSERVED";
    }

    /** Whether the player has earned the right to be told about the seals at all. */
    public static boolean mayKnow(ServerPlayerEntity player, UncannyWorldState state) {
        return PlayerProgress.stage(state.player(player.getUuid()), state).atLeast(LoreStage.BIBLICAL.level());
    }

    /** Convenience for tests and for the debug command. */
    public static String describe(UncannyWorldState state) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < UncannyWorldState.SEAL_COUNT; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(UncannyWorldState.sealName(i)).append('=').append(state.sealIntegrity(i));
        }
        return out.toString();
    }
}
