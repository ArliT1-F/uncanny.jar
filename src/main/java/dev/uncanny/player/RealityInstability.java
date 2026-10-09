package dev.uncanny.player;

import dev.uncanny.config.UncannyConfig;

/**
 * HIDDEN reality instability: 0.0 -> 1.0.
 *
 * An internal, persistent measure of how much this player's reality has been
 * exposed to the overlapping layers. It is never displayed, never hinted at,
 * and never described in HUD, chat, or books. The player is not meant to know
 * the number exists; they are meant to notice that the world has changed its
 * behaviour.
 *
 * It rises only slowly, through things that actually happened:
 *
 *   reading important lore            discovering an anomalous dimension
 *   encountering a major anomaly      standing in an anchor room
 *   a seal giving way                 revisiting a place where an anomaly was
 *   Ledger interactions
 *
 * It is capped at 1.0 and never decreases. How it is USED (by the event
 * director, the Ledger, and the dimensions) is deliberately banded rather than
 * linear:
 *
 *   LOW     < 0.25   subtle environmental inconsistencies
 *   MEDIUM  < 0.55   architectural inconsistencies, memory contradictions,
 *                    dimension overlap
 *   HIGH    >= 0.55  identity inconsistencies, Ledger contradictions,
 *                    stronger Partition and Seal phenomena
 *
 * Higher instability is NOT "more jumpscares". It changes WHICH kinds of
 * wrongness are available, and how wrong they are allowed to be.
 */
public final class RealityInstability {

    /** Below this: only the quietest environmental wrongness. */
    public static final double LOW_MAX = 0.25;

    /** Below this: architecture and memory may contradict. From here: identity. */
    public static final double MEDIUM_MAX = 0.55;

    public enum Band {
        LOW, MEDIUM, HIGH
    }

    private RealityInstability() {
    }

    /** The raw value, 0.0 .. 1.0. Internal only - do not show this to players. */
    public static double value(UncannyPlayerData data) {
        return data.realityInstability;
    }

    /** The band the player is currently in. */
    public static Band band(UncannyPlayerData data) {
        if (data.realityInstability >= MEDIUM_MAX) {
            return Band.HIGH;
        }
        if (data.realityInstability >= LOW_MAX) {
            return Band.MEDIUM;
        }
        return Band.LOW;
    }

    public static boolean atLeast(UncannyPlayerData data, Band band) {
        return band(data).ordinal() >= band.ordinal();
    }

    /**
     * Raises instability by a small amount, scaled by the config rate.
     *
     * Amounts passed here are already tiny (thousandths). The rate multiplier is
     * the single knob for "how fast does this world wake up"; it exists mainly so
     * a test pack can set it to 0 or 5 without touching code.
     */
    public static void raise(UncannyPlayerData data, double amount) {
        double rate = UncannyConfig.get().realityInstabilityRate;
        if (rate <= 0 || amount <= 0) {
            return;
        }
        data.realityInstability = Math.min(1.0, data.realityInstability + amount * rate);
    }

    /** For the debug command: sets the value directly, 0.0 .. 1.0. */
    public static void set(UncannyPlayerData data, double value) {
        data.realityInstability = Math.max(0.0, Math.min(1.0, value));
    }

    /** The gain an anomaly of this severity contributes. Deliberately small. */
    public static double gainFor(int severityOrdinal) {
        return switch (severityOrdinal) {
            case 0 -> 0.003;   // QUIET
            case 1 -> 0.006;   // NOTICED
            case 2 -> 0.012;   // IMPOSSIBLE
            case 3 -> 0.02;    // MAJOR
            default -> 0.003;
        };
    }
}
