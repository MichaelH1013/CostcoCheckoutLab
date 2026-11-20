package parking;

/**
 * One car, with the fan inside it. A car is plain data, not a thread, so the simulation can
 * hold tens of thousands of them. Producer threads create cars and attendant threads move
 * them through the states.
 */
public final class Car {

    public enum State { DRIVING, WAITING, PAYING, PARKED, TURNED_AWAY, GAVE_UP }

    /** The stop value, like the -1 the Costco cashiers used. An attendant who takes it goes home. */
    public static final Car STOP = new Car(0, -1, 0, 0, 0, 0);

    private final int id;
    private final int targetLot;
    private final double spawnedAt;
    private final double arriveAt;
    private final double paySeconds;
    private final double spot;

    private volatile State state = State.DRIVING;
    private volatile double startedAt = Double.NaN;
    private volatile double finishedAt = Double.NaN;
    private volatile long finishedNanos = 0;
    private volatile int servedLot = -1;
    private volatile int space = -1;

    Car(int id, int targetLot, double spawnedAt, double arriveAt, double paySeconds, double spot) {
        this.id = id;
        this.targetLot = targetLot;
        this.spawnedAt = spawnedAt;
        this.arriveAt = arriveAt;
        this.paySeconds = paySeconds;
        this.spot = spot;
    }

    public int getId() {
        return id;
    }

    /** Index of the lot this car drives to. */
    public int getTargetLot() {
        return targetLot;
    }

    public double getSpawnedAt() {
        return spawnedAt;
    }

    /** Simulated time at which the car reaches the back of the line at the lot entrance. */
    public double getArriveAt() {
        return arriveAt;
    }

    /** Simulated seconds the attendant needs to take payment or check the pass. */
    public double getPaySeconds() {
        return paySeconds;
    }

    /** A random number from 0 to 1 that the GUI uses to place the car on its approach road. */
    public double getSpot() {
        return spot;
    }

    public State getState() {
        return state;
    }

    public double getStartedAt() {
        return startedAt;
    }

    public double getFinishedAt() {
        return finishedAt;
    }

    /** Real clock time (System.nanoTime) when the car parked or was turned away. The GUI uses it for animation. */
    public long getFinishedNanos() {
        return finishedNanos;
    }

    /** Index of the lot whose attendant served this car, or -1 if not served yet. */
    public int getServedLot() {
        return servedLot;
    }

    /** Number of the parking space this car took, or -1. */
    public int getSpace() {
        return space;
    }

    /** Time from reaching the line to the start of service. Waiting outside a full lane counts. */
    public double waitSeconds() {
        return Math.max(0, startedAt - arriveAt);
    }

    void markWaiting() {
        state = State.WAITING;
    }

    /** The car arrived when the lane and the street behind it were both full, and drove on. */
    void markGaveUp() {
        state = State.GAVE_UP;
    }

    void markPaying(int lotIndex, double now) {
        servedLot = lotIndex;
        startedAt = now;
        state = State.PAYING;
    }

    void markParked(int spaceNumber, double now) {
        space = spaceNumber;
        finishedAt = now;
        finishedNanos = System.nanoTime();
        state = State.PARKED;
    }

    void markTurnedAway(double now) {
        finishedAt = now;
        finishedNanos = System.nanoTime();
        state = State.TURNED_AWAY;
    }
}
