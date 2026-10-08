package dev.uncanny.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * "Do this small thing a few ticks from now."
 *
 * Several anomalies have the same shape: change something, wait, change it back.
 * A door that shudders. A wall block that flickers. The change has to be visible
 * for a moment, which means it has to be scheduled rather than instantaneous,
 * and it has to be reverted, which means the original state has to survive until
 * then.
 *
 * Deliberately NOT persisted. A queued revert that dies with the server leaves
 * at most one block in its "wrong" state - which is an anomaly anyway - and a
 * file that can resurrect a block state from before a restart is a much scarier
 * thing to have in a save.
 *
 * Cost: one TreeMap poll per due entry, on the server thread, for entries that
 * were explicitly scheduled. Nothing loops over the world.
 */
public final class Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger("uncanny/scheduler");

    private static final TreeMap<Long, List<Runnable>> QUEUE = new TreeMap<>();

    private Scheduler() {
    }

    /** Queues a task for an absolute world tick. */
    public static void runAt(long tick, Runnable task) {
        QUEUE.computeIfAbsent(tick, key -> new ArrayList<>(2)).add(task);
        // Bound the queue the same way the sound queue is: a few dozen entries is
        // already far more than the mod can legitimately schedule at once.
        while (QUEUE.size() > 128) {
            QUEUE.pollFirstEntry();
        }
    }

    /** Called every server tick. Runs everything due at or before now. */
    public static void tick(long now) {
        if (QUEUE.isEmpty()) {
            return;
        }
        while (!QUEUE.isEmpty() && QUEUE.firstKey() <= now) {
            Map.Entry<Long, List<Runnable>> entry = QUEUE.pollFirstEntry();
            for (Runnable task : entry.getValue()) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    // One bad revert must not take the tick loop with it.
                    LOGGER.error("[uncanny] scheduled task failed", e);
                }
            }
        }
    }

    /** Clears pending tasks. Called when a server stops. */
    public static void reset() {
        QUEUE.clear();
    }
}
