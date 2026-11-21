package costco;

import runner.Check;

public class CostcoTest {

    public static void run() {
        System.out.println("Costco tests");

        Check.test("checkout queue preserves FIFO order", () -> {
            CheckoutQueue queue = new CheckoutQueue(3, 3);

            queue.join(1);
            queue.join(2);
            queue.join(3);

            Check.equal(1, queue.next(), "first customer");
            Check.equal(2, queue.next(), "second customer");
            Check.equal(3, queue.next(), "third customer");
        });
    }
}