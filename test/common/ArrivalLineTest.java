package common;

import runner.Check;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Tests for the bounded buffer that every simulation is built on. These are the tests that matter
 * most: if the line loses an item, hands one out twice, or deadlocks, every result is wrong.
 */
public final class ArrivalLineTest {

    private record Item(int id, double arriveAt) {
    }

    private static final Item STOP = new Item(-1, 0);

    private static ArrivalLine<Item> line(int capacity, int maxOutside, List<Integer> gaveUp) {
        return new ArrivalLine<>("test", capacity, STOP, Item::arriveAt, Item::id, item -> { },
                maxOutside, item -> {
                    if (gaveUp != null) {
                        gaveUp.add(item.id());
                    }
                });
    }

    public static void run() {
        System.out.println("ArrivalLine");

        Check.test("items come out in the order they arrived, even through the overflow list", () -> {
            ArrivalLine<Item> line = line(3, 0, null);
            for (int i = 1; i <= 10; i++) {
                line.startApproaching(new Item(i, i));
            }
            Check.equal(10, line.deliverArrivals(100), "items delivered");
            Check.equal(3, line.laneSize(), "lane size");
            Check.equal(7, line.outsideSize(), "outside size");
            Check.equal(7, line.getFoundFull(), "found the lane full");
            for (int expected = 1; expected <= 10; expected++) {
                Check.equal(expected, line.take().id(), "item " + expected);
            }
            Check.equal(0, line.laneSize() + line.outsideSize(), "line is empty at the end");
        });

        Check.test("deliverArrivals only releases items whose time has come, earliest first", () -> {
            ArrivalLine<Item> line = line(10, 0, null);
            line.startApproaching(new Item(3, 30));
            line.startApproaching(new Item(1, 10));
            line.startApproaching(new Item(2, 20));
            Check.equal(0, line.deliverArrivals(5), "nothing is due at 5");
            Check.equal(2, line.deliverArrivals(25), "two are due at 25");
            Check.equal(1, line.approachingSize(), "one still approaching");
            Check.equal(1, line.take().id(), "first out");
            Check.equal(2, line.take().id(), "second out");
        });

        Check.test("stop values come after every item and the GUI views never show them", () -> {
            ArrivalLine<Item> line = line(2, 0, null);
            for (int i = 1; i <= 5; i++) {
                line.startApproaching(new Item(i, i));
            }
            line.deliverArrivals(100);
            line.closeFor(2);
            for (Item item : line.laneItems(10)) {
                Check.that(item != STOP, "a stop value appeared in the lane view");
            }
            for (Item item : line.outsideItems(10)) {
                Check.that(item != STOP, "a stop value appeared in the outside view");
            }
            for (int expected = 1; expected <= 5; expected++) {
                Check.equal(expected, line.take().id(), "item before the stop values");
            }
            Check.that(line.take() == STOP, "first stop value");
            Check.that(line.take() == STOP, "second stop value");
        });

        Check.test("a limit on the outside list makes extra arrivals give up and be counted", () -> {
            List<Integer> gaveUp = new ArrayList<>();
            ArrivalLine<Item> line = line(2, 3, gaveUp);
            for (int i = 1; i <= 10; i++) {
                line.startApproaching(new Item(i, i));
            }
            line.deliverArrivals(100);
            Check.equal(2, line.laneSize(), "lane size");
            Check.equal(3, line.outsideSize(), "outside size is capped");
            Check.equal(5, line.getGaveUp(), "gave up");
            Check.equal(List.of(6, 7, 8, 9, 10), gaveUp, "the latest arrivals are the ones who gave up");
            for (int expected = 1; expected <= 5; expected++) {
                Check.equal(expected, line.take().id(), "queued item");
            }
        });

        Check.test("with no limit nobody ever gives up", () -> {
            ArrivalLine<Item> line = line(1, 0, null);
            for (int i = 1; i <= 500; i++) {
                line.startApproaching(new Item(i, i));
            }
            line.deliverArrivals(1000);
            Check.equal(0, line.getGaveUp(), "gave up");
            Check.equal(500, line.laneSize() + line.outsideSize(), "everyone is waiting");
        });

        Check.test("20,000 items, 4 producers and 8 consumers at once: each item is taken exactly once", () -> {
            int producers = 4;
            int perProducer = 5_000;
            int total = producers * perProducer;
            int consumers = 8;
            ArrivalLine<Item> line = line(50, 0, null);
            AtomicIntegerArray taken = new AtomicIntegerArray(total + 1);
            AtomicInteger takenTotal = new AtomicInteger();

            List<Thread> threads = new ArrayList<>();
            for (int c = 0; c < consumers; c++) {
                Thread consumer = new Thread(() -> {
                    try {
                        while (true) {
                            Item item = line.take();
                            if (item == STOP) {
                                return;
                            }
                            taken.incrementAndGet(item.id());
                            takenTotal.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "line-test-consumer");
                consumer.start();
                threads.add(consumer);
            }
            List<Thread> producerThreads = new ArrayList<>();
            for (int p = 0; p < producers; p++) {
                int first = p * perProducer + 1;
                Thread producer = new Thread(() -> {
                    for (int i = 0; i < perProducer; i++) {
                        line.startApproaching(new Item(first + i, first + i));
                    }
                }, "line-test-producer");
                producer.start();
                producerThreads.add(producer);
            }
            // the one owner thread moves arrivals into the line while the producers are still adding
            int delivered = 0;
            long deadline = System.currentTimeMillis() + 30_000;
            while (delivered < total && System.currentTimeMillis() < deadline) {
                delivered += line.deliverArrivals(1e9);
            }
            for (Thread producer : producerThreads) {
                producer.join(10_000);
            }
            Check.equal(total, delivered, "items delivered into the line");
            line.closeFor(consumers);
            for (Thread consumer : threads) {
                consumer.join(30_000);
                Check.that(!consumer.isAlive(), "a consumer never finished (deadlock or lost stop value)");
            }
            Check.equal(total, takenTotal.get(), "items taken in total");
            for (int id = 1; id <= total; id++) {
                if (taken.get(id) != 1) {
                    throw new AssertionError("item " + id + " was taken " + taken.get(id) + " times");
                }
            }
            Check.that(line.getFoundFull() > 0, "the test never filled the lane, so it did not exercise the overflow list");
        });
    }
}
