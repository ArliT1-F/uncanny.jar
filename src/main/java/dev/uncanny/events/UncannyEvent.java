package dev.uncanny.events;

import dev.uncanny.config.UncannyConfig;
import dev.uncanny.util.RandomUtil;

/**
 * One anomaly.
 *
 * An event is a condition, a probability, and something that happens. The base
 * class handles everything that is the same for all of them - severity, cooldown,
 * whether it may repeat - so a concrete event only has to say what makes it
 * different.
 *
 * The severity ladder is the pacing of the whole mod:
 *
 *   QUIET      the player will probably not notice. That is the point.
 *   NOTICED    the player notices, and tells themselves it was already like that.
 *   IMPOSSIBLE the player cannot explain it. Rare.
 *   MAJOR      a named event, once per world per player. Very rare.
 */
public abstract class UncannyEvent {

    public enum Severity {
        QUIET(0.5), NOTICED(1.0), IMPOSSIBLE(2.0), MAJOR(4.0);

        private final double weight;

        Severity(double weight) {
            this.weight = weight;
        }

        /** Rarer events are picked less often, on top of their own probability. */
        public double weight() {
            return this.weight;
        }
    }

    public final String id;
    public final Severity severity;

    /** Conditions that must all hold. */
    protected EventCondition condition = EventCondition.always();

    /** Base probability per check, before severity and config are applied. */
    protected double probability = 0.1;

    /** Minimum lore stage. */
    protected int minStage = 1;

    /** True if the event may only ever happen once per player. */
    protected boolean oneTime = false;

    protected UncannyEvent(String id, Severity severity) {
        this.id = id;
        this.severity = severity;
    }

    // ------------------------------------------------------------ builders

    protected UncannyEvent when(EventCondition value) {
        this.condition = value;
        return this;
    }

    protected UncannyEvent chance(double value) {
        this.probability = value;
        return this;
    }

    protected UncannyEvent fromStage(int level) {
        this.minStage = level;
        return this;
    }

    protected UncannyEvent onlyOnce() {
        this.oneTime = true;
        return this;
    }

    // ----------------------------------------------------------- evaluation

    /**
     * Whether this event may run right now.
     *
     * Order matters for cost: the cheap checks run first, and the conditions - the
     * only part that touches the world - run last.
     */
    public boolean canRun(EventContext context, RandomUtil.UncannyRandom random) {
        UncannyConfig config = UncannyConfig.get();
        if (context.stage() < this.minStage) {
            return false;
        }
        if (this.oneTime && context.data.eventDone(this.id)) {
            return false;
        }
        long now = context.tick;
        long cooldown = this.severity == Severity.MAJOR
                ? config.majorCooldownTicks
                : config.anomalyCooldownTicks;
        long last = this.severity == Severity.MAJOR
                ? context.data.lastMajorAnomalyTick
                : context.data.lastAnomalyTick;
        if (last != Long.MIN_VALUE && now - last < cooldown) {
            return false;
        }
        if (!this.condition.test(context)) {
            return false;
        }
        double scaled = this.probability * this.severity.weight() * config.anomalyFrequency;
        return random.chance(Math.min(1.0, scaled));
    }

    /** Runs the event and books the cooldown. */
    public void run(EventContext context) {
        perform(context);
        if (this.severity == Severity.MAJOR) {
            context.data.lastMajorAnomalyTick = context.tick;
            context.data.majorAnomaliesSeen++;
            context.data.majorAnomalies.add(this.id);
        } else {
            context.data.lastAnomalyTick = context.tick;
            context.data.anomaliesSeen++;
        }
        if (this.oneTime) {
            context.data.markEventDone(this.id);
        }
        context.state.markDirty();
    }

    /** What actually happens. Implemented by each concrete event. */
    protected abstract void perform(EventContext context);

    @Override
    public String toString() {
        return this.id + " (" + this.severity + ")";
    }
}
