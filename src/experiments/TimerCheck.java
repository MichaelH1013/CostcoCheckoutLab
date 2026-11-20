package experiments;

/**
 * Measures how accurate the operating system's sleep is. The simulations wait with Thread.sleep,
 * so if a one millisecond sleep really takes 15 milliseconds, every scan and every payment takes
 * longer than it should, the scanners seem slower, and the lines get longer. On Windows the
 * accuracy depends on the system timer, which any program can change, so it can be 1 ms one
 * minute and 15 ms the next. The experiments print this number at the start and at the end so a
 * reader can see which of the two they were run under.
 */
public final class TimerCheck {

    private TimerCheck() {
    }

    /** The average time in milliseconds that Thread.sleep(1) really takes. 1 to 2 is good. 15 is coarse. */
    public static double averageSleepMillis() {
        int rounds = 60;
        long start = System.nanoTime();
        try {
            for (int i = 0; i < rounds; i++) {
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return (System.nanoTime() - start) / 1e6 / rounds;
    }

    public static void report() {
        double ms = averageSleepMillis();
        String verdict = ms < 4 ? "accurate" : ms < 10 ? "somewhat coarse" : "coarse, so waits will come out too long";
        System.out.printf("Timer check: Thread.sleep(1) takes %.1f ms here (%s).%n", ms, verdict);
    }
}
