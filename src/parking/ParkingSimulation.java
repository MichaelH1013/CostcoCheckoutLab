package parking;

import common.SimClock;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The parking lot simulation for the stadium: cars arrive, line up at a lot entrance, pay an
 * attendant and take one of the lot's spaces. A full lot turns cars away.
 *
 * It uses the same thread design as StadiumSimulation, so 20 cars or 50,000 cars use the same
 * threads and only the parameters change:
 *   producers  - a small ScheduledThreadPoolExecutor. Each producer wakes up every few
 *                milliseconds, creates the cars that are due (a car is an object, not a thread)
 *                and moves cars that finished driving into the entrance line.
 *   attendants - a fixed thread pool with one thread per attendant (lots x attendantsPerLot).
 *                Each attendant blocks on its line's BlockingQueue, like the Costco cashiers.
 */
public final class ParkingSimulation {

    public enum State { NEW, RUNNING, PAUSED, FINISHED, STOPPED }

    private final ParkingConfig config;
    private final SimClock clock;
    private final List<Lot> lots = new ArrayList<>();
    private final List<CarLine> lines = new ArrayList<>();
    private final double[] cumulativeWeights;
    private final RecentCars recentCars = new RecentCars();
    private final ScheduledThreadPoolExecutor producerPool;
    private final ExecutorService attendantPool;

    private final AtomicInteger spawned = new AtomicInteger();
    private final AtomicInteger producersFinished = new AtomicInteger();
    private final AtomicInteger attendantsRunning;
    private final CountDownLatch finished = new CountDownLatch(1);

    private final boolean ownsClock;
    private volatile Consumer<Car> parkedListener;

    private volatile State state = State.NEW;
    private volatile double finishedAt = Double.NaN;

    public ParkingSimulation(ParkingConfig config) {
        this(config, new SimClock(config.speed()), true);
    }

    /**
     * A parking simulation that runs on a clock shared with another simulation, for example the
     * stadium (see event.EventSimulation). It does not pause the shared clock when it finishes or
     * is stopped, because the other simulation is still using it.
     */
    public static ParkingSimulation withSharedClock(ParkingConfig config, SimClock sharedClock) {
        return new ParkingSimulation(config, sharedClock, false);
    }

    private ParkingSimulation(ParkingConfig config, SimClock clock, boolean ownsClock) {
        this.config = config;
        this.clock = clock;
        this.ownsClock = ownsClock;
        this.attendantsRunning = new AtomicInteger(config.totalAttendants());

        for (int g = 0; g < config.lots(); g++) {
            String name = "Lot " + (char) ('A' + g % 26) + (g >= 26 ? String.valueOf(g / 26) : "");
            CarLine line = new CarLine(name, config.queueCapacity(), config.crowdLimit());
            lines.add(line);
            lots.add(new Lot(g, name, config.spacesPerLot(), config.walkSecondsFor(g), line,
                    config.attendantsPerLot(), clock, recentCars, this::carParked, this::attendantFinished));
        }

        cumulativeWeights = new double[config.lots()];
        double total = 0;
        for (int g = 0; g < config.lots(); g++) {
            double fromEnd = config.lots() == 1 ? 0 : (double) (config.lots() - 1 - g) / (config.lots() - 1);
            total += 1 + config.lotBias() * fromEnd;
            cumulativeWeights[g] = total;
        }

        producerPool = new ScheduledThreadPoolExecutor(config.producerThreads(), runnable -> {
            Thread thread = new Thread(runnable, "car-producer");
            thread.setDaemon(true);
            return thread;
        });
        attendantPool = Executors.newFixedThreadPool(config.totalAttendants(), runnable -> {
            Thread thread = new Thread(runnable, "attendant");
            thread.setDaemon(true);
            return thread;
        });
    }

    public ParkingConfig getConfig() {
        return config;
    }

    public SimClock getClock() {
        return clock;
    }

    public List<Lot> getLots() {
        return lots;
    }

    public State getState() {
        return state;
    }

    /** The cars that were parked or turned away most recently, so the GUI can animate them. */
    public RecentCars getRecentCars() {
        return recentCars;
    }

    public synchronized void start() {
        if (state != State.NEW) {
            throw new IllegalStateException("Already started.");
        }
        state = State.RUNNING;
        for (Lot lot : lots) {
            for (Attendant attendant : lot.getAttendants()) {
                attendantPool.execute(attendant);
            }
        }
        for (int p = 0; p < config.producerThreads(); p++) {
            producerPool.schedule(new CarProducer(p), 0, TimeUnit.MILLISECONDS);
        }
        clock.resume();
    }

    public synchronized void pause() {
        if (state == State.RUNNING) {
            clock.pause();
            state = State.PAUSED;
        }
    }

    public synchronized void resume() {
        if (state == State.PAUSED) {
            clock.resume();
            state = State.RUNNING;
        }
    }

    public void setSpeed(double speed) {
        clock.setSpeed(speed);
    }

    /** Ends the simulation early. The attendants are interrupted, so cars still in line are not handled. */
    public synchronized void stop() {
        if (state == State.FINISHED || state == State.STOPPED) {
            return;
        }
        state = State.STOPPED;
        if (ownsClock) {
            clock.pause();
        }
        producerPool.shutdownNow();
        attendantPool.shutdownNow();
        finished.countDown();
    }

    /** Called from an attendant thread each time a car parks. Used to hand the fans on to the stadium. */
    public void setParkedListener(Consumer<Car> listener) {
        parkedListener = listener;
    }

    private void carParked(Car car) {
        Consumer<Car> listener = parkedListener;
        if (listener != null) {
            listener.accept(car);
        }
    }

    public boolean awaitFinished(long millis) throws InterruptedException {
        return finished.await(millis, TimeUnit.MILLISECONDS);
    }

    public boolean isFinished() {
        return state == State.FINISHED;
    }

    /** Simulated seconds until the last car was handled, or NaN while the simulation is not finished. */
    public double getFinishedAt() {
        return finishedAt;
    }

    private void attendantFinished() {
        if (attendantsRunning.decrementAndGet() > 0) {
            return;
        }
        synchronized (this) {
            if (state == State.STOPPED) {
                return;
            }
            finishedAt = clock.now();
            if (ownsClock) {
                clock.pause();
            }
            state = State.FINISHED;
        }
        producerPool.shutdown();
        attendantPool.shutdown();
        finished.countDown();
    }

    /** Every producer is done, so every car is in a line. Now tell the attendants to go home. */
    private void stopAttendants() {
        for (CarLine line : lines) {
            line.closeFor(config.attendantsPerLot());
        }
    }

    public ParkingSnapshot snapshot() {
        double now = Double.isNaN(finishedAt) ? clock.now() : finishedAt;
        double minutes = now / 60.0;

        List<ParkingSnapshot.LotSnapshot> lotSnapshots = new ArrayList<>();
        int driving = 0;
        int waiting = 0;
        int outside = 0;
        int paying = 0;
        int peak = 0;
        long foundFull = 0;
        long gaveUp = 0;
        long parked = 0;
        long turnedAway = 0;
        long waitMs = 0;
        long processed = 0;
        double longest = 0;
        for (Lot lot : lots) {
            CarLine line = lot.getLine();
            int busy = lot.busyAttendants();
            int lotParked = (int) lot.getParked();
            double filledAt = lot.getFilledAt();
            ParkingSnapshot.LotSnapshot ls = new ParkingSnapshot.LotSnapshot(lot.getId(), lot.getName(),
                    lot.getSpaces(), lotParked, lot.getFreeSpaces(), lot.getAttendants().size(), busy,
                    lot.getTurnedAway(), line.laneSize(), line.outsideSize(), line.drivingSize(),
                    line.getPeakLength(), line.getFoundFull(), line.getGaveUp(), lot.getAverageWaitMinutes(),
                    lot.getLongestWaitMinutes(), lot.getWalkSeconds(),
                    Double.isNaN(filledAt) ? Double.NaN : filledAt / 60.0);
            lotSnapshots.add(ls);
            driving += ls.driving();
            waiting += ls.lane();
            outside += ls.outside();
            paying += busy;
            peak = Math.max(peak, ls.peak());
            foundFull += ls.foundFull();
            gaveUp += ls.gaveUp();
            parked += lotParked;
            turnedAway += ls.turnedAway();
            waitMs += lot.getTotalWaitMs();
            processed += lot.getProcessed();
            longest = Math.max(longest, ls.longestWaitMinutes());
        }
        double average = processed == 0 ? 0 : waitMs / 1000.0 / processed / 60.0;

        return new ParkingSnapshot(state, now, config.cars(), spawned.get(), driving, waiting, outside, paying,
                parked, turnedAway, average, longest, foundFull, gaveUp, peak, config.totalSpaces(), lotSnapshots);
    }

    /**
     * A producer thread's job. It runs again and again on the producer pool. Producer p creates
     * the cars numbered p, p + producers, p + 2 * producers and so on, so the producers share the
     * traffic without any locking between them. Producer p also owns the lines whose index is p
     * modulo the number of producers: only it moves their cars from driving into the line.
     */
    private final class CarProducer implements Runnable {

        private final int index;
        private final SplittableRandom random;
        private int next;

        CarProducer(int index) {
            this.index = index;
            this.random = new SplittableRandom(config.seed() + index);
            this.next = index;
        }

        @Override
        public void run() {
            try {
                double now = clock.now();
                int due = carsDue(now);
                while (next < due) {
                    spawn(next, now);
                    next += config.producerThreads();
                }
                for (int i = index; i < lines.size(); i += config.producerThreads()) {
                    lines.get(i).deliverArrivals(now);
                }
                if (spawned.get() >= config.cars() && ownedLinesAreEmpty()) {
                    if (producersFinished.incrementAndGet() == config.producerThreads()) {
                        stopAttendants();
                    }
                    return;
                }
                producerPool.schedule(this, tickMillis(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // the simulation was stopped
            } catch (RuntimeException e) {
                e.printStackTrace();
                stop();
            }
        }

        private boolean ownedLinesAreEmpty() {
            for (int i = index; i < lines.size(); i += config.producerThreads()) {
                if (lines.get(i).drivingSize() > 0) {
                    return false;
                }
            }
            return true;
        }

        private int carsDue(double now) {
            double fraction = config.shape().fractionArrived(now / config.arrivalSeconds());
            return (int) Math.min(config.cars(), Math.floor(config.cars() * fraction));
        }

        private long tickMillis() {
            return Math.max(1, Math.min(20, Math.round(1000.0 / clock.getSpeed())));
        }

        private void spawn(int carIndex, double now) {
            int lotIndex = switch (config.strategy()) {
                case SHORTEST_LINE -> shortestLineLot();
                case MOST_FREE_SPACES -> mostFreeSpacesLot();
                default -> weightedLot();
            };
            double paySeconds = config.payingSeconds() * (0.5 + random.nextDouble());
            Car car = new Car(carIndex + 1, lotIndex, now, now + config.driveSeconds(), paySeconds,
                    random.nextDouble());
            lines.get(lotIndex).startDriving(car);
            spawned.incrementAndGet();
        }

        private int weightedLot() {
            double pick = random.nextDouble() * cumulativeWeights[cumulativeWeights.length - 1];
            for (int g = 0; g < cumulativeWeights.length; g++) {
                if (pick < cumulativeWeights[g]) {
                    return g;
                }
            }
            return cumulativeWeights.length - 1;
        }

        /** The shortest line among lots that still have a free space. If every lot is full, the shortest line overall. */
        private int shortestLineLot() {
            int count = lots.size();
            int start = random.nextInt(count);
            int best = -1;
            int bestLoad = Integer.MAX_VALUE;
            int bestAny = start;
            int bestAnyLoad = Integer.MAX_VALUE;
            for (int k = 0; k < count; k++) {
                int g = (start + k) % count;
                int load = lines.get(g).load();
                if (load < bestAnyLoad) {
                    bestAny = g;
                    bestAnyLoad = load;
                }
                if (lots.get(g).getFreeSpaces() > 0 && load < bestLoad) {
                    best = g;
                    bestLoad = load;
                }
            }
            return best >= 0 ? best : bestAny;
        }

        /** The lot with the most free spaces once the cars already heading there are counted. */
        private int mostFreeSpacesLot() {
            int count = lots.size();
            int start = random.nextInt(count);
            int best = start;
            long bestRoom = Long.MIN_VALUE;
            for (int k = 0; k < count; k++) {
                int g = (start + k) % count;
                long room = (long) lots.get(g).getFreeSpaces() - lines.get(g).load();
                if (room > bestRoom) {
                    best = g;
                    bestRoom = room;
                }
            }
            return best;
        }
    }
}
