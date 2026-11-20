package stadium;

import java.util.List;

/**
 * A copy of the live numbers at one moment, safe to read from any thread (for example the GUI).
 * While the simulation runs the counts can be off by a few fans because fans move between
 * places as they are counted. After the simulation finishes they are exact.
 *
 * totalFans is the number of fans who will come, as far as is known. maxFans is the most there
 * could be. They are the same unless the fans come from outside (parked cars), where totalFans
 * grows as cars park and maxFans is every car's fans.
 */
public record StadiumSnapshot(
        StadiumSimulation.State state,
        double simSeconds,
        int totalFans,
        int maxFans,
        int spawned,
        int walking,
        int waiting,
        int outside,
        int scanning,
        long admitted,
        double averageWaitMinutes,
        double longestWaitMinutes,
        long foundFull,
        long gaveUp,
        int peakLine,
        List<LineSnapshot> lines,
        List<GateSnapshot> gates) {

    /** A line: one per gate, or a single shared one. */
    public record LineSnapshot(String name, int lane, int outside, int walking, int peak, long foundFull,
                               long gaveUp) {
    }

    public record GateSnapshot(int id, String name, int scanners, int busyScanners, long admitted,
                               double averageWaitMinutes, double longestWaitMinutes, double perMinute) {
    }

    public double simMinutes() {
        return simSeconds / 60.0;
    }
}
