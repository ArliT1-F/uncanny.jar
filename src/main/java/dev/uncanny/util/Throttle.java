package dev.uncanny.util;

/**
 * "Only do this every N ticks" in one small object.
 *
 * The mod runs a lot of cheap checks on the server tick. Running all of them
 * every single tick (20 times a second) would be wasteful, so each subsystem
 * owns a Throttle and asks it whether it is time to run.
 */
public final class Throttle {

    private final int interval;
    private long lastRun = Long.MIN_VALUE;

    public Throttle(int intervalTicks) {
        this.interval = Math.max(1, intervalTicks);
    }

    /** True at most once every interval ticks. */
    public boolean ready(long currentTick) {
        if (currentTick - this.lastRun >= this.interval) {
            this.lastRun = currentTick;
            return true;
        }
        return false;
    }

    /** Forces the next ready() call to return true. */
    public void reset() {
        this.lastRun = Long.MIN_VALUE;
    }

    public int interval() {
        return this.interval;
    }
}
