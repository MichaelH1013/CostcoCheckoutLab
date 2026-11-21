package costco;

import runner.Check;
import java.util.concurrent.CountDownLatch;

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

        Check.test("cashier serves customers and tracks count", () -> {
            CheckoutQueue queue = new CheckoutQueue(3, 2);
            Cashier cashier = new Cashier(1, queue);
            Thread cashierThread = new Thread(cashier);

            cashierThread.start();

            queue.join(1);
            queue.join(2);
            queue.stopCashiers(1);

            cashierThread.join();

            Check.equal(2, cashier.getCustomersServed(), "customers served");
        });

        Check.test("full queue blocks until space becomes available", () -> {
            CheckoutQueue queue = new CheckoutQueue(1, 2);

            queue.join(1);

            CountDownLatch attemptingToJoin = new CountDownLatch(1);

            Thread secondCustomer = new Thread(() -> {
                attemptingToJoin.countDown();
                try {
                    queue.join(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            secondCustomer.start();
            attemptingToJoin.await();

            Check.that(secondCustomer.isAlive(), "second customer should be blocked");

            Check.equal(1, queue.next(), "first customer leaves queue");

            secondCustomer.join();

            Check.equal(2, queue.next(), "second customer enters queue");
            Check.equal(1, queue.getCustomersWhoFoundItFull(), "full queue count");
        });

        Check.test("new queue starts with zero wait statistics", () -> {
            CheckoutQueue queue = new CheckoutQueue(3, 3);

            Check.near(0.0, queue.getAverageWaitMinutes(), 0.001, "average wait");
            Check.near(0.0, queue.getLongestWaitMinutes(), 0.001, "longest wait");
        });

        Check.test("queue records customer wait time", () -> {
            CheckoutQueue queue = new CheckoutQueue(2, 1);

            queue.join(1);

            Thread.sleep(150);

            Check.equal(1, queue.next(), "customer leaves queue");

            double averageWait = queue.getAverageWaitMinutes();
            double longestWait = queue.getLongestWaitMinutes();

            Check.that(averageWait > 0, "average wait should be positive");
            Check.near(averageWait, longestWait, 0.001,
                    "average and longest wait for one customer");
        });

        Check.test("cashier stops when queue is empty", () -> {
            CheckoutQueue queue = new CheckoutQueue(2, 0);
            Cashier cashier = new Cashier(1, queue);
            Thread cashierThread = new Thread(cashier);

            cashierThread.start();

            queue.stopCashiers(1);

            cashierThread.join(1000);

            Check.that(!cashierThread.isAlive(), "cashier should terminate");
            Check.equal(0, cashier.getCustomersServed(), "no customers served");
        });
    }
}