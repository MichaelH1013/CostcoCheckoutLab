package parking;

import common.ArrivalShape;

import java.util.Locale;

/**
 * Runs a parking simulation without the GUI and prints the results, like CostcoSimulation.
 * Good for experiments: pick a preset and change numbers with name=value arguments.
 *
 *   java -cp out parking.ParkingConsole football lots=8 strategy=most_free_spaces
 */
public class ParkingConsole {

    public static void main(String[] args) throws InterruptedException {
        ParkingConfig config;
        try {
            config = parse(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.out.println();
            printUsage();
            System.exit(1);
            return;
        }

        System.out.println("Stadium Parking Simulator");
        System.out.println(describe(config));

        ParkingSimulation simulation = new ParkingSimulation(config);
        long startMs = System.currentTimeMillis();
        simulation.start();
        while (!simulation.awaitFinished(2000)) {
            ParkingSnapshot s = simulation.snapshot();
            System.out.printf("  %.1f min: %d parked, %d turned away, %d in lines, %d waiting on the street%n",
                    s.simMinutes(), s.parked(), s.turnedAway(), s.waiting(), s.outside());
        }
        long realMs = System.currentTimeMillis() - startMs;

        ParkingSnapshot result = simulation.snapshot();
        double openMinutes = simulation.getFinishedAt() / 60.0;
        double staffCost = config.totalAttendants() * ParkingConfig.WAGE_PER_HOUR * openMinutes / 60;
        double revenue = result.parked() * ParkingConfig.FEE_PER_CAR;
        double lost = (result.turnedAway() + result.gaveUp()) * ParkingConfig.FEE_PER_CAR;
        double walkSum = 0;
        for (ParkingSnapshot.LotSnapshot lot : result.lots()) {
            walkSum += lot.parked() * lot.walkSeconds();
        }
        double averageWalk = result.parked() == 0 ? 0 : walkSum / result.parked() / 60.0;

        System.out.println();
        System.out.println("Results");
        System.out.println("-------");
        System.out.println("Cars parked: " + result.parked() + " of " + config.cars());
        System.out.println("Cars turned away (lot full): " + result.turnedAway());
        System.out.printf("Average wait at the entrance: %.1f minutes%n", result.averageWaitMinutes());
        System.out.printf("Longest wait: %.1f minutes%n", result.longestWaitMinutes());
        System.out.println("Longest line (lane plus cars waiting on the street): " + result.peakLine());
        System.out.println("Cars who found the lane full: " + result.foundFull());
        System.out.println("Cars that drove on (street limit full): " + result.gaveUp());
        System.out.printf("Average walk from the lot to the stadium: %.1f minutes%n", averageWalk);
        System.out.printf("Entrances open until the last car was handled: %.1f minutes%n", openMinutes);
        System.out.printf("Parking fees at $%.0f per car: $%,.2f (turned away or drove on: $%,.2f lost)%n",
                ParkingConfig.FEE_PER_CAR, revenue, lost);
        System.out.printf("Attendant cost at $%.0f per hour: $%,.2f%n", ParkingConfig.WAGE_PER_HOUR, staffCost);
        System.out.printf("Real time taken: %.1f seconds%n", realMs / 1000.0);
        System.out.println();
        System.out.printf("%-7s %7s %7s %9s %10s %9s %8s %10s%n", "Lot", "Spaces", "Parked", "Turned", "Avg wait",
                "Longest", "Walk", "Filled at");
        for (ParkingSnapshot.LotSnapshot lot : result.lots()) {
            String filled = Double.isNaN(lot.filledAtMinutes()) ? "never"
                    : String.format("%.1f min", lot.filledAtMinutes());
            System.out.printf("%-7s %7d %7d %9d %9.1fm %8.1fm %7.1fm %10s%n", lot.name(), lot.spaces(),
                    lot.parked(), lot.turnedAway(), lot.averageWaitMinutes(), lot.longestWaitMinutes(),
                    lot.walkSeconds() / 60.0, filled);
        }
    }

    private static String describe(ParkingConfig c) {
        return String.format("%,d cars, %d lots x %,d spaces, %d attendants per lot = %d attendant threads, "
                        + "lane capacity %d, %s, %s, %.0f simulated seconds per real second",
                c.cars(), c.lots(), c.spacesPerLot(), c.attendantsPerLot(), c.totalAttendants(), c.queueCapacity(),
                c.strategy().name().toLowerCase(Locale.ROOT), c.shape().name().toLowerCase(Locale.ROOT), c.speed());
    }

    private static ParkingConfig parse(String[] args) {
        ParkingConfig.Builder builder = ParkingPreset.DEMO.config().toBuilder();
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
                    case "lots" -> builder.lots(Integer.parseInt(value));
                    case "attendants" -> builder.attendantsPerLot(Integer.parseInt(value));
                    case "cars" -> builder.cars(Integer.parseInt(value));
                    case "minutes" -> builder.arrivalMinutes(Double.parseDouble(value));
                    case "pay" -> builder.payingSeconds(Double.parseDouble(value));
                    case "spaces" -> builder.spacesPerLot(Integer.parseInt(value));
                    case "queue" -> builder.queueCapacity(Integer.parseInt(value));
                    case "strategy" -> builder.strategy(LotStrategy.valueOf(value.toUpperCase(Locale.ROOT)));
                    case "shape" -> builder.shape(ArrivalShape.valueOf(value.toUpperCase(Locale.ROOT)));
                    case "producers" -> builder.producerThreads(Integer.parseInt(value));
                    case "drive" -> builder.driveSeconds(Double.parseDouble(value));
                    case "walk" -> builder.walkSeconds(Double.parseDouble(value));
                    case "bias" -> builder.lotBias(Double.parseDouble(value));
                    case "speed" -> builder.speed(Double.parseDouble(value));
                    case "seed" -> builder.seed(Long.parseLong(value));
                    case "street" -> builder.crowdLimit(Integer.parseInt(value));
                    case "layout" -> builder.layout(ParkingLayout.valueOf(value.toUpperCase(Locale.ROOT)));
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

    private static ParkingPreset presetByName(String name) {
        for (ParkingPreset preset : ParkingPreset.values()) {
            if (preset.name().equalsIgnoreCase(name)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown preset: " + name);
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp out parking.ParkingConsole [preset] [name=value ...]");
        System.out.println("Presets:  demo (default), football, concert, stress");
        System.out.println("Settings: lots, attendants (per lot), cars, minutes (arrival window), pay (seconds),");
        System.out.println("          spaces (per lot), queue (lane capacity), strategy (assigned_lot | shortest_line | most_free_spaces),");
        System.out.println("          shape (uniform | surge | late_rush), producers, drive (seconds), walk (seconds), bias, speed, seed,");
        System.out.println("          layout (surrounding | behind_venue, only changes the picture),");
        System.out.println("          street (most cars allowed to wait behind a full lane, 0 = no limit)");
        System.out.println("Example:  java -cp out parking.ParkingConsole football lots=8 strategy=most_free_spaces");
    }
}
