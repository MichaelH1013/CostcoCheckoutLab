package common;

/**
 * The simulation clock. Time is counted in simulated seconds, and speed is how many simulated
 * seconds pass for each real second. The clock can be paused and its speed changed while the
 * simulation runs. Reads are lock-free, so thousands of threads can ask for the time.
 */
public final class SimClock {

    private record Segment(double baseSeconds, long realStartNanos, double speed, boolean running) {
    }

    private volatile Segment segment;

    public SimClock(double speed) {
        segment = new Segment(0, System.nanoTime(), speed, false);
    }

    public double now() {
        Segment s = segment;
        if (!s.running()) {
            return s.baseSeconds();
        }
        return s.baseSeconds() + (System.nanoTime() - s.realStartNanos()) / 1e9 * s.speed();
    }

    public synchronized void resume() {
        Segment s = segment;
        if (!s.running()) {
            segment = new Segment(s.baseSeconds(), System.nanoTime(), s.speed(), true);
        }
    }

    public synchronized void pause() {
        Segment s = segment;
        if (s.running()) {
            segment = new Segment(now(), System.nanoTime(), s.speed(), false);
        }
    }

    public synchronized void setSpeed(double speed) {
        Segment s = segment;
        segment = new Segment(now(), System.nanoTime(), speed, s.running());
    }

    public double getSpeed() {
        return segment.speed();
    }

    public boolean isRunning() {
        return segment.running();
    }

    /**
     * Sleeps until the given number of simulated seconds have passed. A running clock sleeps in
     * slices of at most 100 ms, so a speed change takes effect within a tenth of a second. A
     * paused clock is checked every 20 ms. The slices are long on purpose: on a computer whose
     * operating system timer is coarse (about 15 ms on Windows) every sleep is rounded up to the
     * next tick, so many short sleeps would make a wait several times too long.
     */
    public void sleepSim(double simSeconds) throws InterruptedException {
        double target = now() + simSeconds;
        while (true) {
            double remaining = target - now();
            if (remaining <= 0) {
                return;
            }
            Segment s = segment;
            long ms = 20;
            if (s.running()) {
                ms = Math.max(1, Math.min(100, (long) Math.ceil(remaining / s.speed() * 1000)));
            }
            Thread.sleep(ms);
        }
    }
}
