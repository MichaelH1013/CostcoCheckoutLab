package parking;

import common.ArrivalLine;

import java.util.List;

/**
 * The line of cars at one lot entrance: the bounded buffer between cars (producers) and
 * attendants (consumers). All of the synchronization lives in the generic ArrivalLine. This class
 * only says what a car is and keeps the names the parking code uses: a car drives to the line.
 * The cars that back up onto the street are the ones waiting outside the lane.
 */
public final class CarLine extends ArrivalLine<Car> {

    public CarLine(String name, int capacity) {
        this(name, capacity, 0);
    }

    /** maxOutside is the most cars allowed to wait on the street, or 0 for no limit. */
    public CarLine(String name, int capacity, int maxOutside) {
        super(name, capacity, Car.STOP, Car::getArriveAt, Car::getId, Car::markWaiting, maxOutside,
                Car::markGaveUp);
    }

    /** Called by a producer thread: the car starts driving toward this entrance. */
    public void startDriving(Car car) {
        startApproaching(car);
    }

    public int drivingSize() {
        return approachingSize();
    }

    /** Cars in the lane, front of the line first. */
    public List<Car> laneCars(int max) {
        return laneItems(max);
    }

    /** Cars waiting on the street, first to arrive first. */
    public List<Car> outsideCars(int max) {
        return outsideItems(max);
    }

    /** Cars still driving toward the line, the ones nearest to arriving first. */
    public List<Car> drivingCars(int max) {
        return approachingItems(max);
    }
}
