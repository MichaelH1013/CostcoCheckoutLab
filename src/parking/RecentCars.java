package parking;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Remembers the last few hundred cars that were parked or turned away so the GUI can animate
 * them. It is a fixed-size ring: attendants overwrite the oldest entry without any locking, and
 * cars that are not remembered are simply not animated. Finished cars are not kept anywhere
 * else, so memory stays flat no matter how many thousands of cars the lots handle.
 */
public final class RecentCars {

    private static final int SIZE = 1024;

    private final AtomicReferenceArray<Car> ring = new AtomicReferenceArray<>(SIZE);
    private final AtomicInteger next = new AtomicInteger();

    void add(Car car) {
        ring.set(next.getAndIncrement() & (SIZE - 1), car);
    }

    /** Cars that finished within the last maxAgeNanos of real time. */
    public List<Car> within(long maxAgeNanos) {
        long now = System.nanoTime();
        List<Car> result = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            Car car = ring.get(i);
            if (car != null && now - car.getFinishedNanos() <= maxAgeNanos) {
                result.add(car);
            }
        }
        return result;
    }
}
