package parking;

import java.util.List;

/**
 * A copy of the live numbers at one moment, safe to read from any thread (for example the GUI).
 * While the simulation runs the counts can be off by a few cars because cars move between
 * places as they are counted. After the simulation finishes they are exact.
 */
public record ParkingSnapshot(
        ParkingSimulation.State state,
        double simSeconds,
        int totalCars,
        int spawned,
        int driving,
        int waiting,
        int outside,
        int paying,
        long parked,
        long turnedAway,
        double averageWaitMinutes,
        double longestWaitMinutes,
        long foundFull,
        long gaveUp,
        int peakLine,
        long totalSpaces,
        List<LotSnapshot> lots) {

    public record LotSnapshot(int id, String name, int spaces, int parked, int free, int attendants,
                              int busyAttendants, long turnedAway, int lane, int outside, int driving,
                              int peak, long foundFull, long gaveUp, double averageWaitMinutes, double longestWaitMinutes,
                              double walkSeconds, double filledAtMinutes) {
    }

    public double simMinutes() {
        return simSeconds / 60.0;
    }
}
