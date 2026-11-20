package stadium;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * An entry point to the stadium: its scanners and its statistics. With a line per gate the gate
 * has its own FanLine. With the shared-queue strategy every gate points at the same FanLine.
 */
public final class Gate {

    private final int id;
    private final String name;
    private final FanLine line;
    private final RecentAdmissions recent;
    private final List<GateScanner> scanners = new ArrayList<>();

    private final LongAdder admitted = new LongAdder();
    private final LongAdder totalWaitMs = new LongAdder();
    private final AtomicLong longestWaitMs = new AtomicLong();

    Gate(int id, String name, FanLine line, int scannerCount, SimClock clock, RecentAdmissions recent,
         Runnable onScannerExit) {
        this.id = id;
        this.name = name;
        this.line = line;
        this.recent = recent;
        for (int i = 1; i <= scannerCount; i++) {
            scanners.add(new GateScanner(this, i, clock, onScannerExit));
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public FanLine getLine() {
        return line;
    }

    public List<GateScanner> getScanners() {
        return scanners;
    }

    /** Called by this gate's scanners. LongAdder keeps many scanner threads from fighting over one lock. */
    void recordAdmission(Fan fan) {
        long ms = Math.round(fan.waitSeconds() * 1000);
        admitted.increment();
        totalWaitMs.add(ms);
        longestWaitMs.accumulateAndGet(ms, Math::max);
        recent.add(fan);
    }

    public long getAdmitted() {
        return admitted.sum();
    }

    public double getAverageWaitMinutes() {
        long count = admitted.sum();
        if (count == 0) {
            return 0;
        }
        return totalWaitMs.sum() / 1000.0 / count / 60.0;
    }

    public double getLongestWaitMinutes() {
        return longestWaitMs.get() / 1000.0 / 60.0;
    }

    long getTotalWaitMs() {
        return totalWaitMs.sum();
    }

    public int busyScanners() {
        int busy = 0;
        for (GateScanner scanner : scanners) {
            if (scanner.getCurrent() != null) {
                busy++;
            }
        }
        return busy;
    }
}
