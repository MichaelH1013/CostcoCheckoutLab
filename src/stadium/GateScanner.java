package stadium;

/**
 * A scanner at a gate. The consumer, like Cashier: it takes the next fan from the gate's line,
 * scans the ticket (and checks the bag), and lets the fan in.
 */
public final class GateScanner implements Runnable {

    private final Gate gate;
    private final int number;
    private final SimClock clock;
    private final Runnable onExit;
    private volatile Fan current;
    private volatile long fansScanned = 0;

    GateScanner(Gate gate, int number, SimClock clock, Runnable onExit) {
        this.gate = gate;
        this.number = number;
        this.clock = clock;
        this.onExit = onExit;
    }

    public int getNumber() {
        return number;
    }

    /** The fan being scanned right now, or null when this scanner is free. */
    public Fan getCurrent() {
        return current;
    }

    public long getFansScanned() {
        return fansScanned;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("scanner-" + gate.getName() + "-" + number);
        try {
            while (true) {
                Fan fan = gate.getLine().take();
                if (fan == Fan.STOP) {
                    break;
                }
                current = fan;
                fan.markScanning(gate.getId(), clock.now());
                clock.sleepSim(fan.getScanSeconds());
                fan.markInside(clock.now());
                gate.recordAdmission(fan);
                current = null;
                fansScanned++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            current = null;
            onExit.run();
        }
    }
}
