package parking;

import stadium.SimClock;

/**
 * An attendant at a lot entrance. The consumer, like Cashier: it takes the next car from the
 * entrance line, takes the payment or checks the pass, and waves the car in to a space. If the
 * lot is already full the car is turned away.
 */
public final class Attendant implements Runnable {

    /** Simulated seconds it takes to tell a driver the lot is full. */
    static final double TURN_AWAY_SECONDS = 3;

    private final Lot lot;
    private final int number;
    private final SimClock clock;
    private final Runnable onExit;
    private volatile Car current;
    private volatile long carsHandled = 0;

    Attendant(Lot lot, int number, SimClock clock, Runnable onExit) {
        this.lot = lot;
        this.number = number;
        this.clock = clock;
        this.onExit = onExit;
    }

    public int getNumber() {
        return number;
    }

    /** The car being handled right now, or null when this attendant is free. */
    public Car getCurrent() {
        return current;
    }

    public long getCarsHandled() {
        return carsHandled;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("attendant-" + lot.getName() + "-" + number);
        try {
            while (true) {
                Car car = lot.getLine().take();
                if (car == Car.STOP) {
                    break;
                }
                current = car;
                car.markPaying(lot.getId(), clock.now());
                // The space is taken before the payment, so two attendants can never sell the last one twice.
                int space = lot.reserveSpace();
                if (space < 0) {
                    clock.sleepSim(TURN_AWAY_SECONDS);
                    car.markTurnedAway(clock.now());
                    lot.recordTurnedAway(car);
                } else {
                    clock.sleepSim(car.getPaySeconds());
                    car.markParked(space, clock.now());
                    lot.recordParked(car);
                }
                current = null;
                carsHandled++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            current = null;
            onExit.run();
        }
    }
}
