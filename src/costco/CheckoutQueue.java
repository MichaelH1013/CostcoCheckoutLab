package costco;

import java.util.concurrent.ArrayBlockingQueue;

public class CheckoutQueue {

    public static final int STOP = -1;
    public static final int MS_PER_MINUTE = 100;

    private final ArrayBlockingQueue<Integer> line;
    private final long[] arrivalTimes;
    private int peakLength = 0;
    private int customersWhoFoundItFull = 0;
    private int waitsRecorded = 0;
    private long totalWaitMs = 0;
    private long longestWaitMs = 0;

    public CheckoutQueue(int capacity, int totalCustomers) {
        line = new ArrayBlockingQueue<>(capacity);
        arrivalTimes = new long[totalCustomers + 1];
    }

    public void join(int customerId) throws InterruptedException {
        arrivalTimes[customerId] = System.currentTimeMillis();
        if (!line.offer(customerId)) {
            countFullQueue();
            line.put(customerId);
        }
        recordLength();
    }

    public int next() throws InterruptedException {
        int customerId = line.take();
        if (customerId != STOP) {
            recordWait(System.currentTimeMillis() - arrivalTimes[customerId]);
        }
        return customerId;
    }

    public void stopCashiers(int numberOfCashiers) throws InterruptedException {
        for (int i = 0; i < numberOfCashiers; i++) {
            line.put(STOP);
        }
    }

    private synchronized void countFullQueue() {
        customersWhoFoundItFull++;
    }

    private synchronized void recordLength() {
        peakLength = Math.max(peakLength, line.size());
    }

    private synchronized void recordWait(long waitMs) {
        waitsRecorded++;
        totalWaitMs += waitMs;
        longestWaitMs = Math.max(longestWaitMs, waitMs);
    }

    public synchronized int getPeakLength() {
        return peakLength;
    }

    public synchronized int getCustomersWhoFoundItFull() {
        return customersWhoFoundItFull;
    }

    public synchronized double getAverageWaitMinutes() {
        if (waitsRecorded == 0) {
            return 0;
        }
        return (double) totalWaitMs / waitsRecorded / MS_PER_MINUTE;
    }

    public synchronized double getLongestWaitMinutes() {
        return (double) longestWaitMs / MS_PER_MINUTE;
    }
}
