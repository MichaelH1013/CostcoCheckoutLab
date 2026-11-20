package costco;

public class Cashier implements Runnable {

    private final int id;
    private final CheckoutQueue queue;
    private int customersServed = 0;

    public Cashier(int id, CheckoutQueue queue) {
        this.id = id;
        this.queue = queue;
    }

    public static int serviceMinutes(int customerId) {
        return 2 + customerId % 3;
    }

    public int getId() {
        return id;
    }

    public int getCustomersServed() {
        return customersServed;
    }

    @Override
    public void run() {
        try {
            while (true) {
                int customerId = queue.next();
                if (customerId == CheckoutQueue.STOP) {
                    break;
                }
                Thread.sleep((long) serviceMinutes(customerId) * CheckoutQueue.MS_PER_MINUTE);
                customersServed++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
