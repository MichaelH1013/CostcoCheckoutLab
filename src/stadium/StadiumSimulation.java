package stadium;

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

/**
 * The stadium entry simulation.
 *
 * Threads, no matter how many fans there are:
 *   producers - a small ScheduledThreadPoolExecutor. Each producer wakes up every few
 *               milliseconds, creates the fans who are due (a fan is an object, not a thread),
 *               and moves fans who finished walking into the line.
 *   scanners  - a fixed thread pool with one thread per scanner (gates x scannersPerGate).
 *               Each scanner blocks on its line's BlockingQueue, just like the Costco cashiers.
 *
 * So 20 fans or 50,000 fans use the same threads. Only the parameters change.
 */
public final class StadiumSimulation {

    public enum State { NEW, RUNNING, PAUSED, FINISHED, STOPPED }

    private final StadiumConfig config;
    private final SimClock clock;
    private final List<Gate> gates = new ArrayList<>();
    private final List<FanLine> lines = new ArrayList<>();
    private final double[] cumulativeWeights;
    private final RecentAdmissions recentAdmissions = new RecentAdmissions();
    private final ScheduledThreadPoolExecutor producerPool;
    private final ExecutorService scannerPool;

    private final AtomicInteger spawned = new AtomicInteger();
    private final AtomicInteger producersFinished = new AtomicInteger();
    private final AtomicInteger scannersRunning;
    private final CountDownLatch finished = new CountDownLatch(1);

    // Only used when fans come from outside, for example from the parking lots (see event.EventSimulation).
    private final boolean externalFeed;
    private final boolean ownsClock;
    private final AtomicInteger expectedFans = new AtomicInteger();
    private final AtomicInteger nextFanId = new AtomicInteger();
    private final SplittableRandom injectRandom;
    private volatile boolean arrivalsClosed = false;
    private volatile Runnable finishListener;

    private volatile State state = State.NEW;
    private volatile double finishedAt = Double.NaN;

    public StadiumSimulation(StadiumConfig config) {
        this(config, new SimClock(config.speed()), false);
    }

    /**
     * A stadium whose fans do not arrive by themselves. Whoever uses it hands them over with
     * injectFans and calls closeArrivals after the last one. The clock is shared with the caller,
     * so the stadium does not pause it when it finishes. config.fans() is only an upper bound.
     */
    public static StadiumSimulation withOutsideFans(StadiumConfig config, SimClock sharedClock) {
        return new StadiumSimulation(config, sharedClock, true);
    }

    private StadiumSimulation(StadiumConfig config, SimClock clock, boolean externalFeed) {
        this.config = config;
        this.clock = clock;
        this.externalFeed = externalFeed;
        this.ownsClock = !externalFeed;
        this.injectRandom = new SplittableRandom(config.seed() + 1_000_003L);
        this.scannersRunning = new AtomicInteger(config.totalScanners());

        boolean shared = config.strategy() == QueueStrategy.SHARED_QUEUE;
        FanLine sharedLine = null;
        if (shared) {
            sharedLine = new FanLine("Shared line", config.queueCapacity() * config.gates());
            lines.add(sharedLine);
        }
        for (int g = 0; g < config.gates(); g++) {
            String name = "Gate " + (g + 1);
            FanLine line = sharedLine;
            if (!shared) {
                line = new FanLine(name, config.queueCapacity());
                lines.add(line);
            }
            gates.add(new Gate(g, name, line, config.scannersPerGate(), clock, recentAdmissions,
                    this::scannerFinished));
        }

        cumulativeWeights = new double[config.gates()];
        double total = 0;
        for (int g = 0; g < config.gates(); g++) {
            double fromEnd = config.gates() == 1 ? 0 : (double) (config.gates() - 1 - g) / (config.gates() - 1);
            total += 1 + config.gateBias() * fromEnd;
            cumulativeWeights[g] = total;
        }

        producerPool = new ScheduledThreadPoolExecutor(config.producerThreads(), runnable -> {
            Thread thread = new Thread(runnable, "fan-producer");
            thread.setDaemon(true);
            return thread;
        });
        scannerPool = Executors.newFixedThreadPool(config.totalScanners(), runnable -> {
            Thread thread = new Thread(runnable, "scanner");
            thread.setDaemon(true);
            return thread;
        });
    }

    public StadiumConfig getConfig() {
        return config;
    }

    public SimClock getClock() {
        return clock;
    }

    public List<Gate> getGates() {
        return gates;
    }

    public List<FanLine> getLines() {
        return lines;
    }

    public State getState() {
        return state;
    }

    /** The fans who got in most recently, so the GUI can animate them walking to their seats. */
    public RecentAdmissions getRecentAdmissions() {
        return recentAdmissions;
    }

    public synchronized void start() {
        if (state != State.NEW) {
            throw new IllegalStateException("Already started.");
        }
        state = State.RUNNING;
        for (Gate gate : gates) {
            for (GateScanner scanner : gate.getScanners()) {
                scannerPool.execute(scanner);
            }
        }
        for (int p = 0; p < config.producerThreads(); p++) {
            producerPool.schedule(new FanProducer(p), 0, TimeUnit.MILLISECONDS);
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

    /** Ends the simulation early. The scanners are interrupted, so fans still in line are not admitted. */
    public synchronized void stop() {
        if (state == State.FINISHED || state == State.STOPPED) {
            return;
        }
        state = State.STOPPED;
        if (ownsClock) {
            clock.pause();
        }
        producerPool.shutdownNow();
        scannerPool.shutdownNow();
        finished.countDown();
    }

    /** Hands over fans who just reached the stadium. They walk to a gate and join its line. */
    public void injectFans(int count) {
        if (!externalFeed) {
            throw new IllegalStateException("This stadium creates its own fans.");
        }
        synchronized (injectRandom) {
            double now = clock.now();
            for (int i = 0; i < count; i++) {
                createFan(injectRandom, nextFanId.incrementAndGet(), now);
            }
        }
    }

    /** Says that no more fans will be handed over. The stadium finishes once everyone is in. */
    public void closeArrivals() {
        arrivalsClosed = true;
    }

    /** Fans who are known to be coming, for the count shown in the picture. */
    public void addExpectedFans(int count) {
        expectedFans.addAndGet(count);
    }

    /** Called once when the last fan is inside. */
    public void setFinishListener(Runnable listener) {
        finishListener = listener;
    }

    public boolean awaitFinished(long millis) throws InterruptedException {
        return finished.await(millis, TimeUnit.MILLISECONDS);
    }

    public boolean isFinished() {
        return state == State.FINISHED;
    }

    /** Simulated seconds until the last fan was inside, or NaN while the simulation is not finished. */
    public double getFinishedAt() {
        return finishedAt;
    }

    private void scannerFinished() {
        if (scannersRunning.decrementAndGet() > 0) {
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
        scannerPool.shutdown();
        finished.countDown();
        Runnable listener = finishListener;
        if (listener != null) {
            listener.run();
        }
    }

    private void createFan(SplittableRandom random, int fanId, double now) {
        int gateIndex = -1;
        FanLine line;
        switch (config.strategy()) {
            case SHARED_QUEUE -> line = lines.get(0);
            case SHORTEST_LINE -> {
                gateIndex = shortestLineGate(random);
                line = gates.get(gateIndex).getLine();
            }
            default -> {
                gateIndex = weightedGate(random);
                line = gates.get(gateIndex).getLine();
            }
        }
        double scanSeconds = config.serviceSeconds() * (0.5 + random.nextDouble());
        Fan fan = new Fan(fanId, gateIndex, now, now + config.walkSeconds(), scanSeconds, random.nextDouble());
        line.startWalking(fan);
        spawned.incrementAndGet();
    }

    private int weightedGate(SplittableRandom random) {
        double pick = random.nextDouble() * cumulativeWeights[cumulativeWeights.length - 1];
        for (int g = 0; g < cumulativeWeights.length; g++) {
            if (pick < cumulativeWeights[g]) {
                return g;
            }
        }
        return cumulativeWeights.length - 1;
    }

    private int shortestLineGate(SplittableRandom random) {
        int count = gates.size();
        int start = random.nextInt(count);
        int best = start;
        int bestLoad = Integer.MAX_VALUE;
        for (int k = 0; k < count; k++) {
            int g = (start + k) % count;
            int load = gates.get(g).getLine().load();
            if (load < bestLoad) {
                best = g;
                bestLoad = load;
            }
        }
        return best;
    }

    /** Every producer is done, so every fan is in a line. Now tell the scanners to go home. */
    private void stopScanners() {
        int scannersPerLine = config.strategy() == QueueStrategy.SHARED_QUEUE
                ? config.totalScanners() : config.scannersPerGate();
        for (FanLine line : lines) {
            line.closeFor(scannersPerLine);
        }
    }

    public StadiumSnapshot snapshot() {
        double now = Double.isNaN(finishedAt) ? clock.now() : finishedAt;

        List<StadiumSnapshot.LineSnapshot> lineSnapshots = new ArrayList<>();
        int walking = 0;
        int waiting = 0;
        int outside = 0;
        int peak = 0;
        long foundFull = 0;
        for (FanLine line : lines) {
            StadiumSnapshot.LineSnapshot ls = new StadiumSnapshot.LineSnapshot(line.getName(),
                    line.laneSize(), line.outsideSize(), line.walkingSize(), line.getPeakLength(), line.getFoundFull());
            lineSnapshots.add(ls);
            walking += ls.walking();
            waiting += ls.lane();
            outside += ls.outside();
            peak = Math.max(peak, ls.peak());
            foundFull += ls.foundFull();
        }

        List<StadiumSnapshot.GateSnapshot> gateSnapshots = new ArrayList<>();
        int scanning = 0;
        long admitted = 0;
        long waitMs = 0;
        double longest = 0;
        double minutes = now / 60.0;
        for (Gate gate : gates) {
            int busy = gate.busyScanners();
            long done = gate.getAdmitted();
            gateSnapshots.add(new StadiumSnapshot.GateSnapshot(gate.getId(), gate.getName(),
                    gate.getScanners().size(), busy, done, gate.getAverageWaitMinutes(),
                    gate.getLongestWaitMinutes(), minutes > 0 ? done / minutes : 0));
            scanning += busy;
            admitted += done;
            waitMs += gate.getTotalWaitMs();
            longest = Math.max(longest, gate.getLongestWaitMinutes());
        }
        double average = admitted == 0 ? 0 : waitMs / 1000.0 / admitted / 60.0;

        int totalFans = externalFeed ? expectedFans.get() : config.fans();
        return new StadiumSnapshot(state, now, totalFans, config.fans(), spawned.get(), walking, waiting, outside,
                scanning, admitted, average, longest, foundFull, peak, lineSnapshots, gateSnapshots);
    }

    /**
     * A producer thread's job. It runs again and again on the producer pool. Producer p creates
     * the fans numbered p, p + producers, p + 2 * producers and so on, so the producers share the
     * crowd without any locking between them. Producer p also owns the lines whose index is p
     * modulo the number of producers: only it moves their fans from walking into the line.
     */
    private final class FanProducer implements Runnable {

        private final int index;
        private final SplittableRandom random;
        private int next;

        FanProducer(int index) {
            this.index = index;
            this.random = new SplittableRandom(config.seed() + index);
            this.next = index;
        }

        @Override
        public void run() {
            try {
                double now = clock.now();
                if (!externalFeed) {
                    int due = fansDue(now);
                    while (next < due) {
                        createFan(random, next + 1, now);
                        next += config.producerThreads();
                    }
                }
                for (int i = index; i < lines.size(); i += config.producerThreads()) {
                    lines.get(i).deliverArrivals(now);
                }
                boolean noMoreFans = externalFeed ? arrivalsClosed : spawned.get() >= config.fans();
                if (noMoreFans && ownedLinesAreEmpty()) {
                    if (producersFinished.incrementAndGet() == config.producerThreads()) {
                        stopScanners();
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
                if (lines.get(i).walkingSize() > 0) {
                    return false;
                }
            }
            return true;
        }

        private int fansDue(double now) {
            double fraction = config.shape().fractionArrived(now / config.arrivalSeconds());
            return (int) Math.min(config.fans(), Math.floor(config.fans() * fraction));
        }

        private long tickMillis() {
            return Math.max(1, Math.min(20, Math.round(1000.0 / clock.getSpeed())));
        }
    }
}
