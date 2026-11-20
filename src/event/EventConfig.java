package event;

import parking.ParkingConfig;
import stadium.StadiumConfig;

/**
 * The settings of a whole event: the parking lots, the stadium gates, and how many fans are in
 * each car. Every car that parks lets its fans out, and they walk from the lot to the stadium
 * and join the line at a gate. Cars that are turned away bring no fans.
 *
 * Use EventConfig.of so the two simulations agree on the speed and the number of fans.
 *
 * @param parking     the parking lots. Its speed is the speed of the whole event.
 * @param stadium     the stadium gates. Its fans, arrival window and arrival pattern are not used,
 *                    because the fans arrive from the parking lots.
 * @param fansPerCar  fans in each car
 */
public record EventConfig(ParkingConfig parking, StadiumConfig stadium, int fansPerCar) {

    public static final int MAX_FANS_PER_CAR = 8;

    public EventConfig {
        if (fansPerCar < 1 || fansPerCar > MAX_FANS_PER_CAR) {
            throw new IllegalArgumentException("Fans per car must be from 1 to " + MAX_FANS_PER_CAR + ".");
        }
        if ((long) parking.cars() * fansPerCar > 1_000_000) {
            throw new IllegalArgumentException("Cars times fans per car can be at most 1,000,000.");
        }
    }

    /** Builds the config, copying the parking speed to the stadium and sizing the stadium for every car parking. */
    public static EventConfig of(ParkingConfig parking, StadiumConfig stadiumSettings, int fansPerCar) {
        long fans = Math.max(1, Math.min(1_000_000L, (long) parking.cars() * fansPerCar));
        StadiumConfig stadium = stadiumSettings.toBuilder().fans((int) fans).speed(parking.speed()).build();
        return new EventConfig(parking, stadium, fansPerCar);
    }

    public int totalStaff() {
        return parking.totalAttendants() + stadium.totalScanners();
    }
}
