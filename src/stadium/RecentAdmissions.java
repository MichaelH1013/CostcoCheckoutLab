package stadium;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Remembers the last few hundred fans who got in so the GUI can animate them walking to their
 * seats. It is a fixed-size ring: scanners overwrite the oldest entry without any locking, and
 * fans who are not remembered are simply not animated. Admitted fans are not kept anywhere else,
 * so memory stays flat no matter how many thousands of fans the stadium admits.
 */
public final class RecentAdmissions {

    private static final int SIZE = 1024;

    private final AtomicReferenceArray<Fan> ring = new AtomicReferenceArray<>(SIZE);
    private final AtomicInteger next = new AtomicInteger();

    void add(Fan fan) {
        ring.set(next.getAndIncrement() & (SIZE - 1), fan);
    }

    /** Fans who got in within the last maxAgeNanos of real time. */
    public List<Fan> within(long maxAgeNanos) {
        long now = System.nanoTime();
        List<Fan> result = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            Fan fan = ring.get(i);
            if (fan != null && now - fan.getAdmittedNanos() <= maxAgeNanos) {
                result.add(fan);
            }
        }
        return result;
    }
}
