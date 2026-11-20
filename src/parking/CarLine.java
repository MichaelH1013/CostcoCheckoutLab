package parking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * The line of cars at one lot entrance: the bounded buffer between cars (producers) and
 * attendants (consumers). It works like FanLine in the stadium package.
 *
 * A car goes through up to three places:
 *   driving - cars on their way to the entrance, ordered by the time they arrive
 *   lane    - the ArrayBlockingQueue on the entrance road, with a fixed capacity
 *   outside - cars that arrived while the lane was full, backed up onto the street
 *
 * Attendants block on lane.take() when the lane is empty. A car never blocks: one that finds the
 * lane full waits in the outside list and moves into the lane when an attendant makes room, so
 * thousands of waiting cars need no extra threads.
 */
public final class CarLine {

    private final String name;
    private final ArrayBlockingQueue<Car> lane;
    private final ConcurrentLinkedQueue<Car> outside = new ConcurrentLinkedQueue<>();
    private final PriorityBlockingQueue<Car> driving = new PriorityBlockingQueue<>(256,
            Comparator.comparingDouble(Car::getArriveAt).thenComparingInt(Car::getId));
    private final AtomicInteger peakLength = new AtomicInteger();
    private final LongAdder foundFull = new LongAdder();

    public CarLine(String name, int capacity) {
        this.name = name;
        this.lane = new ArrayBlockingQueue<>(capacity);
    }

    /** Called by a producer thread: the car starts driving toward this entrance. */
    public void startDriving(Car car) {
        driving.add(car);
    }

    /**
     * Called by the one producer thread that owns this line: every car that has finished driving
     * now joins the line. Returns how many arrived.
     */
    public int deliverArrivals(double now) {
        int delivered = 0;
        Car car;
        while ((car = driving.peek()) != null && car.getArriveAt() <= now) {
            driving.poll();
            join(car);
            delivered++;
        }
        return delivered;
    }

    /**
     * The lane and the outside list change together, so this is synchronized. A car only goes
     * straight into the lane when nobody is waiting outside, which keeps the line first come,
     * first served.
     */
    private synchronized void join(Car car) {
        car.markWaiting();
        if (!outside.isEmpty() || !lane.offer(car)) {
            foundFull.increment();
            outside.add(car);
        }
        peakLength.accumulateAndGet(lane.size() + outside.size(), Math::max);
    }

    /** Called by attendant threads. Blocks while the lane is empty. */
    public Car take() throws InterruptedException {
        Car car = lane.take();
        promoteOutsideCars();
        return car;
    }

    private synchronized void promoteOutsideCars() {
        Car next;
        while ((next = outside.peek()) != null && lane.offer(next)) {
            outside.poll();
        }
    }

    /** Puts one stop value per attendant behind every car still in line. */
    public synchronized void closeFor(int attendants) {
        for (int i = 0; i < attendants; i++) {
            if (!outside.isEmpty() || !lane.offer(Car.STOP)) {
                outside.add(Car.STOP);
            }
        }
    }

    /** Cars in the lane, front of the line first. Copies at most max cars. */
    public List<Car> laneCars(int max) {
        return copyUpTo(lane, max);
    }

    /** Cars waiting outside the lane, first to arrive first. Copies at most max cars. */
    public List<Car> outsideCars(int max) {
        return copyUpTo(outside, max);
    }

    /** Cars still driving toward the line, the ones nearest to arriving first. */
    public List<Car> drivingCars(int max) {
        List<Car> result = new ArrayList<>();
        for (Object o : driving.toArray()) {
            if (result.size() >= max) {
                break;
            }
            result.add((Car) o);
        }
        return result;
    }

    private static List<Car> copyUpTo(Iterable<Car> source, int max) {
        List<Car> result = new ArrayList<>();
        for (Car car : source) {
            if (result.size() >= max) {
                break;
            }
            if (car != Car.STOP) {
                result.add(car);
            }
        }
        return result;
    }

    public String getName() {
        return name;
    }

    public int capacity() {
        return lane.size() + lane.remainingCapacity();
    }

    public int laneSize() {
        return lane.size();
    }

    public int outsideSize() {
        return outside.size();
    }

    public int drivingSize() {
        return driving.size();
    }

    /** Everyone heading for this line or already in it. Used to pick the shortest line. */
    public int load() {
        return driving.size() + lane.size() + outside.size();
    }

    public int getPeakLength() {
        return peakLength.get();
    }

    public long getFoundFull() {
        return foundFull.sum();
    }
}
