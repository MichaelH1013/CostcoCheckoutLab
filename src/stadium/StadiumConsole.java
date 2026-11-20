package stadium;

import common.ArrivalShape;

import java.util.Locale;

/**
 * Runs a stadium simulation without the GUI and prints the results, like CostcoSimulation.
 * Good for experiments: pick a preset and change numbers with name=value arguments.
 *
 *   java -cp out stadium.StadiumConsole football gates=12 scanners=3
 */
public class StadiumConsole {

    public static void main(String[] args) throws InterruptedException {
        StadiumConfig config;
        try {
            config = parse(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.out.println();
            printUsage();
            System.exit(1);
            return;
        }

        System.out.println("Stadium Entry Simulator");
        System.out.println(describe(config));

        StadiumSimulation simulation = new StadiumSimulation(config);
        long startMs = System.currentTimeMillis();
        simulation.start();
        while (!simulation.awaitFinished(2000)) {
            StadiumSnapshot s = simulation.snapshot();
            System.out.printf("  %.1f min: %d of %d admitted, %d in lines, %d waiting outside%n",
                    s.simMinutes(), s.admitted(), s.totalFans(), s.waiting(), s.outside());
        }
        long realMs = System.currentTimeMillis() - startMs;

        StadiumSnapshot result = simulation.snapshot();
        double openMinutes = simulation.getFinishedAt() / 60.0;
        double staffCost = config.totalScanners() * StadiumConfig.WAGE_PER_HOUR * openMinutes / 60;

        System.out.println();
        System.out.println("Results");
        System.out.println("-------");
        System.out.println("Fans admitted: " + result.admitted() + " of " + config.fans());
        System.out.printf("Average wait: %.1f minutes%n", result.averageWaitMinutes());
        System.out.printf("Longest wait: %.1f minutes%n", result.longestWaitMinutes());
        System.out.println("Longest line (lane plus fans waiting outside it): " + result.peakLine());
        System.out.println("Fans who found the lane full: " + result.foundFull());
        System.out.println("Fans who gave up (crowd limit full): " + result.gaveUp());
        System.out.printf("Gates open until the last fan was inside: %.1f minutes%n", openMinutes);
        System.out.printf("Scanner cost at $%.0f per hour: $%.2f%n", StadiumConfig.WAGE_PER_HOUR, staffCost);
        System.out.printf("Real time taken: %.1f seconds%n", realMs / 1000.0);
        System.out.println();
        System.out.printf("%-8s %9s %10s %10s %10s%n", "Gate", "Admitted", "Avg wait", "Longest", "Per minute");
        for (StadiumSnapshot.GateSnapshot gate : result.gates()) {
            System.out.printf("%-8s %9d %9.1fm %9.1fm %10.1f%n", gate.name(), gate.admitted(),
                    gate.averageWaitMinutes(), gate.longestWaitMinutes(), gate.perMinute());
        }
    }

    private static String describe(StadiumConfig c) {
        return String.format("%,d fans, %d gates x %d scanners = %d scanner threads, lane capacity %d, %s, %s, %.0f simulated seconds per real second",
                c.fans(), c.gates(), c.scannersPerGate(), c.totalScanners(), c.queueCapacity(),
                c.strategy().name().toLowerCase(Locale.ROOT), c.shape().name().toLowerCase(Locale.ROOT), c.speed());
    }

    private static StadiumConfig parse(String[] args) {
        StadiumConfig.Builder builder = StadiumPreset.DEMO.config().toBuilder();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (equals < 0) {
                builder = presetByName(arg).config().toBuilder();
                continue;
            }
            String key = arg.substring(0, equals).toLowerCase(Locale.ROOT);
            String value = arg.substring(equals + 1);
            try {
                switch (key) {
                    case "gates" -> builder.gates(Integer.parseInt(value));
                    case "scanners" -> builder.scannersPerGate(Integer.parseInt(value));
                    case "fans" -> builder.fans(Integer.parseInt(value));
                    case "minutes" -> builder.arrivalMinutes(Double.parseDouble(value));
                    case "service" -> builder.serviceSeconds(Double.parseDouble(value));
                    case "queue" -> builder.queueCapacity(Integer.parseInt(value));
                    case "strategy" -> builder.strategy(QueueStrategy.valueOf(value.toUpperCase(Locale.ROOT)));
                    case "shape" -> builder.shape(ArrivalShape.valueOf(value.toUpperCase(Locale.ROOT)));
                    case "producers" -> builder.producerThreads(Integer.parseInt(value));
                    case "walk" -> builder.walkSeconds(Double.parseDouble(value));
                    case "bias" -> builder.gateBias(Double.parseDouble(value));
                    case "speed" -> builder.speed(Double.parseDouble(value));
                    case "seed" -> builder.seed(Long.parseLong(value));
                    case "crowd" -> builder.crowdLimit(Integer.parseInt(value));
                    default -> throw new IllegalArgumentException("Unknown setting: " + key);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a number for " + key + ": " + value);
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("No enum constant")) {
                    throw new IllegalArgumentException("Unknown value for " + key + ": " + value);
                }
                throw e;
            }
        }
        return builder.build();
    }

    private static StadiumPreset presetByName(String name) {
        for (StadiumPreset preset : StadiumPreset.values()) {
            if (preset.name().equalsIgnoreCase(name)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown preset: " + name);
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp out stadium.StadiumConsole [preset] [name=value ...]");
        System.out.println("Presets:  demo (default), football, concert, stress");
        System.out.println("Settings: gates, scanners (per gate), fans, minutes (arrival window), service (seconds),");
        System.out.println("          queue (lane capacity), strategy (assigned_gate | shortest_line | shared_queue),");
        System.out.println("          shape (uniform | surge | late_rush), producers, walk (seconds), bias, speed, seed,");
        System.out.println("          crowd (most fans allowed to wait outside a full lane, 0 = no limit)");
        System.out.println("Example:  java -cp out stadium.StadiumConsole football gates=12 scanners=3 strategy=shortest_line");
    }
}
