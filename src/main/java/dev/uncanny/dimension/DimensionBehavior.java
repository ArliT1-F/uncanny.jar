package dev.uncanny.dimension;

import dev.uncanny.events.UncannyEvent;
import dev.uncanny.player.PlayerMemory;
import dev.uncanny.player.RealityInstability;
import dev.uncanny.player.UncannyPlayerData;

/**
 * What each layer IS, as selection behaviour.
 *
 * The layers were already different places: this class makes them different
 * RULES. None of it is a gameplay restriction - nothing is forbidden anywhere -
 * it is a multiplier on which kind of wrongness tends to surface where you are
 * standing, plus the documented identity the atmosphere events implement:
 *
 *   HALL       space/distance is unreliable. Corridors subtly change; repeated
 *              architecture becomes inconsistent.      (hall_shift)
 *   HOUSE      familiarity and identity. Architecture resembles places the
 *              player has been, made of what they build with.  (house_wall)
 *   COPY       duplication. Almost-identical copies of things, slightly wrong.
 *                                                (copy_duplicate, echoes x1.6)
 *   WOODS      navigation and memory. Paths rearrange; trees return wrong.
 *                                                (woods_path, the tree chain)
 *   ARCHIVE    information. Documents reference the player; the Ledger gets
 *              more important with instability.        (archive_note, ledger x1.5)
 *   DEEP       scale. Architecture suggests something enormous beneath.
 *                                                (deep_scale, auditory x1.2)
 *   BELOW      reality degradation. Boundaries conflict; pieces of other layers
 *              appear.                                 (below_fragment)
 *   PARTITION  convergence. Everything found elsewhere shows up here, and at
 *              HIGH instability the seals and the Ledger push hardest.
 *                                                (partition_fragment, seal boost)
 *   OVERWORLD  untouched, deliberately: bias 1.0, apart from rare overlap at
 *              MEDIUM instability (see overworld_bleed).
 *
 * Multipliers stay inside [0.75, 1.6]. A layer can make a kind of event more
 * likely; it can never make one impossible.
 */
public final class DimensionBehavior {

    private DimensionBehavior() {
    }

    /** How much this layer favours this event's family, for this player. */
    public static double weightBias(UncannyEvent event, UncannyDimension dimension,
                                    UncannyPlayerData data) {
        UncannyEvent.Family family = event.family();
        double bias = 1.0;

        switch (dimension) {
            case HALL -> {
                if (family == UncannyEvent.Family.ARCHITECTURAL
                        || family == UncannyEvent.Family.FALSE_NORMALITY) {
                    bias *= 1.35;
                }
                if (family == UncannyEvent.Family.AUDITORY) {
                    bias *= 1.15;
                }
            }
            case HOUSE -> {
                if (family == UncannyEvent.Family.ARCHITECTURAL) {
                    bias *= 1.25;
                }
                if (family == UncannyEvent.Family.IDENTITY) {
                    bias *= 1.3;
                }
            }
            case COPY -> {
                if (family == UncannyEvent.Family.ECHO) {
                    bias *= 1.6;
                }
                if (family == UncannyEvent.Family.FALSE_NORMALITY) {
                    bias *= 1.25;
                }
                if (family == UncannyEvent.Family.ECHO
                        && RealityInstability.atLeast(data, RealityInstability.Band.MEDIUM)) {
                    bias *= 1.2;
                }
            }
            case WOODS -> {
                if (family == UncannyEvent.Family.ENVIRONMENTAL) {
                    bias *= 1.35;
                }
            }
            case ARCHIVE -> {
                if (family == UncannyEvent.Family.LEDGER) {
                    bias *= 1.5;
                }
                if (family == UncannyEvent.Family.DIMENSIONAL) {
                    bias *= 1.1;
                }
            }
            case DEEP -> {
                if (family == UncannyEvent.Family.AUDITORY) {
                    bias *= 1.2;
                }
                if (family == UncannyEvent.Family.ENVIRONMENTAL) {
                    bias *= 1.2;
                }
            }
            case ABYSS -> {
                if (family == UncannyEvent.Family.DIMENSIONAL) {
                    bias *= 1.3;
                }
                if (family == UncannyEvent.Family.ENVIRONMENTAL) {
                    bias *= 1.15;
                }
            }
            case PARTITION -> {
                if (family == UncannyEvent.Family.LEDGER
                        || family == UncannyEvent.Family.DIMENSIONAL) {
                    bias *= 1.4;
                }
                // High instability: the converging layer pushes hardest.
                if (RealityInstability.atLeast(data, RealityInstability.Band.HIGH)
                        && (family == UncannyEvent.Family.LEDGER
                            || family == UncannyEvent.Family.DIMENSIONAL
                            || family == UncannyEvent.Family.IDENTITY)) {
                    bias *= 1.2;
                }
            }
            case OVERWORLD -> {
                // Deliberate stillness. The Overworld stays at neutral so its
                // rare wrongness reads as wrongness, not as weather.
            }
        }
        return Math.max(0.75, Math.min(1.6, bias));
    }

    /** The coarse area the player is standing in, for behaviour matching. */
    public static String areaKeyFor(UncannyPlayerData data, net.minecraft.util.math.BlockPos pos) {
        return PlayerMemory.areaKey(data.currentDimension, pos);
    }
}
