package stadium;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * The stadium version of CheckoutQueue: the bounded buffer between fans (producers) and gate
 * scanners (consumers).
 *
 * A fan goes through up to three places:
 *   walking - fans on their way to the gate, ordered by the time they arrive
 *   lane    - the ArrayBlockingQueue behind the barriers, with a fixed capacity
 *   outside - fans who arrived while the lane was full, in arrival order
 *
 * Scanners block on lane.take() when the lane is empty, exactly like cashiers did. A fan never
 * blocks. Instead of one waiting thread per fan, a fan who finds the lane full is parked in the
 * outside list and moved into the lane when a scanner makes room. That is what lets the
 * simulation hold thousands of waiting fans with a small, fixed number of threads.
 */
public final class FanLine {

    private final String name;
    private final ArrayBlockingQueue<Fan> lane;
    private final ConcurrentLinkedQueue<Fan> outside = new ConcurrentLinkedQueue<>();
    private final PriorityBlockingQueue<Fan> walking = new PriorityBlockingQueue<>(256,
            Comparator.comparingDouble(Fan::getArriveAt).thenComparingInt(Fan::getId));
    private final AtomicInteger peakLength = new AtomicInteger();
    private final LongAdder foundFull = new LongAdder();

    public FanLine(String name, int capacity) {
        this.name = name;
        this.lane = new ArrayBlockingQueue<>(capacity);
    }

    /** Called by a producer thread: the fan starts walking toward this line. */
    public void startWalking(Fan fan) {
        walking.add(fan);
    }

    /**
     * Called by the one producer thread that owns this line: every fan whose walk is over now
     * joins the line. Returns how many arrived.
     */
    public int deliverArrivals(double now) {
        int delivered = 0;
        Fan fan;
        while ((fan = walking.peek()) != null && fan.getArriveAt() <= now) {
            walking.poll();
            join(fan);
            delivered++;
        }
        return delivered;
    }

    /**
     * The lane and the outside list change together, so this is synchronized, like the
     * synchronized methods in CheckoutQueue. A fan only goes straight into the lane when nobody
     * is waiting outside, which keeps the line first come, first served.
     */
    private synchronized void join(Fan fan) {
        fan.markWaiting();
        if (!outside.isEmpty() || !lane.offer(fan)) {
            foundFull.increment();
            outside.add(fan);
        }
        peakLength.accumulateAndGet(lane.size() + outside.size(), Math::max);
    }

    /** Called by scanner threads. Blocks while the lane is empty. */
    public Fan take() throws InterruptedException {
        Fan fan = lane.take();
        promoteOutsideFans();
        return fan;
    }

    private synchronized void promoteOutsideFans() {
        Fan next;
        while ((next = outside.peek()) != null && lane.offer(next)) {
            outside.poll();
        }
    }

    /**
     * Puts one stop value per scanner behind every fan who is still in line, so every fan is
     * scanned before the scanners go home. It follows the same path as a fan so the order holds.
     */
    public synchronized void closeFor(int scanners) {
        for (int i = 0; i < scanners; i++) {
            if (!outside.isEmpty() || !lane.offer(Fan.STOP)) {
                outside.add(Fan.STOP);
            }
        }
    }

    /*
     * Read-only views for the GUI. Each one copies at most max fans, so drawing stays cheap even
     * when thousands of fans are in line. The queues are thread-safe to iterate while the
     * simulation runs.
     */

    /** Fans in the lane, front of the line first. */
    public List<Fan> laneFans(int max) {
        return copyUpTo(lane, max);
    }

    /** Fans waiting outside the lane, first to arrive first. */
    public List<Fan> outsideFans(int max) {
        return copyUpTo(outside, max);
    }

    /** Fans still walking toward the line, the ones nearest to arriving first. */
    public List<Fan> walkingFans(int max) {
        List<Fan> result = new ArrayList<>();
        for (Object o : walking.toArray()) {
            if (result.size() >= max) {
                break;
            }
            result.add((Fan) o);
        }
        return result;
    }

    private static List<Fan> copyUpTo(Iterable<Fan> source, int max) {
        List<Fan> result = new ArrayList<>();
        for (Fan fan : source) {
            if (result.size() >= max) {
                break;
            }
            if (fan != Fan.STOP) {
                result.add(fan);
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

    public int walkingSize() {
        return walking.size();
    }

    /** Everyone heading for this line or already in it. Used to pick the shortest line. */
    public int load() {
        return walking.size() + lane.size() + outside.size();
    }

    public int getPeakLength() {
        return peakLength.get();
    }

    public long getFoundFull() {
        return foundFull.sum();
    }
}
