package event;

import parking.ParkingPreset;
import stadium.StadiumPreset;

/**
 * Ready-made events that pair a parking preset with a stadium preset.
 */
public enum EventPreset {

    DEMO("Small demo (150 cars, 3 gates)", ParkingPreset.DEMO, StadiumPreset.DEMO, 2),

    FOOTBALL("Football game (1,700 cars, 5,100 fans)", ParkingPreset.FOOTBALL, StadiumPreset.FOOTBALL, 3),

    CONCERT("Concert (1,000 cars, 3,000 fans)", ParkingPreset.CONCERT, StadiumPreset.CONCERT, 3),

    STRESS("Stress test (50,000 cars, 100,000 fans)", ParkingPreset.STRESS, StadiumPreset.STRESS, 2);

    private final String label;
    private final EventConfig config;

    EventPreset(String label, ParkingPreset parking, StadiumPreset stadium, int fansPerCar) {
        this.label = label;
        this.config = EventConfig.of(parking.config(), stadium.config(), fansPerCar);
    }

    public EventConfig config() {
        return config;
    }

    @Override
    public String toString() {
        return label;
    }
}
