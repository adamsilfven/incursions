package dev.incursions;

import java.util.*;
import java.util.random.RandomGenerator;

/** World-independent rules. All time is logical server ticks. */
public final class Rules {
    private Rules() {}
    public static int ordinary(double roll, double zero, double one) {
        return roll < zero ? 0 : roll < zero + one ? 1 : 2;
    }
    public static double multiplier(int players) { return 1 + .35 * (Math.min(5, Math.max(1, players)) - 1); }
    public static int scaled(int count, double multiplier, RandomGenerator random) {
        if (count == 0) return 0;
        double n = count * multiplier;
        int floor = (int)Math.floor(n);
        return floor + (random.nextDouble() < n - floor ? 1 : 0);
    }
    public static <T> T weighted(List<T> entries, java.util.function.ToDoubleFunction<T> weight, RandomGenerator rng) {
        double total = entries.stream().mapToDouble(weight).sum();
        if (!(total > 0) || !Double.isFinite(total)) throw new IllegalArgumentException("pool total must be finite and positive");
        double value = rng.nextDouble() * total;
        for (T entry : entries) if ((value -= weight.applyAsDouble(entry)) < 0) return entry;
        return entries.getLast();
    }
    public record Lottery<T>(T value, double chance, boolean first) {}
    public static <T> T choose(List<Lottery<T>> eligible, RandomGenerator rng) {
        List<T> successes = new ArrayList<>();
        // A single first-priority profile is enforced by definition validation.
        for (Lottery<T> x : eligible) if (x.first && rng.nextDouble() < x.chance) return x.value;
        for (Lottery<T> x : eligible) if (!x.first && rng.nextDouble() < x.chance) successes.add(x.value);
        return successes.isEmpty() ? null : successes.get(rng.nextInt(successes.size()));
    }
    public record Schedule(int waves, int seconds, int gap, int eliteDelay, double zero, double one,
                           boolean normalElite, boolean rare, boolean rareElite, int interval) {
        public Schedule(int waves,int seconds,int gap,int eliteDelay,double zero,double one,boolean normalElite,boolean rare,boolean rareElite) {
            this(waves,seconds,gap,eliteDelay,zero,one,normalElite,rare,rareElite,20);
        }
        public Schedule {
            if(interval<20 || interval>seconds*20 || (seconds*20)%interval!=0) throw new IllegalArgumentException("interval must divide the spawning window and be at least 20 ticks");
        }
    }
    public enum Kind { ORDINARY, ENHANCED, RARE, RARE_ENHANCED }
    public record Arrival(long due, long deadline, Kind kind, boolean zeroPair) {
        public Arrival(long due,long deadline,Kind kind) {this(due,deadline,kind,false);}
    }
    public interface World {
        /** False means placement failed; enhanced/rare arrivals retry until their deadline. */
        boolean spawn(Kind kind);
        default boolean spawn(Arrival arrival) {return spawn(arrival.kind);}
        default void waveStarted(int wave,int total) {}
        int requiredAlive();
        double scaling();
        void completed();
        void failed(String reason);
    }
    public static final class Fight {
        private final Schedule s;
        private final RandomGenerator rng;
        private final List<Arrival> pending = new ArrayList<>();
        public int wave;
        public long waveStart, nextWave = -1;
        public boolean ended;
        private boolean windowClosed;
        public Fight(Schedule schedule, RandomGenerator random, long now, World world) {
            s = schedule; rng = random; begin(now, world);
        }
        private void begin(long now, World world) {
            wave++; world.waveStarted(wave,s.waves); waveStart = now; nextWave = -1; windowClosed = false;
            double multiplier = world.scaling();
            int totalRolled = 0;
            for (int offset = 0; offset < s.seconds*20; offset+=s.interval) {
                int roll = ordinary(rng.nextDouble(), s.zero, s.one);
                totalRolled += roll;
                for (int i = 0, n = scaled(roll, multiplier, rng); i < n; i++)
                    pending.add(new Arrival(now + offset, now + offset, Kind.ORDINARY));
            }
            if (totalRolled == 0) {
                for (int i = 0; i < 2; i++) pending.add(delayed(now, Kind.ENHANCED,true));
            } else if (wave == s.waves && s.normalElite && !s.rareElite) pending.add(delayed(now, Kind.ENHANCED));
            // One reserved Rotbeast is additional, never consumes or changes ordinary zero rolls.
            if (s.rare && ((s.rareElite && wave == s.waves) || (!s.rareElite && wave == 1)))
                pending.add(s.rareElite ? delayed(now, Kind.RARE_ENHANCED)
                        : new Arrival(now + s.seconds*20L-s.interval, now + s.seconds*20L-s.interval + 600, Kind.RARE));
        }
        private Arrival delayed(long now, Kind kind) {return delayed(now,kind,false);}
        private Arrival delayed(long now,Kind kind,boolean zeroPair) {return new Arrival(now+s.eliteDelay,now+s.eliteDelay+600,kind,zeroPair);}
        public int pendingCount() { return pending.size(); }
        public List<Arrival> arrivals() { return List.copyOf(pending); }
        public void tick(long now, World world) {
            if (ended) return;
            if (nextWave >= 0) { if (now >= nextWave) begin(now, world); else return; }
            var it = pending.iterator();
            while (it.hasNext()) {
                Arrival a = it.next();
                if (now < a.due) continue;
                if (world.spawn(a)) it.remove();
                else if (a.kind == Kind.ORDINARY) it.remove();
                else if (now >= a.deadline) { fail(world, "required_arrival_obstructed"); return; }
            }
            windowClosed = now >= waveStart + s.seconds * 20L;
            if (windowClosed && pending.isEmpty() && world.requiredAlive() == 0) {
                if (wave == s.waves) { ended = true; world.completed(); }
                else nextWave = now + s.gap;
            }
        }
        public void fail(World world, String reason) { if (!ended) { ended = true; pending.clear(); world.failed(reason); } }
    }
}
