package stadium;

/**
 * Ready-made events. Each one only fills in the parameters of StadiumConfig.
 */
public enum StadiumPreset {

    DEMO("Small demo (150 fans)", StadiumConfig.builder()
            .gates(3).scannersPerGate(2).fans(150).arrivalMinutes(10).serviceSeconds(8)
            .queueCapacity(10).shape(ArrivalShape.SURGE).producerThreads(1).walkSeconds(20)
            .gateBias(0.5).speed(120).build()),

    FOOTBALL("Football game (4,000 fans)", StadiumConfig.builder().build()),

    CONCERT("Concert (3,000 fans, slower bag checks)", StadiumConfig.builder()
            .gates(6).scannersPerGate(3).fans(3000).arrivalMinutes(45).serviceSeconds(10)
            .queueCapacity(30).shape(ArrivalShape.LATE_RUSH).walkSeconds(60)
            .gateBias(0.5).speed(120).build()),

    STRESS("Stress test (50,000 fans, 40 gates)", StadiumConfig.builder()
            .gates(40).scannersPerGate(3).fans(50_000).arrivalMinutes(60).serviceSeconds(6)
            .queueCapacity(50).strategy(QueueStrategy.SHORTEST_LINE).shape(ArrivalShape.SURGE)
            .producerThreads(4).walkSeconds(45).gateBias(1.0).speed(600).build());

    private final String label;
    private final StadiumConfig config;

    StadiumPreset(String label, StadiumConfig config) {
        this.label = label;
        this.config = config;
    }

    public StadiumConfig config() {
        return config;
    }

    @Override
    public String toString() {
        return label;
    }
}
