package dev.uncanny.util;

import java.util.List;

/**
 * A tiny deterministic random number generator (xorshift64*).
 *
 * java.util.Random would work too, but this class makes the intent obvious:
 * the sequence depends ONLY on the seed it was given, on every JVM, forever.
 * If you want the same corridor twice, construct two UncannyRandoms with the
 * same seed.
 */
public final class RandomUtil {

    /** An xorshift random stream. Create one per generated thing, never share one. */
    public static final class UncannyRandom {
        private long state;

        public UncannyRandom(long seed) {
            // A zero state would freeze xorshift forever, so nudge it.
            this.state = seed == 0L ? 0x2545F4914F6CDD1DL : seed;
        }

        public long nextLong() {
            long x = this.state;
            x ^= x >>> 12;
            x ^= x << 25;
            x ^= x >>> 27;
            this.state = x;
            return x * 0x2545F4914F6CDD1DL;
        }

        public int nextInt() {
            return (int) (nextLong() >>> 32);
        }

        /** 0 (inclusive) to bound (exclusive). */
        public int nextInt(int bound) {
            if (bound <= 0) {
                return 0;
            }
            return Math.floorMod(nextInt(), bound);
        }

        /** min (inclusive) to max (inclusive). */
        public int between(int min, int max) {
            if (max <= min) {
                return min;
            }
            return min + nextInt(max - min + 1);
        }

        /** 0.0 (inclusive) to 1.0 (exclusive). */
        public double nextDouble() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }

        public boolean chance(double probability) {
            return nextDouble() < probability;
        }

        public boolean nextBoolean() {
            return (nextLong() & 1L) == 0L;
        }

        public <T> T pick(List<T> list) {
            if (list.isEmpty()) {
                return null;
            }
            return list.get(nextInt(list.size()));
        }
    }

    private RandomUtil() {
    }

    /** Creates a stream for a seed. */
    public static UncannyRandom of(long seed) {
        return new UncannyRandom(seed);
    }
}
