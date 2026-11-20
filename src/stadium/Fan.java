package stadium;

/**
 * One fan. A fan is plain data, not a thread, so the simulation can hold tens of thousands of
 * them. Producer threads create fans and scanner threads move them through the states.
 */
public final class Fan {

    public enum State { WALKING, WAITING, SCANNING, INSIDE, GAVE_UP }

    /** The stop value, like the -1 the Costco cashiers used. A scanner that takes it goes home. */
    public static final Fan STOP = new Fan(0, -1, 0, 0, 0, 0);

    private final int id;
    private final int targetGate;
    private final double spawnedAt;
    private final double arriveAt;
    private final double scanSeconds;
    private final double spot;

    private volatile State state = State.WALKING;
    private volatile double startedAt = Double.NaN;
    private volatile double admittedAt = Double.NaN;
    private volatile long admittedNanos = 0;
    private volatile int servedGate = -1;

    Fan(int id, int targetGate, double spawnedAt, double arriveAt, double scanSeconds, double spot) {
        this.id = id;
        this.targetGate = targetGate;
        this.spawnedAt = spawnedAt;
        this.arriveAt = arriveAt;
        this.scanSeconds = scanSeconds;
        this.spot = spot;
    }

    public int getId() {
        return id;
    }

    /** Index of the gate this fan walks to, or -1 when the fan joins the shared line. */
    public int getTargetGate() {
        return targetGate;
    }

    public double getSpawnedAt() {
        return spawnedAt;
    }

    /** Simulated time at which the fan reaches the line. */
    public double getArriveAt() {
        return arriveAt;
    }

    public double getScanSeconds() {
        return scanSeconds;
    }

    /** A random number from 0 to 1 that the GUI uses to place the fan on its approach path. */
    public double getSpot() {
        return spot;
    }

    public State getState() {
        return state;
    }

    public double getStartedAt() {
        return startedAt;
    }

    public double getAdmittedAt() {
        return admittedAt;
    }

    /** Real clock time (System.nanoTime) when the fan got in. The GUI uses it to animate the walk to a seat. */
    public long getAdmittedNanos() {
        return admittedNanos;
    }

    /** Index of the gate whose scanner served this fan, or -1 if not served yet. */
    public int getServedGate() {
        return servedGate;
    }

    /** Time from reaching the line to the start of scanning. Waiting outside a full lane counts. */
    public double waitSeconds() {
        return Math.max(0, startedAt - arriveAt);
    }

    void markWaiting() {
        state = State.WAITING;
    }

    /** The fan arrived when the lane and the crowd outside it were both full, and left. */
    void markGaveUp() {
        state = State.GAVE_UP;
    }

    void markScanning(int gateIndex, double now) {
        servedGate = gateIndex;
        startedAt = now;
        state = State.SCANNING;
    }

    void markInside(double now) {
        admittedAt = now;
        admittedNanos = System.nanoTime();
        state = State.INSIDE;
    }
}
