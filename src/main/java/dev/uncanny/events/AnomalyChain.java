package dev.uncanny.events;

import dev.uncanny.player.UncannyPlayerData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anomaly chains: cause and effect without a quest log.
 *
 * A chain is nothing but an ordered list of stage names. An event that
 * "advances" a chain marks its own stage when it runs; an event that comes
 * "after" a stage checks the mark before it may run. That is the entire
 * mechanism - no scheduling, no guarantees, no progression bar.
 *
 * What makes it feel like cause and effect rather than a rigid sequence:
 *
 *   - every stage keeps its OWN conditions and probability, so a player may
 *     never see a later stage at all (the design explicitly wants that);
 *   - the marks are persistent states like "the_tree:missing_tree", so the
 *     world genuinely remembers what happened to THIS player;
 *   - stages can be marked by non-event things too (the Ledger marks its own
 *     reference stage when it writes the line), which is how a document can sit
 *     in the middle of a chain.
 *
 * The one chain wired up so far is THE TREE, following the design document's
 * example:
 *
 *   missing_tree -> ledger reference -> wrong_tree -> woods convergence
 *
 * Adding a chain is one line in the static block plus `advances`/`after` on
 * the events involved.
 */
public final class AnomalyChain {

    /** The missing tree, its record in the Ledger, its wrong return, and the Woods. */
    public static final String TREE = "the_tree";

    private static final Map<String, List<String>> CHAINS = new LinkedHashMap<>();

    static {
        CHAINS.put(TREE, List.of(
                "missing_tree",
                "ledger_reference",
                "wrong_tree",
                "woods_convergence"));
    }

    private AnomalyChain() {
    }

    /** All chain ids, for the debug command. */
    public static List<String> ids() {
        return List.copyOf(CHAINS.keySet());
    }

    /** The stages of one chain, in order. Empty if the chain is unknown. */
    public static List<String> stages(String chain) {
        List<String> stages = CHAINS.get(chain);
        return stages == null ? List.of() : stages;
    }

    /** Records that a stage of a chain has happened for this player. */
    public static void mark(UncannyPlayerData data, String chain, String stage) {
        data.memory.markFlag(chain + ":" + stage);
    }

    /** Whether a specific stage has happened. */
    public static boolean reached(UncannyPlayerData data, String chain, String stage) {
        return data.memory.hasFlag(chain + ":" + stage);
    }

    /** Whether every entry in the given list ("chain:stage") has happened. */
    public static boolean reachedAll(UncannyPlayerData data, List<String> marks) {
        for (String mark : marks) {
            if (!data.memory.hasFlag(mark)) {
                return false;
            }
        }
        return true;
    }

    /** How many stages of a chain this player has completed, for diagnostics. */
    public static int stageOf(UncannyPlayerData data, String chain) {
        int count = 0;
        for (String stage : stages(chain)) {
            if (reached(data, chain, stage)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
