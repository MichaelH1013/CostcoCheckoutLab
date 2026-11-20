package parking;

import stadium.SimClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * A parking lot: an entrance line, its attendants, a fixed number of spaces and its statistics.
 * The spaces are a second bounded resource. A Semaphore hands them out, so many attendant
 * threads can never give away more spaces than the lot has.
 */
public final class Lot {

    private final int id;
    private final String name;
    private final int spaces;
    private final double walkSeconds;
    private final CarLine line;
    private final SimClock clock;
    private final RecentCars recent;
    private final Consumer<Car> onParked;
    private final List<Attendant> attendants = new ArrayList<>();

    private final Semaphore freeSpaces;
    private final AtomicInteger nextSpace = new AtomicInteger();
    private final LongAdder parked = new LongAdder();
    private final LongAdder turnedAway = new LongAdder();
    private final LongAdder totalWaitMs = new LongAdder();
    private final AtomicLong longestWaitMs = new AtomicLong();
    private volatile double filledAt = Double.NaN;

    Lot(int id, String name, int spaces, double walkSeconds, CarLine line, int attendantCount,
        SimClock clock, RecentCars recent, Consumer<Car> onParked, Runnable onAttendantExit) {
        this.onParked = onParked;
        this.id = id;
        this.name = name;
        this.spaces = spaces;
        this.walkSeconds = walkSeconds;
        this.line = line;
        this.clock = clock;
        this.recent = recent;
        this.freeSpaces = new Semaphore(spaces);
        for (int i = 1; i <= attendantCount; i++) {
            attendants.add(new Attendant(this, i, clock, onAttendantExit));
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSpaces() {
        return spaces;
    }

    /** Simulated seconds it takes to walk from this lot to the stadium. */
    public double getWalkSeconds() {
        return walkSeconds;
    }

    public CarLine getLine() {
        return line;
    }

    public List<Attendant> getAttendants() {
        return attendants;
    }

    /** Takes one space and returns its number, or returns -1 if the lot is full. */
    int reserveSpace() {
        if (!freeSpaces.tryAcquire()) {
            return -1;
        }
        int space = nextSpace.getAndIncrement();
        if (space == spaces - 1) {
            filledAt = clock.now();
        }
        return space;
    }

    void recordParked(Car car) {
        parked.increment();
        recordWait(car);
        onParked.accept(car);
    }

    void recordTurnedAway(Car car) {
        turnedAway.increment();
        recordWait(car);
    }

    /** LongAdder and AtomicLong keep many attendant threads from fighting over one lock. */
    private void recordWait(Car car) {
        long ms = Math.round(car.waitSeconds() * 1000);
        totalWaitMs.add(ms);
        longestWaitMs.accumulateAndGet(ms, Math::max);
        recent.add(car);
    }

    public int getFreeSpaces() {
        return freeSpaces.availablePermits();
    }

    public long getParked() {
        return parked.sum();
    }

    public long getTurnedAway() {
        return turnedAway.sum();
    }

    /** Cars that reached an attendant, parked or turned away. */
    public long getProcessed() {
        return parked.sum() + turnedAway.sum();
    }

    long getTotalWaitMs() {
        return totalWaitMs.sum();
    }

    public double getAverageWaitMinutes() {
        long count = getProcessed();
        if (count == 0) {
            return 0;
        }
        return totalWaitMs.sum() / 1000.0 / count / 60.0;
    }

    public double getLongestWaitMinutes() {
        return longestWaitMs.get() / 1000.0 / 60.0;
    }

    /** Simulated seconds at which the last space was taken, or NaN if the lot never filled. */
    public double getFilledAt() {
        return filledAt;
    }

    public int busyAttendants() {
        int busy = 0;
        for (Attendant attendant : attendants) {
            if (attendant.getCurrent() != null) {
                busy++;
            }
        }
        return busy;
    }
}
