package costco;

public class Customer implements Runnable {

    private final int id;
    private final int shoppingMinutes;
    private final CheckoutQueue queue;

    public Customer(int id, int shoppingMinutes, CheckoutQueue queue) {
        this.id = id;
        this.shoppingMinutes = shoppingMinutes;
        this.queue = queue;
    }

    public int getId() {
        return id;
    }

    @Override
    public void run() {
        try {
            Thread.sleep((long) shoppingMinutes * CheckoutQueue.MS_PER_MINUTE);
            queue.join(id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
