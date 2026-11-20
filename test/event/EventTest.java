package event;

import parking.LotStrategy;
import parking.ParkingConfig;
import parking.ParkingSnapshot;
import runner.Check;
import stadium.QueueStrategy;
import stadium.StadiumConfig;
import stadium.StadiumSnapshot;

/**
 * Tests for the whole event, where parked cars let their fans out and the fans go in through the
 * gates. The key rule is that the number of fans who get in is exactly the number of cars that
 * parked times the fans in each car.
 */
public final class EventTest {

    private static final String[] THREADS = {"scanner", "fan-producer", "attendant", "car-producer", "bridge"};

    private static ParkingConfig.Builder parking() {
        return ParkingConfig.builder().speed(3000).arrivalMinutes(5).driveSeconds(10).walkSeconds(30).payingSeconds(6);
    }

    private static StadiumConfig.Builder stadium() {
        return StadiumConfig.builder().walkSeconds(15).serviceSeconds(4);
    }

    private static void checkRules(EventSimulation event, int fansPerCar, String label) {
        ParkingSnapshot p = event.getParking().snapshot();
        StadiumSnapshot s = event.getStadium().snapshot();
        long expected = p.parked() * (long) fansPerCar;
        Check.equal(expected, s.admitted() + s.gaveUp(), label + ": fans inside plus fans who gave up");
        Check.equal(expected, s.totalFans(), label + ": fans expected");
        Check.equal(expected, s.spawned(), label + ": fans handed to the stadium");
        Check.equal(0, s.walking() + s.waiting() + s.outside() + s.scanning(), label + ": fans left in lines");
        Check.equal(0, event.getFansWalking(), label + ": fans still walking from the lots");
        Check.equal(EventSimulation.State.FINISHED, event.getState(), label + ": state");
        Check.that(event.getFinishedAt() >= event.getParking().getFinishedAt(),
                label + ": the stadium finished before the parking did");
    }

    public static void run() {
        System.out.println("Whole event");

        Check.test("fans per car is checked, and the config copies the speed and sizes the stadium", () -> {
            ParkingConfig p = parking().cars(500).speed(777).build();
            EventConfig c = EventConfig.of(p, stadium().speed(5).build(), 3);
            Check.equal(1500, c.stadium().fans(), "fans in the stadium config");
            Check.equal(777.0, c.stadium().speed(), "speed copied from the parking");
            Check.rejects("Fans per car", () -> EventConfig.of(p, stadium().build(), 0));
            Check.rejects("Fans per car", () -> EventConfig.of(p, stadium().build(), 9));
            for (EventPreset preset : EventPreset.values()) {
                Check.that(preset.config().fansPerCar() >= 1, preset + " fans per car");
            }
        });

        Check.test("every fan whose car parked gets in, for every pair of strategies", () -> {
            for (LotStrategy lot : LotStrategy.values()) {
                for (QueueStrategy gate : QueueStrategy.values()) {
                    EventConfig config = EventConfig.of(
                            parking().lots(3).attendantsPerLot(4).cars(240).spacesPerLot(70).queueCapacity(6)
                                    .strategy(lot).producerThreads(3).build(),
                            stadium().gates(4).scannersPerGate(4).queueCapacity(8).strategy(gate).producerThreads(3).build(),
                            3);
                    EventSimulation event = new EventSimulation(config);
                    event.start();
                    String label = lot + " + " + gate;
                    Check.that(event.awaitFinished(90_000), label + " did not finish");
                    checkRules(event, 3, label);
                }
            }
            Check.noThreadsLeft(3000, THREADS);
        });

        Check.test("a crowd limit at the gates makes fans give up, and every fan is still accounted for", () -> {
            EventConfig config = EventConfig.of(
                    parking().lots(2).attendantsPerLot(4).cars(150).spacesPerLot(400).queueCapacity(6).build(),
                    stadium().gates(2).scannersPerGate(1).queueCapacity(3).crowdLimit(4).serviceSeconds(10).build(), 3);
            EventSimulation event = new EventSimulation(config);
            event.start();
            Check.that(event.awaitFinished(90_000), "did not finish");
            Check.that(event.getStadium().snapshot().gaveUp() > 0, "nobody gave up, so the limit was not tested");
            checkRules(event, 3, "gate crowd limit");
        });

        Check.test("cars that are turned away bring no fans", () -> {
            EventConfig config = EventConfig.of(
                    parking().lots(1).attendantsPerLot(2).cars(100).spacesPerLot(1).queueCapacity(3).build(),
                    stadium().gates(2).scannersPerGate(1).build(), 2);
            EventSimulation event = new EventSimulation(config);
            event.start();
            Check.that(event.awaitFinished(60_000), "did not finish");
            Check.equal(1, event.getParking().snapshot().parked(), "cars parked");
            Check.equal(2, event.getStadium().snapshot().admitted(), "fans inside");
            checkRules(event, 2, "one space");
        });

        Check.test("pause, resume and a speed change keep both halves in step, and the clock stops at the end", () -> {
            EventConfig config = EventConfig.of(
                    parking().speed(300).arrivalMinutes(5).lots(2).cars(200).spacesPerLot(300).build(),
                    stadium().gates(2).scannersPerGate(2).build(), 2);
            EventSimulation event = new EventSimulation(config);
            event.start();
            try {
                Thread.sleep(400);
                event.pause();
                Thread.sleep(150);
                double t1 = event.getClock().now();
                long inside1 = event.getStadium().snapshot().admitted();
                Thread.sleep(500);
                Check.equal(t1, event.getClock().now(), "time while paused");
                Check.equal(inside1, event.getStadium().snapshot().admitted(), "fans inside while paused");
                Check.equal(EventSimulation.State.PAUSED, event.getState(), "state while paused");
                event.resume();
                event.setSpeed(900);
                Check.that(event.awaitFinished(60_000), "did not finish");
                double end = event.getClock().now();
                Thread.sleep(300);
                Check.equal(end, event.getClock().now(), "the clock must stop when the event finishes");
                checkRules(event, 2, "after pause");
            } finally {
                event.stop();
            }
        });

        Check.test("stopping in the middle ends everything and leaves no threads behind", () -> {
            EventConfig config = EventConfig.of(parking().speed(60).cars(1000).lots(2).build(),
                    stadium().gates(2).build(), 2);
            EventSimulation event = new EventSimulation(config);
            event.start();
            Thread.sleep(300);
            event.stop();
            Check.equal(EventSimulation.State.STOPPED, event.getState(), "state");
            Check.noThreadsLeft(3000, THREADS);
        });
    }
}
