package common;

import runner.Check;

/**
 * Tests for the simulation clock and the arrival patterns. Timing checks use wide tolerances,
 * because the tests share a computer with other programs.
 */
public final class SimClockTest {

    public static void run() {
        System.out.println("SimClock and ArrivalShape");

        Check.test("a new clock is stopped at zero", () -> {
            SimClock clock = new SimClock(100);
            Thread.sleep(100);
            Check.equal(0.0, clock.now(), "time before resume");
            Check.that(!clock.isRunning(), "running");
        });

        Check.test("a running clock advances by speed times real time", () -> {
            SimClock clock = new SimClock(200);
            clock.resume();
            Thread.sleep(500);
            double now = clock.now();
            Check.near(100.0, now, 40.0, "simulated seconds after 0.5 real seconds at 200x");
        });

        Check.test("pausing freezes time and resuming continues from the same moment", () -> {
            SimClock clock = new SimClock(500);
            clock.resume();
            Thread.sleep(200);
            clock.pause();
            double frozen = clock.now();
            Thread.sleep(300);
            Check.equal(frozen, clock.now(), "time while paused");
            clock.resume();
            Thread.sleep(200);
            Check.that(clock.now() > frozen + 20, "time did not continue after resume");
        });

        Check.test("changing the speed changes the rate without a jump in the time", () -> {
            SimClock clock = new SimClock(100);
            clock.resume();
            Thread.sleep(300);
            double before = clock.now();
            clock.setSpeed(1000);
            double after = clock.now();
            Check.near(before, after, 5.0, "time right after the speed change");
            Thread.sleep(300);
            Check.that(clock.now() - after > 150, "the new speed was not used");
        });

        Check.test("sleepSim waits for the simulated time, and a pause makes it wait longer", () -> {
            SimClock clock = new SimClock(1000);
            clock.resume();
            double start = clock.now();
            clock.sleepSim(300);
            Check.near(300.0, clock.now() - start, 120.0, "simulated time slept");

            Thread pauser = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    clock.pause();
                    Thread.sleep(400);
                    clock.resume();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            long realStart = System.nanoTime();
            pauser.start();
            clock.sleepSim(200);
            double realSeconds = (System.nanoTime() - realStart) / 1e9;
            Check.that(realSeconds >= 0.4, "sleepSim finished during the pause after only " + realSeconds + " s");
            pauser.join();
        });

        Check.test("arrival patterns start at 0, end at 1, never go backwards and clamp outside 0 to 1", () -> {
            for (ArrivalShape shape : ArrivalShape.values()) {
                Check.equal(0.0, shape.fractionArrived(0), shape + " at 0");
                Check.equal(1.0, shape.fractionArrived(1), shape + " at 1");
                Check.equal(0.0, shape.fractionArrived(-3), shape + " below 0");
                Check.equal(1.0, shape.fractionArrived(7), shape + " above 1");
                double previous = 0;
                for (int i = 1; i <= 200; i++) {
                    double value = shape.fractionArrived(i / 200.0);
                    Check.that(value >= previous - 1e-12, shape + " went backwards at " + i / 200.0);
                    previous = value;
                }
            }
        });

        Check.test("the surge pattern is busiest about two thirds of the way through", () -> {
            double busiestAt = 0;
            double busiest = 0;
            for (int i = 0; i < 1000; i++) {
                double rate = ArrivalShape.SURGE.fractionArrived((i + 1) / 1000.0) - ArrivalShape.SURGE.fractionArrived(i / 1000.0);
                if (rate > busiest) {
                    busiest = rate;
                    busiestAt = i / 1000.0;
                }
            }
            Check.near(2.0 / 3.0, busiestAt, 0.02, "peak of the surge");
        });
    }
}
