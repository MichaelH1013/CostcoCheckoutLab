package costco;

import java.util.Random;
import java.util.Scanner;

public class CostcoSimulation {

    private static final double WAGE_PER_HOUR = 18.0;
    private static final long SEED = 311;

    public static void main(String[] args) throws InterruptedException {
        Scanner input = new Scanner(System.in);

        System.out.println("Costco Checkout Simulator");
        int customerCount = readNumber(input, "Number of customers: ");
        int cashierCount = readNumber(input, "Number of cashiers: ");
        int queueSize = readNumber(input, "Queue size (how many customers can wait in line): ");
        int arrivalMinutes = readNumber(input, "Minutes over which customers finish shopping: ");

        CheckoutQueue queue = new CheckoutQueue(queueSize, customerCount);
        Random random = new Random(SEED);

        Cashier[] cashiers = new Cashier[cashierCount];
        Thread[] cashierThreads = new Thread[cashierCount];
        for (int i = 0; i < cashierCount; i++) {
            cashiers[i] = new Cashier(i + 1, queue);
            cashierThreads[i] = new Thread(cashiers[i]);
        }

        Thread[] customerThreads = new Thread[customerCount];
        for (int i = 0; i < customerCount; i++) {
            int shoppingMinutes = 1 + random.nextInt(arrivalMinutes);
            Customer customer = new Customer(i + 1, shoppingMinutes, queue);
            customerThreads[i] = new Thread(customer);
        }

        long start = System.currentTimeMillis();

        for (Thread thread : cashierThreads) {
            thread.start();
        }
        for (Thread thread : customerThreads) {
            thread.start();
        }

        for (Thread thread : customerThreads) {
            thread.join();
        }
        queue.stopCashiers(cashierCount);
        for (Thread thread : cashierThreads) {
            thread.join();
        }

        long elapsedMs = System.currentTimeMillis() - start;
        double openMinutes = (double) elapsedMs / CheckoutQueue.MS_PER_MINUTE;
        double staffCost = cashierCount * WAGE_PER_HOUR * openMinutes / 60;

        int served = 0;
        for (Cashier cashier : cashiers) {
            served += cashier.getCustomersServed();
        }

        System.out.println();
        System.out.println("Results");
        System.out.println("-------");
        System.out.println("Customers served: " + served + " of " + customerCount);
        System.out.println("Cashiers: " + cashierCount);
        System.out.println("Queue size: " + queueSize);
        System.out.printf("Average wait: %.1f minutes%n", queue.getAverageWaitMinutes());
        System.out.printf("Longest wait: %.1f minutes%n", queue.getLongestWaitMinutes());
        System.out.println("Longest line: " + queue.getPeakLength());
        System.out.println("Customers who found the line full: " + queue.getCustomersWhoFoundItFull());
        System.out.printf("Store open until the last customer left: %.1f minutes%n", openMinutes);
        System.out.printf("Cashier cost at $%.0f per hour: $%.2f%n", WAGE_PER_HOUR, staffCost);
        for (Cashier cashier : cashiers) {
            System.out.println("Cashier " + cashier.getId() + " served " + cashier.getCustomersServed());
        }
    }

    private static int readNumber(Scanner input, String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!input.hasNextLine()) {
                System.out.println();
                System.out.println("No more input, stopping.");
                System.exit(1);
            }
            String line = input.nextLine().trim();
            try {
                int number = Integer.parseInt(line);
                if (number >= 1) {
                    return number;
                }
            } catch (NumberFormatException e) {
            }
            System.out.println("Please enter a whole number of 1 or more.");
        }
    }
}
