package stadium;

/**
 * Every setting of a stadium simulation. Changing the size of the event means changing these
 * numbers only, no code.
 *
 * @param gates            number of entry points
 * @param scannersPerGate  scanner threads at each gate
 * @param fans             total fans who will arrive
 * @param arrivalMinutes   simulated minutes over which the fans arrive
 * @param serviceSeconds   average simulated seconds to scan one fan (each fan takes 0.5x to 1.5x of it)
 * @param queueCapacity    fans that fit in one lane (a shared line holds this times the gates)
 * @param strategy         how fans choose a line
 * @param shape            when during the window fans arrive
 * @param producerThreads  threads that create fans and walk them to the gates
 * @param walkSeconds      simulated seconds a fan walks from the approach to the gate
 * @param gateBias         0 means every gate is equally popular, 1 means the first gate draws twice as many fans as the last
 * @param speed            simulated seconds per real second
 * @param seed             random seed
 */
public record StadiumConfig(
        int gates,
        int scannersPerGate,
        int fans,
        double arrivalMinutes,
        double serviceSeconds,
        int queueCapacity,
        QueueStrategy strategy,
        ArrivalShape shape,
        int producerThreads,
        double walkSeconds,
        double gateBias,
        double speed,
        long seed) {

    public static final double WAGE_PER_HOUR = 18.0;
    public static final int MAX_SCANNER_THREADS = 2000;

    public StadiumConfig {
        check(gates >= 1 && gates <= 200, "Gates must be from 1 to 200.");
        check(scannersPerGate >= 1 && scannersPerGate <= 100, "Scanners per gate must be from 1 to 100.");
        check((long) gates * scannersPerGate <= MAX_SCANNER_THREADS,
                "Gates times scanners per gate can be at most " + MAX_SCANNER_THREADS + ".");
        check(fans >= 1 && fans <= 1_000_000, "Fans must be from 1 to 1,000,000.");
        check(arrivalMinutes > 0 && arrivalMinutes <= 1000, "Arrival window must be more than 0 and at most 1000 minutes.");
        check(serviceSeconds > 0 && serviceSeconds <= 600, "Service time must be more than 0 and at most 600 seconds.");
        check(queueCapacity >= 1 && queueCapacity <= 10_000, "Queue capacity must be from 1 to 10,000.");
        check(strategy != null, "Pick a queue strategy.");
        check(shape != null, "Pick an arrival pattern.");
        check(producerThreads >= 1 && producerThreads <= 32, "Producer threads must be from 1 to 32.");
        check(walkSeconds >= 0 && walkSeconds <= 3600, "Walk time must be from 0 to 3600 seconds.");
        check(gateBias >= 0 && gateBias <= 10, "Gate bias must be from 0 to 10.");
        check(speed >= 0.1 && speed <= 10_000, "Speed must be from 0.1 to 10,000.");
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    public int totalScanners() {
        return gates * scannersPerGate;
    }

    public double arrivalSeconds() {
        return arrivalMinutes * 60;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .gates(gates).scannersPerGate(scannersPerGate).fans(fans)
                .arrivalMinutes(arrivalMinutes).serviceSeconds(serviceSeconds)
                .queueCapacity(queueCapacity).strategy(strategy).shape(shape)
                .producerThreads(producerThreads).walkSeconds(walkSeconds)
                .gateBias(gateBias).speed(speed).seed(seed);
    }

    /** Starts from the football-game numbers, so a caller only sets what differs. */
    public static final class Builder {
        private int gates = 8;
        private int scannersPerGate = 2;
        private int fans = 4000;
        private double arrivalMinutes = 60;
        private double serviceSeconds = 7;
        private int queueCapacity = 40;
        private QueueStrategy strategy = QueueStrategy.ASSIGNED_GATE;
        private ArrivalShape shape = ArrivalShape.SURGE;
        private int producerThreads = 2;
        private double walkSeconds = 45;
        private double gateBias = 1.0;
        private double speed = 120;
        private long seed = 311;

        public Builder gates(int value) {
            gates = value;
            return this;
        }

        public Builder scannersPerGate(int value) {
            scannersPerGate = value;
            return this;
        }

        public Builder fans(int value) {
            fans = value;
            return this;
        }

        public Builder arrivalMinutes(double value) {
            arrivalMinutes = value;
            return this;
        }

        public Builder serviceSeconds(double value) {
            serviceSeconds = value;
            return this;
        }

        public Builder queueCapacity(int value) {
            queueCapacity = value;
            return this;
        }

        public Builder strategy(QueueStrategy value) {
            strategy = value;
            return this;
        }

        public Builder shape(ArrivalShape value) {
            shape = value;
            return this;
        }

        public Builder producerThreads(int value) {
            producerThreads = value;
            return this;
        }

        public Builder walkSeconds(double value) {
            walkSeconds = value;
            return this;
        }

        public Builder gateBias(double value) {
            gateBias = value;
            return this;
        }

        public Builder speed(double value) {
            speed = value;
            return this;
        }

        public Builder seed(long value) {
            seed = value;
            return this;
        }

        public StadiumConfig build() {
            return new StadiumConfig(gates, scannersPerGate, fans, arrivalMinutes, serviceSeconds,
                    queueCapacity, strategy, shape, producerThreads, walkSeconds, gateBias, speed, seed);
        }
    }
}
