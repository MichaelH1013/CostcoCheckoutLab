package parking;

import common.ArrivalShape;
import runner.Check;

/**
 * Tests for the parking simulation. The most important rule is that a lot never has more cars
 * than spaces, however many attendants work at once, and that a lot only turns cars away when it
 * is really full.
 */
public final class ParkingTest {

    private static final String[] THREADS = {"attendant", "car-producer"};

    private static ParkingConfig.Builder quick() {
        return ParkingConfig.builder().speed(3000).arrivalMinutes(5).lots(3).attendantsPerLot(3).cars(600)
                .spacesPerLot(200).queueCapacity(6).driveSeconds(10).walkSeconds(30).payingSeconds(4);
    }

    private static void checkRules(ParkingSnapshot s, int cars, String label) {
        Check.equal(cars, s.parked() + s.turnedAway() + s.gaveUp(), label + ": cars parked plus turned away plus drove on");
        Check.equal(cars, s.spawned(), label + ": cars created");
        Check.equal(0, s.driving() + s.waiting() + s.outside() + s.paying(), label + ": cars left in lines");
        for (ParkingSnapshot.LotSnapshot lot : s.lots()) {
            Check.that(lot.parked() <= lot.spaces(), label + ": " + lot.name() + " has more cars than spaces");
            Check.equal(lot.spaces() - lot.parked(), lot.free(), label + ": " + lot.name() + " free spaces");
            Check.that(lot.turnedAway() == 0 || lot.parked() == lot.spaces(),
                    label + ": " + lot.name() + " turned cars away without being full");
        }
    }

    public static void run() {
        System.out.println("Parking");

        Check.test("settings are checked, and bad ones say what is wrong", () -> {
            Check.rejects("Lots", () -> ParkingConfig.builder().lots(0).build());
            Check.rejects("Attendants", () -> ParkingConfig.builder().attendantsPerLot(0).build());
            Check.rejects("Cars", () -> ParkingConfig.builder().cars(0).build());
            Check.rejects("Spaces", () -> ParkingConfig.builder().spacesPerLot(0).build());
            Check.rejects("Queue capacity", () -> ParkingConfig.builder().queueCapacity(0).build());
            Check.rejects("lot layout", () -> ParkingConfig.builder().layout(null).build());
        });

        Check.test("walk time grows from the nearest lot to the farthest, which is three times as far", () -> {
            ParkingConfig config = ParkingConfig.builder().lots(5).walkSeconds(100).build();
            Check.equal(100.0, config.walkSecondsFor(0), "nearest lot");
            Check.equal(300.0, config.walkSecondsFor(4), "farthest lot");
            Check.that(config.walkSecondsFor(1) < config.walkSecondsFor(2), "walk time is not increasing");
            Check.equal(100.0, ParkingConfig.builder().lots(1).walkSeconds(100).build().walkSecondsFor(0), "one lot");
        });

        Check.test("every car is parked or turned away exactly once, for every strategy and producer count", () -> {
            for (LotStrategy strategy : LotStrategy.values()) {
                for (int producers : new int[] {1, 4}) {
                    ParkingConfig config = quick().cars(600).spacesPerLot(250).strategy(strategy)
                            .producerThreads(producers).build();
                    ParkingSimulation sim = new ParkingSimulation(config);
                    sim.start();
                    String label = strategy + " with " + producers + " producer(s)";
                    Check.that(sim.awaitFinished(60_000), label + " did not finish");
                    checkRules(sim.snapshot(), 600, label);
                    Check.equal(0, sim.snapshot().gaveUp(), label + ": cars that drove on with no street limit");
                    Check.equal(ParkingSimulation.State.FINISHED, sim.getState(), label + ": state");
                }
            }
            Check.noThreadsLeft(3000, THREADS);
        });

        Check.test("a lot with one space and three busy attendants never sells it twice", () -> {
            ParkingSimulation sim = new ParkingSimulation(quick().lots(1).attendantsPerLot(3).cars(300)
                    .spacesPerLot(1).queueCapacity(2).payingSeconds(3).build());
            sim.start();
            Check.that(sim.awaitFinished(60_000), "did not finish");
            ParkingSnapshot s = sim.snapshot();
            Check.equal(1, s.parked(), "cars parked");
            Check.equal(299, s.turnedAway(), "cars turned away");
            checkRules(s, 300, "one space");
        });

        Check.test("a lot with room for everyone turns nobody away", () -> {
            ParkingSimulation sim = new ParkingSimulation(quick().lots(2).cars(200).spacesPerLot(1000).build());
            sim.start();
            Check.that(sim.awaitFinished(60_000), "did not finish");
            Check.equal(200, sim.snapshot().parked(), "cars parked");
            Check.equal(0, sim.snapshot().turnedAway(), "cars turned away");
        });

        Check.test("when the total room is enough, choosing the lot with free spaces turns nobody away", () -> {
            ParkingConfig base = quick().lots(4).cars(600).spacesPerLot(180).lotBias(2.0)
                    .shape(ArrivalShape.UNIFORM).arrivalMinutes(10).build();
            ParkingSimulation careful = new ParkingSimulation(base.toBuilder().strategy(LotStrategy.MOST_FREE_SPACES).build());
            careful.start();
            Check.that(careful.awaitFinished(60_000), "did not finish");
            Check.equal(0, careful.snapshot().turnedAway(), "cars turned away with most_free_spaces");
            ParkingSimulation careless = new ParkingSimulation(base.toBuilder().strategy(LotStrategy.ASSIGNED_LOT).build());
            careless.start();
            Check.that(careless.awaitFinished(60_000), "did not finish");
            Check.that(careless.snapshot().turnedAway() > 0, "the nearest lot should have filled up with assigned_lot");
        });

        Check.test("with a street limit, cars that find the lane and the street full drive on, and every car is accounted for", () -> {
            ParkingSimulation sim = new ParkingSimulation(quick().lots(1).attendantsPerLot(1).queueCapacity(3).crowdLimit(5)
                    .cars(300).arrivalMinutes(2).spacesPerLot(1000).payingSeconds(8).build());
            sim.start();
            Check.that(sim.awaitFinished(90_000), "did not finish");
            ParkingSnapshot s = sim.snapshot();
            Check.that(s.gaveUp() > 0, "nobody drove on, so the limit was not tested");
            Check.equal(300, s.parked() + s.turnedAway() + s.gaveUp(), "cars parked plus turned away plus drove on");
            Check.that(s.peakLine() <= 3 + 5, "the line grew past its lane plus its street limit: " + s.peakLine());
            checkRules(s, 300, "street limit");
        });

        Check.test("a car that parks is handed to the listener once", () -> {
            ParkingSimulation sim = new ParkingSimulation(quick().cars(200).spacesPerLot(300).build());
            java.util.concurrent.atomic.AtomicInteger heard = new java.util.concurrent.atomic.AtomicInteger();
            sim.setParkedListener(car -> {
                Check.that(car.getState() == Car.State.PARKED, "listener saw a car that is not parked");
                heard.incrementAndGet();
            });
            sim.start();
            Check.that(sim.awaitFinished(60_000), "did not finish");
            Check.equal(sim.snapshot().parked(), heard.get(), "cars reported to the listener");
        });

        Check.test("pausing freezes the simulated time and stopping leaves no threads behind", () -> {
            ParkingSimulation sim = new ParkingSimulation(quick().speed(300).arrivalMinutes(3).cars(300).build());
            sim.start();
            try {
                Thread.sleep(300);
                sim.pause();
                Thread.sleep(150);
                double t1 = sim.snapshot().simSeconds();
                Thread.sleep(500);
                Check.equal(t1, sim.snapshot().simSeconds(), "time while paused");
                sim.resume();
                Check.that(sim.awaitFinished(60_000), "did not finish after resume");
            } finally {
                sim.stop();
            }

            ParkingSimulation other = new ParkingSimulation(quick().speed(60).cars(1000).build());
            other.start();
            Thread.sleep(300);
            other.stop();
            Check.equal(ParkingSimulation.State.STOPPED, other.getState(), "state after stop");
            Check.noThreadsLeft(3000, THREADS);
        });
    }
}
