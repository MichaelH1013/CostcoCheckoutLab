package experiments;

import event.EventConfig;
import event.EventPreset;
import event.EventSimulation;
import parking.LotStrategy;
import parking.ParkingConfig;
import parking.ParkingPreset;
import parking.ParkingSimulation;
import parking.ParkingSnapshot;
import stadium.QueueStrategy;
import stadium.StadiumConfig;
import stadium.StadiumPreset;
import stadium.StadiumSimulation;
import stadium.StadiumSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the experiments in the README several times each, with a different random seed every
 * time, and prints the mean and standard deviation. One run of a simulation that uses real
 * threads is not enough to compare two settings: the difference may be smaller than the
 * run-to-run noise. The standard deviation shows how large that noise is.
 *
 * Run it from the project folder. The simulations run at 120x speed, so one run of a 90 minute
 * event takes about 45 seconds and an experiment with 10 runs of 3 settings takes about 20
 * minutes. The experiments do not depend on each other, so they can run in separate terminals
 * at the same time:
 *
 *   java -cp out experiments.Replications            all three experiments, 10 runs each
 *   java -cp out experiments.Replications stadium 5  only the stadium strategies, 5 runs each
 *
 * The experiment is stadium, parking, event or crowd.
 */
public final class Replications {

    /**
     * Slow enough that a scan lasts about 60 ms of real time, much longer than the 1 to 15 ms the
     * operating system's sleep can be off by. At 600x a scan lasts 12 ms and the results depend on
     * the timer, which can change while other programs run.
     */
    private static final double SPEED = 120;

    /** The slowest Thread.sleep(1) seen before any run, in milliseconds. */
    private static double worstTimer = 0;

    public static void main(String[] args) throws Exception {
        String which = args.length > 0 ? args[0] : "all";
        int runs = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        if (!List.of("all", "stadium", "parking", "event", "crowd").contains(which) || runs < 1) {
            System.out.println("Usage: java -cp out experiments.Replications [all | stadium | parking | event | crowd] [runs]");
            System.exit(1);
        }
        System.out.printf("Each row is the mean ± standard deviation over %d runs, with a different random seed in each run.%n", runs);
        System.out.printf("Speed %.0fx. ", SPEED);
        TimerCheck.report();
        if (which.equals("all") || which.equals("stadium")) {
            stadiumStrategies(runs);
        }
        if (which.equals("all") || which.equals("parking")) {
            parkingStrategies(runs);
        }
        if (which.equals("all") || which.equals("event")) {
            eventBottleneck(runs);
        }
        if (which.equals("crowd")) {
            crowdLimit(runs);
        }
        System.out.println();
        System.out.printf("Slowest timer check before any of the runs: %.1f ms.%n", worstTimer);
        TimerCheck.report();
    }

    /** Football game: what a limit on the crowd outside a full lane does to the waits and to the fans who give up. */
    private static void crowdLimit(int runs) throws Exception {
        System.out.println();
        System.out.println("Stadium: the crowd limit (football game, 4,000 fans, 8 gates x 2 scanners, assigned gates)");
        System.out.println();
        System.out.println("| Crowd limit per gate | Fans who gave up | Average wait (min) | Longest wait (min) | Longest line |");
        System.out.println("|---|---|---|---|---|");
        for (int limit : new int[] {0, 50, 20, 10}) {
            List<Double> gaveUp = new ArrayList<>();
            List<Double> average = new ArrayList<>();
            List<Double> longest = new ArrayList<>();
            List<Double> peak = new ArrayList<>();
            for (int run = 0; run < runs; run++) {
                noteTimer();
                StadiumConfig config = StadiumPreset.FOOTBALL.config().toBuilder().speed(SPEED).crowdLimit(limit)
                        .seed(311 + run).build();
                StadiumSimulation sim = new StadiumSimulation(config);
                sim.start();
                sim.awaitFinished(600_000);
                StadiumSnapshot s = sim.snapshot();
                gaveUp.add((double) s.gaveUp());
                average.add(s.averageWaitMinutes());
                longest.add(s.longestWaitMinutes());
                peak.add((double) s.peakLine());
            }
            System.out.printf("| %s | %s | %s | %s | %s |%n", limit == 0 ? "none" : String.valueOf(limit),
                    Stats.of(gaveUp).format(0), Stats.of(average).format(1), Stats.of(longest).format(1),
                    Stats.of(peak).format(0));
        }
    }

    private static void noteTimer() {
        worstTimer = Math.max(worstTimer, TimerCheck.averageSleepMillis());
    }

    /** Football game, 4,000 fans, 8 gates of 2 scanners: how fans choose a line. */
    private static void stadiumStrategies(int runs) throws Exception {
        System.out.println();
        System.out.println("Stadium: how fans choose a gate (football game, 4,000 fans, 8 gates x 2 scanners)");
        System.out.println();
        System.out.println("| Strategy | Average wait (min) | Longest wait (min) | Longest line | Found lane full |");
        System.out.println("|---|---|---|---|---|");
        for (QueueStrategy strategy : QueueStrategy.values()) {
            List<Double> average = new ArrayList<>();
            List<Double> longest = new ArrayList<>();
            List<Double> peak = new ArrayList<>();
            List<Double> full = new ArrayList<>();
            for (int run = 0; run < runs; run++) {
                noteTimer();
                StadiumConfig config = StadiumPreset.FOOTBALL.config().toBuilder().speed(SPEED).strategy(strategy)
                        .seed(311 + run).build();
                StadiumSimulation sim = new StadiumSimulation(config);
                sim.start();
                sim.awaitFinished(600_000);
                StadiumSnapshot s = sim.snapshot();
                average.add(s.averageWaitMinutes());
                longest.add(s.longestWaitMinutes());
                peak.add((double) s.peakLine());
                full.add((double) s.foundFull());
            }
            System.out.printf("| `%s` | %s | %s | %s | %s |%n", strategy.name().toLowerCase(), Stats.of(average).format(1),
                    Stats.of(longest).format(1), Stats.of(peak).format(0), Stats.of(full).format(0));
        }
    }

    /** Football game, 1,700 cars, 6 lots of 300 spaces: how cars choose a lot. */
    private static void parkingStrategies(int runs) throws Exception {
        System.out.println();
        System.out.println("Parking: how cars choose a lot (football game, 1,700 cars, 6 lots x 300 spaces)");
        System.out.println();
        System.out.println("| Strategy | Cars parked | Turned away | Average wait (min) | Longest wait (min) | Average walk (min) |");
        System.out.println("|---|---|---|---|---|---|");
        for (LotStrategy strategy : LotStrategy.values()) {
            List<Double> parked = new ArrayList<>();
            List<Double> away = new ArrayList<>();
            List<Double> average = new ArrayList<>();
            List<Double> longest = new ArrayList<>();
            List<Double> walk = new ArrayList<>();
            for (int run = 0; run < runs; run++) {
                noteTimer();
                ParkingConfig config = ParkingPreset.FOOTBALL.config().toBuilder().speed(SPEED).strategy(strategy)
                        .seed(311 + run).build();
                ParkingSimulation sim = new ParkingSimulation(config);
                sim.start();
                sim.awaitFinished(600_000);
                ParkingSnapshot s = sim.snapshot();
                parked.add((double) s.parked());
                away.add((double) s.turnedAway());
                average.add(s.averageWaitMinutes());
                longest.add(s.longestWaitMinutes());
                double walkSum = 0;
                for (ParkingSnapshot.LotSnapshot lot : s.lots()) {
                    walkSum += lot.parked() * lot.walkSeconds();
                }
                walk.add(s.parked() == 0 ? 0 : walkSum / s.parked() / 60.0);
            }
            System.out.printf("| `%s` | %s | %s | %s | %s | %s |%n", strategy.name().toLowerCase(),
                    Stats.of(parked).format(0), Stats.of(away).format(0), Stats.of(average).format(1),
                    Stats.of(longest).format(1), Stats.of(walk).format(1));
        }
    }

    /** Whole events: which half of the event, the parking or the gates, makes the fans wait. */
    private static void eventBottleneck(int runs) throws Exception {
        System.out.println();
        System.out.println("Whole event: where do fans wait?");
        System.out.println();
        System.out.println("| Event | Gates x scanners | Parking average wait (min) | Gate average wait (min) | Gate longest wait (min) | Last fan inside (min) |");
        System.out.println("|---|---|---|---|---|---|");
        String[] names = {"Football", "Football, more gates", "Concert"};
        EventConfig football = EventPreset.FOOTBALL.config();
        EventConfig[] configs = {
                football,
                EventConfig.of(football.parking(), football.stadium().toBuilder().gates(12).build(), football.fansPerCar()),
                EventPreset.CONCERT.config()};
        for (int i = 0; i < configs.length; i++) {
            List<Double> parkWait = new ArrayList<>();
            List<Double> gateWait = new ArrayList<>();
            List<Double> gateLongest = new ArrayList<>();
            List<Double> finish = new ArrayList<>();
            for (int run = 0; run < runs; run++) {
                noteTimer();
                EventConfig base = configs[i];
                EventConfig config = EventConfig.of(base.parking().toBuilder().speed(SPEED).seed(311 + run).build(),
                        base.stadium().toBuilder().seed(311 + run).build(), base.fansPerCar());
                EventSimulation event = new EventSimulation(config);
                event.start();
                event.awaitFinished(900_000);
                parkWait.add(event.getParking().snapshot().averageWaitMinutes());
                StadiumSnapshot s = event.getStadium().snapshot();
                gateWait.add(s.averageWaitMinutes());
                gateLongest.add(s.longestWaitMinutes());
                finish.add(event.getFinishedAt() / 60.0);
            }
            StadiumConfig stadium = configs[i].stadium();
            System.out.printf("| %s | %d x %d | %s | %s | %s | %s |%n", names[i], stadium.gates(), stadium.scannersPerGate(),
                    Stats.of(parkWait).format(1), Stats.of(gateWait).format(1), Stats.of(gateLongest).format(1),
                    Stats.of(finish).format(1));
        }
    }
}
