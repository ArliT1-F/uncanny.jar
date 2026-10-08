package dev.uncanny.events;

import dev.uncanny.config.UncannyConfig;
import dev.uncanny.data.AnomalyLocation;
import dev.uncanny.player.PlayerBehavior;
import dev.uncanny.player.RealityInstability;
import dev.uncanny.util.RandomUtil;

/**
 * One anomaly.
 *
 * An event is a condition, a probability, and something that happens. The base
 * class handles everything that is the same for all of them - severity, cooldown,
 * whether it may repeat, which family it belongs to, which chain stage it sits
 * in, how unstable reality must be before it is allowed - so a concrete event
 * only has to say what makes it different.
 *
 * The severity ladder is the pacing of the whole mod:
 *
 *   QUIET      the player will probably not notice. That is the point.
 *   NOTICED    the player notices, and tells themselves it was already like that.
 *   IMPOSSIBLE the player cannot explain it. Rare.
 *   MAJOR      a named event, once per world per player. Very rare.
 *
 * Evaluation is split from selection: {@link #evaluate} says WHETHER an event
 * may run and, when asked, WHY NOT (the debug log and /uncanny evaluate use
 * that reason); the manager then picks among the survivors by weight.
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

    /**
     * What KIND of wrongness this is. Used by {@link dev.uncanny.player.PlayerBehavior}
     * and {@link dev.uncanny.dimension.DimensionBehavior} to bias selection
     * towards this player's playstyle and this layer's identity - never to
     * forbid anything.
     */
    public enum Family {
        /** The world itself: trees, graves, light, terrain. */
        ENVIRONMENTAL,
        /** Buildings, doors, signs, windows - things with intent behind them. */
        ARCHITECTURAL,
        /** Almost correct: a staircase with one extra step, the wrong wood. */
        FALSE_NORMALITY,
        /** Sound with no source. */
        AUDITORY,
        /** Layers bleeding into each other, thresholds, seals. */
        DIMENSIONAL,
        /** The player's own actions coming back wrong. */
        ECHO,
        /** Name, identity, who is recorded as present. */
        IDENTITY,
        /** The Ledger and things that read like records of the player. */
        LEDGER,
        /** An attempt that started and aborted. Uncommon on purpose. */
        NEAR_MISS
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

    /** Which kind of wrongness this is. Defaults to environmental. */
    protected Family family = Family.ENVIRONMENTAL;

    /**
     * Minimum hidden instability required, 0.0 .. 1.0. Gates new families into
     * the bands the design calls for: subtle first, architectural and memory in
     * the middle, identity and Ledger contradictions late. Existing events all
     * leave this at 0 so their pacing does not change.
     */
    protected double minInstability = 0.0;

    /** True: an aborted attempt. Own cooldown, does not count as an anomaly. */
    protected boolean nearMiss = false;

    /** True: requires the player to have investigated anomalies before. */
    protected boolean advanced = false;

    /** Chain this event belongs to, or null. See {@link AnomalyChain}. */
    protected String chainId = null;

    /** Chain stages that must have been reached before this event. */
    protected java.util.List<String> chainRequires = java.util.List.of();

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

    protected UncannyEvent family(Family value) {
        this.family = value;
        return this;
    }

    protected UncannyEvent fromInstability(double value) {
        this.minInstability = value;
        return this;
    }

    protected UncannyEvent nearMiss() {
        this.nearMiss = true;
        return this;
    }

    protected UncannyEvent advanced() {
        this.advanced = true;
        return this;
    }

    /** Marks this event as a stage of a chain; running it advances the chain. */
    protected UncannyEvent advances(String chain) {
        this.chainId = chain;
        return this;
    }

    /** Requires that an earlier stage of the chain has already happened. */
    protected UncannyEvent after(String chain, String stage) {
        this.chainId = this.chainId == null ? chain : this.chainId;
        this.chainRequires = new java.util.ArrayList<>(this.chainRequires);
        this.chainRequires.add(chain + ":" + stage);
        return this;
    }

    // ----------------------------------------------------------- evaluation

    /** Why an event may not run right now. NONE means it may. */
    public enum Rejection {
        NONE, STAGE, ALREADY_DONE, COOLDOWN, CHAIN, INSTABILITY, EXPERIENCE,
        CONDITION, PROBABILITY
    }

    /** One event's verdict for one check, with the human reason for a refusal. */
    public static final class Evaluation {
        public final Rejection rejection;
        public final String detail;

        Evaluation(Rejection rejection, String detail) {
            this.rejection = rejection;
            this.detail = detail;
        }

        public boolean isSelected() {
            return this.rejection == Rejection.NONE;
        }
    }

    /**
     * Whether this event may run right now, and if not, why.
     *
     * Order matters for cost: the cheap checks run first, and the conditions -
     * the only part that touches the world - run last. Probability is rolled
     * here, as it always has been, so an event that fails its roll reports
     * "probability failed" and stops.
     *
     * @param explain when true, the refusal carries a labeled reason (used by
     *                the debug log and /uncanny evaluate; costs one extra pass
     *                over the failed condition, so it stays off otherwise)
     */
    public Evaluation evaluate(EventContext context, RandomUtil.UncannyRandom random, boolean explain) {
        UncannyConfig config = UncannyConfig.get();
        if (context.stage() < this.minStage) {
            return new Evaluation(Rejection.STAGE, "stage too low (need " + this.minStage
                    + ", have " + context.stage() + ")");
        }
        if (this.oneTime && context.data.eventDone(this.id)) {
            return new Evaluation(Rejection.ALREADY_DONE, "already happened");
        }
        if (!this.chainRequires.isEmpty()
                && !AnomalyChain.reachedAll(context.data, this.chainRequires)) {
            return new Evaluation(Rejection.CHAIN, "chain stage not reached");
        }
        if (this.minInstability > 0
                && RealityInstability.value(context.data) < this.minInstability) {
            return new Evaluation(Rejection.INSTABILITY, "reality too stable ("
                    + String.format(java.util.Locale.ROOT, "%.2f",
                            RealityInstability.value(context.data))
                    + " < " + this.minInstability + ")");
        }
        if (this.advanced && !PlayerBehavior.advancedFamilies(context.data, context.state)) {
            return new Evaluation(Rejection.EXPERIENCE, "player has not investigated enough");
        }
        long now = context.tick;
        long cooldown;
        long last;
        if (this.nearMiss) {
            cooldown = config.nearMissCooldownTicks;
            last = context.data.lastNearMissTick;
        } else {
            cooldown = this.severity == Severity.MAJOR
                    ? config.majorCooldownTicks
                    : config.anomalyCooldownTicks;
            last = this.severity == Severity.MAJOR
                    ? context.data.lastMajorAnomalyTick
                    : context.data.lastAnomalyTick;
        }
        if (last != Long.MIN_VALUE && now - last < cooldown) {
            long remaining = (cooldown - (now - last)) / 20;
            return new Evaluation(Rejection.COOLDOWN, "cooldown (" + remaining + "s left)");
        }
        if (!this.condition.test(context)) {
            String detail = "condition failed";
            if (explain) {
                String label = this.condition.failure(context);
                if (label != null) {
                    detail = detail + ": " + label;
                }
            }
            return new Evaluation(Rejection.CONDITION, detail);
        }
        double scaled = this.probability * this.severity.weight() * config.anomalyFrequency;
        scaled *= instabilityFactor(context.data);
        if (!random.chance(Math.min(1.0, scaled))) {
            return new Evaluation(Rejection.PROBABILITY, "probability failed");
        }
        return new Evaluation(Rejection.NONE, "eligible");
    }

    /**
     * How hidden instability scales this family's chance.
     *
     * Deliberately mild, and never a straight "more scares": echoes and near
     * misses (the things that only make sense once the layers have thinned)
     * grow the most, everything else barely moves.
     */
    private double instabilityFactor(dev.uncanny.player.UncannyPlayerData data) {
        double instability = RealityInstability.value(data);
        if (this.nearMiss) {
            return 0.5 + 0.8 * instability;
        }
        if (this.family == Family.ECHO) {
            return 0.5 + 1.0 * instability;
        }
        if (this.family == Family.IDENTITY || this.family == Family.LEDGER) {
            // Gated by minInstability anyway; this just keeps them rare early.
            return 0.7 + 0.6 * instability;
        }
        return 0.9 + 0.2 * instability;
    }

    /** Whether this event may run right now. Kept for callers that ignore why. */
    public boolean canRun(EventContext context, RandomUtil.UncannyRandom random) {
        return evaluate(context, random, false).isSelected();
    }

    /** The family, for the selection bias pass. */
    public Family family() {
        return this.family;
    }

    /** True for near misses (aborted attempts with their own long cooldown). */
    public boolean isNearMiss() {
        return this.nearMiss;
    }

    /** Severity weight before behavioural and dimensional bias are applied. */
    public double baseWeight(dev.uncanny.player.UncannyPlayerData data) {
        double weight = this.severity.weight();
        if (this.severity == Severity.IMPOSSIBLE) {
            weight *= 1.0 + 0.5 * RealityInstability.value(data);
        }
        return weight;
    }

    /**
     * Runs the event and books the cooldown.
     *
     * Side effects that belong to every anomaly - anomaly locations, hidden
     * instability, chain advancement - are booked here so that a forced debug
     * run books them too.
     */
    public void run(EventContext context) {
        perform(context);
        if (this.nearMiss) {
            // An aborted attempt is not an anomaly: no counters, no record, no
            // instability. It only books its own, longer cooldown.
            context.data.lastNearMissTick = context.tick;
        } else {
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
            // The world remembers where this happened, so revisits can matter.
            context.state.recordAnomaly(new AnomalyLocation(
                    dev.uncanny.dimension.DimensionManager.current(context.player).path(),
                    context.player.getBlockPos().asLong(),
                    this.id, context.tick, this.severity.ordinal()));
            RealityInstability.raise(context.data, RealityInstability.gainFor(this.severity.ordinal()));
            if (this.chainId != null) {
                AnomalyChain.mark(context.data, this.chainId, this.id);
            }
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
