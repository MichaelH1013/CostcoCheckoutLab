package common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * The bounded buffer between the producers (fans or cars) and the consumers (scanners or
 * attendants). It is the generalised version of the Costco lab's CheckoutQueue, and FanLine and
 * CarLine are the two uses of it.
 *
 * An item goes through up to three places:
 *   approaching - on its way to the line, ordered by the time it arrives
 *   lane        - the ArrayBlockingQueue with a fixed capacity
 *   outside     - items that arrived while the lane was full, in arrival order
 *
 * Consumers block on lane.take() when the lane is empty, exactly like the Costco cashiers. A
 * producer never blocks. An item that finds the lane full is parked in the outside list and moved
 * into the lane when a consumer makes room. That is what lets thousands of waiting items be held
 * with a small, fixed number of threads. Because the outside list is not bounded, it can be given
 * a limit: when it is full, a new arrival gives up (reneges) and is counted instead of queued.
 *
 * Which thread may call what:
 *   startApproaching  any producer thread
 *   deliverArrivals   exactly one thread per line (the producer that owns it), because it removes
 *                     items from the approaching queue
 *   take              consumer threads
 *   closeFor          the thread that finishes the run, after every item has arrived
 *
 * @param <T> the kind of item that waits in the line
 */
public class ArrivalLine<T> {

    private final String name;
    private final T stop;
    private final ToDoubleFunction<T> arriveAt;
    private final Consumer<T> onJoin;
    private final Consumer<T> onGiveUp;
    private final int maxOutside;
    private final ArrayBlockingQueue<T> lane;
    private final ConcurrentLinkedQueue<T> outside = new ConcurrentLinkedQueue<>();
    private final PriorityBlockingQueue<T> approaching;
    private final AtomicInteger peakLength = new AtomicInteger();
    private final LongAdder foundFull = new LongAdder();
    private final LongAdder gaveUp = new LongAdder();

    /**
     * @param name        shown in the GUI and the results
     * @param capacity    items that fit in the lane
     * @param stop        the value consumers take to learn that they can go home
     * @param arriveAt    the simulated time at which an item reaches the line
     * @param id          a tie-breaker so items with the same arrival time keep a fixed order
     * @param onJoin      called when an item reaches the line (for example to set its state)
     * @param maxOutside  most items allowed to wait outside the lane, or 0 for no limit
     * @param onGiveUp    called for an item that arrives when the outside list is full
     */
    public ArrivalLine(String name, int capacity, T stop, ToDoubleFunction<T> arriveAt, ToIntFunction<T> id,
                       Consumer<T> onJoin, int maxOutside, Consumer<T> onGiveUp) {
        this.name = name;
        this.stop = stop;
        this.arriveAt = arriveAt;
        this.onJoin = onJoin;
        this.onGiveUp = onGiveUp;
        this.maxOutside = maxOutside;
        this.lane = new ArrayBlockingQueue<>(capacity);
        this.approaching = new PriorityBlockingQueue<>(256,
                Comparator.comparingDouble(arriveAt).thenComparingInt(id));
    }

    /** Called by a producer thread: the item starts moving toward this line. */
    public void startApproaching(T item) {
        approaching.add(item);
    }

    /**
     * Called by the one producer thread that owns this line: every item whose trip is over now
     * joins the line. Returns how many arrived.
     */
    public int deliverArrivals(double now) {
        int delivered = 0;
        T item;
        while ((item = approaching.peek()) != null && arriveAt.applyAsDouble(item) <= now) {
            approaching.poll();
            join(item);
            delivered++;
        }
        return delivered;
    }

    /**
     * The lane and the outside list change together, so this is synchronized, like the
     * synchronized methods in CheckoutQueue. An item only goes straight into the lane when nobody
     * is waiting outside, which keeps the line first come, first served.
     */
    private synchronized void join(T item) {
        onJoin.accept(item);
        if (!outside.isEmpty() || !lane.offer(item)) {
            foundFull.increment();
            if (maxOutside > 0 && outside.size() >= maxOutside) {
                gaveUp.increment();
                onGiveUp.accept(item);
                return;
            }
            outside.add(item);
        }
        peakLength.accumulateAndGet(lane.size() + outside.size(), Math::max);
    }

    /** Called by consumer threads. Blocks while the lane is empty. */
    public T take() throws InterruptedException {
        T item = lane.take();
        promoteOutside();
        return item;
    }

    private synchronized void promoteOutside() {
        T next;
        while ((next = outside.peek()) != null && lane.offer(next)) {
            outside.poll();
        }
    }

    /**
     * Puts one stop value per consumer behind every item that is still in line, so every item is
     * handled before the consumers go home. It follows the same path as an item so the order holds.
     * The outside limit does not apply to stop values.
     */
    public synchronized void closeFor(int consumers) {
        for (int i = 0; i < consumers; i++) {
            if (!outside.isEmpty() || !lane.offer(stop)) {
                outside.add(stop);
            }
        }
    }

    /*
     * Read-only views for the GUI. Each one copies at most max items, so drawing stays cheap even
     * when thousands of items are in line. The queues are thread-safe to iterate while the
     * simulation runs.
     */

    /** Items in the lane, front of the line first. */
    public List<T> laneItems(int max) {
        return copyUpTo(lane, max);
    }

    /** Items waiting outside the lane, first to arrive first. */
    public List<T> outsideItems(int max) {
        return copyUpTo(outside, max);
    }

    /** Items still on their way to the line, the ones nearest to arriving first. */
    @SuppressWarnings("unchecked")
    public List<T> approachingItems(int max) {
        List<T> result = new ArrayList<>();
        for (Object o : approaching.toArray()) {
            if (result.size() >= max) {
                break;
            }
            result.add((T) o);
        }
        return result;
    }

    private List<T> copyUpTo(Iterable<T> source, int max) {
        List<T> result = new ArrayList<>();
        for (T item : source) {
            if (result.size() >= max) {
                break;
            }
            if (item != stop) {
                result.add(item);
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

    public int approachingSize() {
        return approaching.size();
    }

    /** Everyone heading for this line or already in it. Used to pick the shortest line. */
    public int load() {
        return approaching.size() + lane.size() + outside.size();
    }

    public int getPeakLength() {
        return peakLength.get();
    }

    /** Items that arrived while the lane was full, including the ones that then gave up. */
    public long getFoundFull() {
        return foundFull.sum();
    }

    /** Items that arrived while the lane and the outside list were both full, and left. */
    public long getGaveUp() {
        return gaveUp.sum();
    }
}
