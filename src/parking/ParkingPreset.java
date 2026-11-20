package parking;

import common.ArrivalShape;

/**
 * Ready-made events. Each one only fills in the parameters of ParkingConfig.
 */
public enum ParkingPreset {

    DEMO("Small demo (150 cars, lots fill up)", ParkingConfig.builder()
            .lots(3).attendantsPerLot(1).cars(150).arrivalMinutes(10).payingSeconds(10)
            .spacesPerLot(40).queueCapacity(8).shape(ArrivalShape.SURGE).producerThreads(1)
            .driveSeconds(15).walkSeconds(60).lotBias(0.5).speed(120).build()),

    FOOTBALL("Football game (1,700 cars, 6 lots)", ParkingConfig.builder().build()),

    CONCERT("Concert (1,000 cars, cash at the booth)", ParkingConfig.builder()
            .lots(4).attendantsPerLot(2).cars(1000).arrivalMinutes(45).payingSeconds(20)
            .spacesPerLot(250).queueCapacity(20).shape(ArrivalShape.LATE_RUSH).driveSeconds(40)
            .walkSeconds(150).lotBias(0.5).speed(120).layout(ParkingLayout.BEHIND_VENUE).build()),

    STRESS("Stress test (50,000 cars, 30 lots)", ParkingConfig.builder()
            .lots(30).attendantsPerLot(6).cars(50_000).arrivalMinutes(90).payingSeconds(12)
            .spacesPerLot(2000).queueCapacity(60).strategy(LotStrategy.MOST_FREE_SPACES)
            .shape(ArrivalShape.SURGE).producerThreads(4).driveSeconds(40).walkSeconds(120)
            .lotBias(1.0).speed(600).build());

    private final String label;
    private final ParkingConfig config;

    ParkingPreset(String label, ParkingConfig config) {
        this.label = label;
        this.config = config;
    }

    public ParkingConfig config() {
        return config;
    }

    @Override
    public String toString() {
        return label;
    }
}
