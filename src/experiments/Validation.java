package experiments;

import common.SimClock;
import stadium.Fan;
import stadium.QueueStrategy;
import stadium.StadiumConfig;
import stadium.StadiumSimulation;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Checks the simulation against queueing theory, so the results can be trusted.
 *
 * The test: many gates run at the same time, each with its own line. Fans arrive at random
 * (Poisson) times and are shared out between the gates at random, so every gate is an independent
 * line with Poisson arrivals. That is exactly the situation the textbook formulas describe. For
 * each gate we measure the average wait and compare it with the formula.
 *
 * The formula needs the real arrival rate and the real service times, not the ones we asked for,
 * because the threads sleep a little longer than requested. So the program records every fan's
 * arrival, start and finish time, measures the mean and variance of the service time that
 * actually happened, and puts those into the formula. What is left to check is the line itself:
 * first come first served, no fan lost or counted twice, and the waiting measured correctly.
 *
 * With one scanner per gate the formula is exact (Pollaczek-Khinchine). With several scanners
 * sharing a gate's line it is the Allen-Cunneen approximation, which is usually within a few
 * percent. The 95% interval comes from the spread between the gates, which are independent.
 *
 * Run it from the project folder (it takes about 8 minutes; add --quick for a 2 minute check):
 *
 *   java -cp out experiments.Validation
 */
public final class Validation {

    private static final double MEAN_SERVICE = 300;
    private static final double SPEED = 600;
    /** A result this close to the formula counts as a match even if the interval is very narrow. */
    private static final double TOLERANCE = 0.12;

    private record Case(String name, int scanners, int gates, double load) {
    }

    private record Result(double measured, double lower, double upper, double theory, double load,
                          double meanService, double cv2, int fans) {
        double difference() {
            return (measured - theory) / theory;
        }

        boolean matches() {
            return (theory >= lower && theory <= upper) || Math.abs(difference()) <= TOLERANCE;
        }
    }

    public static void main(String[] args) throws Exception {
        boolean quick = args.length > 0 && args[0].equals("--quick");
        double duration = quick ? 20_000 : 90_000;
        List<Case> cases = List.of(
                new Case("1 scanner per gate, load 0.5", 1, 100, 0.5),
                new Case("1 scanner per gate, load 0.7", 1, 100, 0.7),
                new Case("3 scanners per gate, load 0.7", 3, 40, 0.7));

        System.out.println("Validation against queueing theory");
        System.out.printf("Each case simulates %,.0f seconds of simulated time at %.0fx speed, so about %.0f real seconds.%n",
                duration, SPEED, duration / SPEED);
        System.out.println();
        System.out.printf("%-32s %6s %17s %9s %8s %6s %5s %9s%n", "Case", "Fans", "Measured wait (s)",
                "Theory", "Diff", "Load", "CV^2", "Matches");

        boolean allMatch = true;
        long seed = 311;
        for (Case c : cases) {
            Result r = simulate(c, duration, seed++);
            allMatch &= r.matches();
            System.out.printf("%-32s %,6d %8.1f (%5.1f-%5.1f) %9.1f %+7.1f%% %6.2f %5.2f %9s%n", c.name(), r.fans(),
                    r.measured(), r.lower(), r.upper(), r.theory(), r.difference() * 100, r.load(), r.cv2(),
                    r.matches() ? "yes" : "NO");
        }
        System.out.println();
        System.out.println(allMatch
                ? "The simulation agrees with queueing theory in every case."
                : "At least one case does not match. Rerun without --quick before drawing conclusions.");
        System.exit(allMatch ? 0 : 1);
    }

    private static Result simulate(Case c, double duration, long seed) throws Exception {
        double gateRate = c.scanners() * c.load() / MEAN_SERVICE;
        double totalRate = gateRate * c.gates();
        int fansBound = (int) Math.min(1_000_000, totalRate * duration * 2 + 100);

        StadiumConfig config = StadiumConfig.builder()
                .gates(c.gates()).scannersPerGate(c.scanners()).fans(fansBound)
                .serviceSeconds(MEAN_SERVICE).queueCapacity(10_000).strategy(QueueStrategy.ASSIGNED_GATE)
                .gateBias(0).walkSeconds(0).producerThreads(2).speed(SPEED).seed(seed).build();
        SimClock clock = new SimClock(SPEED);
        StadiumSimulation sim = StadiumSimulation.withOutsideFans(config, clock);

        // every fan's gate, arrival, start of service and finish
        ConcurrentLinkedQueue<double[]> records = new ConcurrentLinkedQueue<>();
        sim.setAdmissionListener((Fan fan) -> records.add(new double[] {
                fan.getServedGate(), fan.getArriveAt(), fan.getStartedAt(), fan.getAdmittedAt()}));
        sim.start();

        // Poisson arrivals: the gaps between fans are exponentially distributed
        SplittableRandom random = new SplittableRandom(seed);
        double next = -Math.log(1 - random.nextDouble()) / totalRate;
        while (next < duration) {
            while (clock.now() < next) {
                Thread.sleep(1);
            }
            sim.addExpectedFans(1);
            sim.injectFans(1);
            next += -Math.log(1 - random.nextDouble()) / totalRate;
        }
        sim.closeArrivals();
        if (!sim.awaitFinished(600_000)) {
            throw new IllegalStateException("The simulation did not finish");
        }

        // Only fans who arrived in the middle of the run count, so the start up and the draining are left out.
        double from = 0.15 * duration;
        double to = 0.9 * duration;
        List<List<Double>> waitsByGate = new ArrayList<>();
        int[] countByGate = new int[c.gates()];
        for (int g = 0; g < c.gates(); g++) {
            waitsByGate.add(new ArrayList<>());
        }
        double sumService = 0;
        double sumServiceSquared = 0;
        int fans = 0;
        for (double[] r : records) {
            if (r[1] < from || r[1] > to) {
                continue;
            }
            int gate = (int) r[0];
            double service = r[3] - r[2];
            waitsByGate.get(gate).add(r[2] - r[1]);
            countByGate[gate]++;
            sumService += service;
            sumServiceSquared += service * service;
            fans++;
        }
        double meanService = sumService / fans;
        double cv2 = (sumServiceSquared / fans) / (meanService * meanService) - 1;
        List<Double> gateMeans = new ArrayList<>();
        double arrivalRate = 0;
        for (int g = 0; g < c.gates(); g++) {
            if (waitsByGate.get(g).isEmpty()) {
                continue;
            }
            double sum = 0;
            for (double w : waitsByGate.get(g)) {
                sum += w;
            }
            gateMeans.add(sum / waitsByGate.get(g).size());
            arrivalRate += countByGate[g] / (to - from);
        }
        arrivalRate /= gateMeans.size();

        Stats stats = Stats.of(gateMeans);
        double halfWidth = 1.96 * stats.standardError();
        double theory = QueueingTheory.meanWait(c.scanners(), arrivalRate, meanService, cv2);
        return new Result(stats.mean(), stats.mean() - halfWidth, stats.mean() + halfWidth, theory,
                arrivalRate * meanService / c.scanners(), meanService, cv2, fans);
    }
}
