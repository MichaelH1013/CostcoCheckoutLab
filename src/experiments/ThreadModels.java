package experiments;

import common.ArrivalShape;
import stadium.QueueStrategy;
import stadium.StadiumConfig;
import stadium.StadiumSimulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compares three ways to build the same stadium gates, to show why the simulation does not use
 * one thread per fan:
 *
 *   THREAD_PER_FAN          the design of the Costco lab. Every fan is a platform thread that
 *                           walks (sleeps) and then calls put() on a bounded queue, blocking when
 *                           the line is full.
 *   VIRTUAL_THREAD_PER_FAN  the same code, but every fan is a virtual thread (Java 21 and later).
 *   POOLS_AND_OBJECTS       this project: a fan is a small object, a few producer threads move
 *                           fans along, and a fixed pool of scanner threads takes them.
 *
 * Every design has the same gates, scanners, walk time, scan time and arrival times, and must
 * scan every fan. Each one runs in a separate Java process with a limited heap (-Xmx2g) and a
 * small thread stack (-Xss256k), so that a design that runs out of memory fails on its own
 * instead of taking the others down with it.
 *
 * Run it from the project folder:
 *
 *   java -cp out experiments.ThreadModels --all          every design at 1,000 to 50,000 fans
 *   java -cp out experiments.ThreadModels POOLS_AND_OBJECTS 20000    one run
 *
 * The time is mostly spent waiting, because the scanners are the slow part, so the interesting
 * numbers are the number of threads and the memory, and whether the run finished at all.
 */
public final class ThreadModels {

    private static final int GATES = 40;
    private static final int SCANNERS = 3;
    private static final int LANE = 50;
    private static final double WALK_SECONDS = 30;
    private static final double SCAN_SECONDS = 6;
    private static final double WINDOW_SECONDS = 600;
    private static final double SPEED = 100;

    private enum Design { THREAD_PER_FAN, VIRTUAL_THREAD_PER_FAN, POOLS_AND_OBJECTS }

    private record Result(Design design, int fans, boolean finished, long scanned, double seconds, int peakThreads,
                          double peakHeapMb, String note) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java -cp out experiments.ThreadModels --all");
            System.out.println("       java -cp out experiments.ThreadModels <THREAD_PER_FAN | VIRTUAL_THREAD_PER_FAN | POOLS_AND_OBJECTS> <fans>");
            System.exit(1);
        }
        if (args[0].equals("--all")) {
            runAll(args.length > 1 ? Integer.parseInt(args[1]) : 50_000);
            return;
        }
        Design design = Design.valueOf(args[0]);
        int fans = Integer.parseInt(args[1]);
        runOne(design, fans);
    }

    // ------------------------------------------------------------------ the driver: one process per run

    private static void runAll(int largest) throws Exception {
        int[] sizes = {1_000, 5_000, 20_000, 50_000};
        System.out.printf("%d gates x %d scanners, scan time %.0f ms, walk %.0f ms, fans arrive over %.0f s of real time%n%n",
                GATES, SCANNERS, SCAN_SECONDS / SPEED * 1000, WALK_SECONDS / SPEED * 1000, WINDOW_SECONDS / SPEED);
        System.out.println("| Fans | Design | Finished | Time (s) | Peak platform threads | Peak heap (MB) |");
        System.out.println("|---|---|---|---|---|---|");
        for (int fans : sizes) {
            if (fans > largest) {
                continue;
            }
            for (Design design : Design.values()) {
                String line = runInChildProcess(design, fans);
                System.out.println(line);
            }
        }
    }

    private static String runInChildProcess(Design design, int fans) throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder builder = new ProcessBuilder(java, "-Xmx2g", "-Xss256k", "-cp", System.getProperty("java.class.path"),
                "experiments.ThreadModels", design.name(), String.valueOf(fans));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (Exception e) {
                // the process ended
            }
        });
        reader.start();
        boolean done = process.waitFor(300, TimeUnit.SECONDS);
        if (!done) {
            process.destroyForcibly();
            reader.join(2000);
            return String.format("| %,d | `%s` | no (still running after 300 s) | - | - | - |", fans, design);
        }
        reader.join(5000);
        for (String line : output.toString().split("\n")) {
            if (line.startsWith("RESULT|")) {
                String[] p = line.split("\\|");
                return String.format("| %,d | `%s` | %s | %s | %s | %s |", fans, design, p[1], p[2], p[3], p[4]);
            }
        }
        String why = output.toString().trim().replace('\n', ' ');
        if (why.length() > 80) {
            why = why.substring(0, 80);
        }
        return String.format("| %,d | `%s` | no (%s) | - | - | - |", fans, design, why.isEmpty() ? "the process stopped" : why);
    }

    // ------------------------------------------------------------------ one run in this process

    private static void runOne(Design design, int fans) {
        AtomicLong peakUsed = new AtomicLong();
        Thread sampler = new Thread(() -> {
            Runtime runtime = Runtime.getRuntime();
            while (!Thread.currentThread().isInterrupted()) {
                peakUsed.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory(), Math::max);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "heap-sampler");
        sampler.setDaemon(true);
        System.gc();
        sampler.start();

        long start = System.nanoTime();
        long scanned = 0;
        String note = "";
        boolean finished = false;
        try {
            scanned = switch (design) {
                case THREAD_PER_FAN -> runBlockingPut(fans, false);
                case VIRTUAL_THREAD_PER_FAN -> runBlockingPut(fans, true);
                case POOLS_AND_OBJECTS -> runEngine(fans);
            };
            finished = scanned == fans;
        } catch (Throwable e) {
            note = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        sampler.interrupt();
        int peakThreads = ManagementFactory.getThreadMXBean().getPeakThreadCount();
        peakUsed.accumulateAndGet(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(), Math::max);
        Result result = new Result(design, fans, finished, scanned, seconds, peakThreads, peakUsed.get() / 1048576.0, note);
        if (finished) {
            System.out.printf("RESULT|yes|%.1f|%,d|%.0f%n", result.seconds(), result.peakThreads(), result.peakHeapMb());
        } else {
            System.out.printf("RESULT|no (%s)|%.1f|%,d|%.0f%n", note.isEmpty() ? "scanned " + scanned : note, result.seconds(),
                    result.peakThreads(), result.peakHeapMb());
        }
        System.exit(finished ? 0 : 2);
    }

    /** The Costco lab design: every fan is a thread that sleeps, then blocks in put() on a bounded queue. */
    private static long runBlockingPut(int fans, boolean virtual) throws Exception {
        int stop = -1;
        List<BlockingQueue<Integer>> lanes = new ArrayList<>();
        for (int g = 0; g < GATES; g++) {
            lanes.add(new ArrayBlockingQueue<>(LANE));
        }
        AtomicInteger scanned = new AtomicInteger();
        long scanMs = Math.round(SCAN_SECONDS / SPEED * 1000);
        long walkMs = Math.round(WALK_SECONDS / SPEED * 1000);
        long windowMs = Math.round(WINDOW_SECONDS / SPEED * 1000);

        List<Thread> scannerThreads = new ArrayList<>();
        for (int g = 0; g < GATES; g++) {
            BlockingQueue<Integer> lane = lanes.get(g);
            for (int s = 0; s < SCANNERS; s++) {
                Random random = new Random(g * 1000L + s);
                Thread scanner = new Thread(() -> {
                    try {
                        while (true) {
                            if (lane.take() == stop) {
                                return;
                            }
                            Thread.sleep(Math.round(scanMs * (0.5 + random.nextDouble())));
                            scanned.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "scanner-" + g + "-" + s);
                scanner.start();
                scannerThreads.add(scanner);
            }
        }

        Random random = new Random(311);
        List<Thread> fanThreads = new ArrayList<>(fans);
        for (int i = 0; i < fans; i++) {
            BlockingQueue<Integer> lane = lanes.get(random.nextInt(GATES));
            long delay = Math.round(random.nextDouble() * windowMs) + walkMs;
            int id = i;
            Runnable fan = () -> {
                try {
                    Thread.sleep(delay);
                    lane.put(id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            fanThreads.add(virtual ? Thread.ofVirtual().start(fan) : startPlatform(fan));
        }
        for (Thread fan : fanThreads) {
            fan.join();
        }
        for (BlockingQueue<Integer> lane : lanes) {
            for (int s = 0; s < SCANNERS; s++) {
                lane.put(stop);
            }
        }
        for (Thread scanner : scannerThreads) {
            scanner.join();
        }
        return scanned.get();
    }

    private static Thread startPlatform(Runnable body) {
        Thread thread = new Thread(body, "fan");
        thread.start();
        return thread;
    }

    /** This project's design, using the real stadium simulation with matching settings. */
    private static long runEngine(int fans) throws Exception {
        StadiumConfig config = StadiumConfig.builder().gates(GATES).scannersPerGate(SCANNERS).fans(fans)
                .arrivalMinutes(WINDOW_SECONDS / 60).serviceSeconds(SCAN_SECONDS).queueCapacity(LANE)
                .strategy(QueueStrategy.ASSIGNED_GATE).shape(ArrivalShape.UNIFORM).gateBias(0)
                .producerThreads(2).walkSeconds(WALK_SECONDS).speed(SPEED).build();
        StadiumSimulation sim = new StadiumSimulation(config);
        sim.start();
        if (!sim.awaitFinished(280_000)) {
            sim.stop();
        }
        return sim.snapshot().admitted();
    }
}
