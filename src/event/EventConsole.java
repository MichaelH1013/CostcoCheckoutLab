package event;

import parking.LotStrategy;
import parking.ParkingConfig;
import parking.ParkingSnapshot;
import stadium.QueueStrategy;
import stadium.StadiumConfig;
import stadium.StadiumSnapshot;

import java.util.Locale;

/**
 * Runs a whole event without the GUI and prints the results: cars park, then their fans walk to
 * the stadium and go in through the gates. Like StadiumConsole and ParkingConsole, pick a preset
 * and change numbers with name=value arguments.
 *
 *   java -cp out event.EventConsole football lots=8 gates=12 fanspercar=4
 */
public class EventConsole {

    public static void main(String[] args) throws InterruptedException {
        EventConfig config;
        try {
            config = parse(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.out.println();
            printUsage();
            System.exit(1);
            return;
        }

        ParkingConfig p = config.parking();
        StadiumConfig s = config.stadium();
        System.out.println("Stadium Event Simulator (parking, then the gates)");
        System.out.printf("Parking: %,d cars, %d lots x %,d spaces, %d attendants per lot, %s%n", p.cars(), p.lots(),
                p.spacesPerLot(), p.attendantsPerLot(), p.strategy().name().toLowerCase(Locale.ROOT));
        System.out.printf("Stadium: %d gates x %d scanners, scan time %.0f s, %s, %d fans in each car%n", s.gates(),
                s.scannersPerGate(), s.serviceSeconds(), s.strategy().name().toLowerCase(Locale.ROOT), config.fansPerCar());
        System.out.printf("Speed: %.0f simulated seconds per real second%n", p.speed());

        EventSimulation event = new EventSimulation(config);
        long startMs = System.currentTimeMillis();
        event.start();
        while (!event.awaitFinished(2000)) {
            ParkingSnapshot ps = event.getParking().snapshot();
            StadiumSnapshot ss = event.getStadium().snapshot();
            System.out.printf("  %.1f min: %d cars parked, %d turned away, %d fans walking to the stadium, %d waiting at gates, %d inside%n",
                    event.getClock().now() / 60.0, ps.parked(), ps.turnedAway(), event.getFansWalking(),
                    ss.waiting() + ss.outside(), ss.admitted());
        }
        long realMs = System.currentTimeMillis() - startMs;

        ParkingSnapshot ps = event.getParking().snapshot();
        StadiumSnapshot ss = event.getStadium().snapshot();
        double parkingMinutes = event.getParking().getFinishedAt() / 60.0;
        double eventMinutes = event.getFinishedAt() / 60.0;
        double staffCost = (p.totalAttendants() * parkingMinutes + s.totalScanners() * eventMinutes)
                * ParkingConfig.WAGE_PER_HOUR / 60;

        System.out.println();
        System.out.println("Parking");
        System.out.println("-------");
        System.out.println("Cars parked: " + ps.parked() + " of " + p.cars());
        System.out.println("Cars turned away (lot full): " + ps.turnedAway());
        System.out.println("Cars that drove on (street limit full): " + ps.gaveUp());
        System.out.printf("Average wait at the lot entrance: %.1f minutes%n", ps.averageWaitMinutes());
        System.out.printf("Longest wait: %.1f minutes%n", ps.longestWaitMinutes());
        System.out.printf("Last car handled at: %.1f minutes%n", parkingMinutes);
        System.out.println();
        System.out.println("Stadium gates");
        System.out.println("-------------");
        System.out.println("Fans admitted: " + ss.admitted() + " of " + ss.totalFans() + " who parked");
        System.out.printf("Average wait at the gates: %.1f minutes%n", ss.averageWaitMinutes());
        System.out.printf("Longest wait: %.1f minutes%n", ss.longestWaitMinutes());
        System.out.println("Longest line (lane plus fans waiting outside it): " + ss.peakLine());
        System.out.println("Fans who found the lane full: " + ss.foundFull());
        System.out.println("Fans who gave up (crowd limit full): " + ss.gaveUp());
        System.out.printf("Last fan inside at: %.1f minutes%n", eventMinutes);
        System.out.println();
        System.out.println("Whole event");
        System.out.println("-----------");
        String longer = ps.averageWaitMinutes() >= ss.averageWaitMinutes() ? "the parking lots" : "the stadium gates";
        System.out.printf("Fans wait longer at %s (%.1f minutes against %.1f)%n", longer,
                Math.max(ps.averageWaitMinutes(), ss.averageWaitMinutes()),
                Math.min(ps.averageWaitMinutes(), ss.averageWaitMinutes()));
        System.out.printf("Parking fees at $%.0f per car: $%,.2f (turned away: $%,.2f lost)%n",
                ParkingConfig.FEE_PER_CAR, ps.parked() * ParkingConfig.FEE_PER_CAR, ps.turnedAway() * ParkingConfig.FEE_PER_CAR);
        System.out.printf("Staff cost at $%.0f per hour (attendants until the lots finish, scanners until the end): $%,.2f%n",
                ParkingConfig.WAGE_PER_HOUR, staffCost);
        System.out.printf("Real time taken: %.1f seconds%n", realMs / 1000.0);
    }

    private static EventConfig parse(String[] args) {
        EventConfig base = EventPreset.DEMO.config();
        for (String arg : args) {
            if (arg.indexOf('=') < 0) {
                base = presetByName(arg).config();
            }
        }
        ParkingConfig.Builder parking = base.parking().toBuilder();
        StadiumConfig.Builder stadium = base.stadium().toBuilder();
        int fansPerCar = base.fansPerCar();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = arg.substring(0, equals).toLowerCase(Locale.ROOT);
            String value = arg.substring(equals + 1);
            try {
                switch (key) {
                    case "cars" -> parking.cars(Integer.parseInt(value));
                    case "lots" -> parking.lots(Integer.parseInt(value));
                    case "spaces" -> parking.spacesPerLot(Integer.parseInt(value));
                    case "attendants" -> parking.attendantsPerLot(Integer.parseInt(value));
                    case "pay" -> parking.payingSeconds(Double.parseDouble(value));
                    case "lotstrategy" -> parking.strategy(LotStrategy.valueOf(value.toUpperCase(Locale.ROOT)));
                    case "minutes" -> parking.arrivalMinutes(Double.parseDouble(value));
                    case "gates" -> stadium.gates(Integer.parseInt(value));
                    case "scanners" -> stadium.scannersPerGate(Integer.parseInt(value));
                    case "scan" -> stadium.serviceSeconds(Double.parseDouble(value));
                    case "gatestrategy" -> stadium.strategy(QueueStrategy.valueOf(value.toUpperCase(Locale.ROOT)));
                    case "fanspercar" -> fansPerCar = Integer.parseInt(value);
                    case "speed" -> parking.speed(Double.parseDouble(value));
                    case "streetlimit" -> parking.crowdLimit(Integer.parseInt(value));
                    case "gatecrowd" -> stadium.crowdLimit(Integer.parseInt(value));
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
        return EventConfig.of(parking.build(), stadium.build(), fansPerCar);
    }

    private static EventPreset presetByName(String name) {
        for (EventPreset preset : EventPreset.values()) {
            if (preset.name().equalsIgnoreCase(name)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown preset: " + name);
    }

    private static void printUsage() {
        System.out.println("Usage: java -cp out event.EventConsole [preset] [name=value ...]");
        System.out.println("Presets:  demo (default), football, concert, stress");
        System.out.println("Settings: cars, lots, spaces (per lot), attendants (per lot), pay (seconds), minutes (arrival window),");
        System.out.println("          lotstrategy (assigned_lot | shortest_line | most_free_spaces),");
        System.out.println("          gates, scanners (per gate), scan (seconds),");
        System.out.println("          gatestrategy (assigned_gate | shortest_line | shared_queue),");
        System.out.println("          fanspercar, speed, streetlimit and gatecrowd (0 = no limit)");
        System.out.println("Example:  java -cp out event.EventConsole football lots=8 gates=12 fanspercar=4");
    }
}
