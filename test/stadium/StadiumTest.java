package stadium;

import common.ArrivalShape;
import runner.Check;

/**
 * Tests for the stadium entry simulation: the settings, and the rules a correct run must follow
 * whatever the timing. None of them depend on exact waiting times.
 */
public final class StadiumTest {

    private static final String[] THREADS = {"scanner", "fan-producer"};

    private static StadiumConfig.Builder quick() {
        return StadiumConfig.builder().speed(3000).arrivalMinutes(5).gates(4).scannersPerGate(3).fans(600)
                .queueCapacity(6).walkSeconds(20).serviceSeconds(4);
    }

    public static void run() {
        System.out.println("Stadium");

        Check.test("settings are checked, and bad ones say what is wrong", () -> {
            Check.rejects("Gates", () -> StadiumConfig.builder().gates(0).build());
            Check.rejects("Gates", () -> StadiumConfig.builder().gates(201).build());
            Check.rejects("Scanners per gate", () -> StadiumConfig.builder().scannersPerGate(0).build());
            Check.rejects("at most", () -> StadiumConfig.builder().gates(200).scannersPerGate(11).build());
            Check.rejects("Fans", () -> StadiumConfig.builder().fans(0).build());
            Check.rejects("Queue capacity", () -> StadiumConfig.builder().queueCapacity(0).build());
            Check.rejects("Speed", () -> StadiumConfig.builder().speed(0).build());
            Check.rejects("Service time", () -> StadiumConfig.builder().serviceSeconds(-1).build());
        });

        Check.test("toBuilder copies every setting and every preset is valid", () -> {
            StadiumConfig a = StadiumConfig.builder().gates(7).scannersPerGate(3).fans(1234).queueCapacity(9)
                    .strategy(QueueStrategy.SHORTEST_LINE).shape(ArrivalShape.LATE_RUSH).seed(5).build();
            Check.equal(a, a.toBuilder().build(), "copy of the config");
            for (StadiumPreset preset : StadiumPreset.values()) {
                Check.that(preset.config().totalScanners() > 0, preset + " has scanners");
            }
        });

        Check.test("every fan is admitted exactly once, for every strategy and producer count", () -> {
            for (QueueStrategy strategy : QueueStrategy.values()) {
                for (int producers : new int[] {1, 4}) {
                    StadiumConfig config = quick().strategy(strategy).producerThreads(producers).build();
                    StadiumSimulation sim = new StadiumSimulation(config);
                    sim.start();
                    String label = strategy + " with " + producers + " producer(s)";
                    Check.that(sim.awaitFinished(60_000), label + " did not finish");
                    StadiumSnapshot s = sim.snapshot();
                    Check.equal(600, s.admitted(), label + ": fans admitted");
                    Check.equal(0, s.gaveUp(), label + ": fans who gave up with no crowd limit");
                    Check.equal(600, s.spawned(), label + ": fans created");
                    long perGate = 0;
                    for (StadiumSnapshot.GateSnapshot gate : s.gates()) {
                        perGate += gate.admitted();
                    }
                    Check.equal(600, perGate, label + ": fans admitted, added up per gate");
                    Check.equal(0, s.walking() + s.waiting() + s.outside() + s.scanning(), label + ": fans left in lines");
                    Check.equal(StadiumSimulation.State.FINISHED, sim.getState(), label + ": state");
                    Check.that(s.averageWaitMinutes() >= 0, label + ": average wait");
                    Check.that(s.longestWaitMinutes() >= s.averageWaitMinutes(), label + ": longest wait is at least the average");
                }
            }
            Check.noThreadsLeft(3000, THREADS);
        });

        Check.test("a lane of size 1 and a heavy load still admits everyone", () -> {
            StadiumSimulation sim = new StadiumSimulation(quick().gates(2).scannersPerGate(2).queueCapacity(1)
                    .fans(400).serviceSeconds(3).build());
            sim.start();
            Check.that(sim.awaitFinished(60_000), "did not finish");
            Check.equal(400, sim.snapshot().admitted(), "fans admitted");
            Check.that(sim.snapshot().foundFull() > 0, "the lane never filled, so the overflow list was not used");
        });

        Check.test("with a crowd limit, fans who find the lane and the crowd full give up, and every fan is accounted for", () -> {
            for (QueueStrategy strategy : new QueueStrategy[] {QueueStrategy.ASSIGNED_GATE, QueueStrategy.SHARED_QUEUE}) {
                StadiumConfig config = quick().gates(2).scannersPerGate(1).queueCapacity(3).crowdLimit(5).fans(500)
                        .arrivalMinutes(2).serviceSeconds(8).strategy(strategy).build();
                StadiumSimulation sim = new StadiumSimulation(config);
                sim.start();
                Check.that(sim.awaitFinished(90_000), strategy + " did not finish");
                StadiumSnapshot s = sim.snapshot();
                Check.that(s.gaveUp() > 0, strategy + ": nobody gave up, so the limit was not tested");
                Check.equal(500, s.admitted() + s.gaveUp(), strategy + ": fans admitted plus fans who gave up");
                Check.equal(500, s.spawned(), strategy + ": fans created");
                int lineLimit = strategy == QueueStrategy.SHARED_QUEUE ? (3 + 5) * 2 : 3 + 5;
                Check.that(s.peakLine() <= lineLimit, strategy + ": a line grew past its lane plus its crowd limit: " + s.peakLine());
                Check.equal(0, s.walking() + s.waiting() + s.outside() + s.scanning(), strategy + ": fans left in lines");
            }
        });

        Check.test("one fan, one gate, one scanner works", () -> {
            StadiumSimulation sim = new StadiumSimulation(quick().gates(1).scannersPerGate(1).fans(1).build());
            sim.start();
            Check.that(sim.awaitFinished(30_000), "did not finish");
            Check.equal(1, sim.snapshot().admitted(), "fans admitted");
        });

        Check.test("with a gate bias the first gate draws about twice as many fans as the last", () -> {
            StadiumSimulation sim = new StadiumSimulation(quick().gates(5).scannersPerGate(20).fans(3000)
                    .gateBias(1.0).strategy(QueueStrategy.ASSIGNED_GATE).serviceSeconds(1).build());
            sim.start();
            Check.that(sim.awaitFinished(90_000), "did not finish");
            long first = sim.snapshot().gates().get(0).admitted();
            long last = sim.snapshot().gates().get(4).admitted();
            double ratio = (double) first / last;
            Check.near(2.0, ratio, 0.5, "first gate to last gate");
        });

        Check.test("pausing freezes the simulated time and the run still finishes", () -> {
            StadiumSimulation sim = new StadiumSimulation(quick().speed(300).arrivalMinutes(3).fans(300).build());
            sim.start();
            try {
                Thread.sleep(300);
                sim.pause();
                Thread.sleep(150);
                double t1 = sim.snapshot().simSeconds();
                long admitted1 = sim.snapshot().admitted();
                Thread.sleep(500);
                Check.equal(t1, sim.snapshot().simSeconds(), "time while paused");
                Check.equal(admitted1, sim.snapshot().admitted(), "fans admitted while paused");
                Check.equal(StadiumSimulation.State.PAUSED, sim.getState(), "state while paused");
                sim.resume();
                Check.that(sim.awaitFinished(60_000), "did not finish after resume");
            } finally {
                sim.stop();
            }
        });

        Check.test("stopping in the middle ends the run and leaves no threads behind", () -> {
            StadiumSimulation sim = new StadiumSimulation(quick().speed(60).fans(1000).build());
            sim.start();
            Thread.sleep(300);
            sim.stop();
            Check.equal(StadiumSimulation.State.STOPPED, sim.getState(), "state");
            Check.noThreadsLeft(3000, THREADS);
        });

        Check.test("fans handed over from outside are admitted, and only a stadium built for it accepts them", () -> {
            StadiumConfig config = quick().fans(1000).build();
            Check.rejects("creates its own fans", () -> {
                try {
                    new StadiumSimulation(config).injectFans(1);
                } catch (IllegalStateException e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
            });
            common.SimClock clock = new common.SimClock(config.speed());
            StadiumSimulation sim = StadiumSimulation.withOutsideFans(config, clock);
            sim.start();
            for (int i = 0; i < 10; i++) {
                sim.addExpectedFans(30);
                sim.injectFans(30);
                Thread.sleep(5);
            }
            sim.closeArrivals();
            Check.that(sim.awaitFinished(60_000), "did not finish");
            Check.equal(300, sim.snapshot().admitted(), "fans admitted");
            Check.equal(300, sim.snapshot().totalFans(), "fans expected");
            Check.that(clock.isRunning(), "a stadium that shares a clock must not pause it");
            Check.noThreadsLeft(3000, THREADS);
        });
    }
}
