package parking;

import stadium.ArrivalShape;

/**
 * Every setting of a parking simulation. Changing the size of the event means changing these
 * numbers only, no code.
 *
 * @param lots             number of parking lots around the stadium
 * @param attendantsPerLot attendant threads at each lot entrance
 * @param cars             total cars that will arrive
 * @param arrivalMinutes   simulated minutes over which the cars arrive
 * @param payingSeconds    average simulated seconds to take payment or check a pass (each car takes 0.5x to 1.5x of it)
 * @param spacesPerLot     parking spaces in each lot
 * @param queueCapacity    cars that fit in one entrance lane. Extra cars wait outside it, on the street.
 * @param strategy         how cars choose a lot
 * @param shape            when during the window cars arrive
 * @param producerThreads  threads that create cars and drive them to the lots
 * @param driveSeconds     simulated seconds a car drives from the main road to the back of the entrance line
 * @param walkSeconds      simulated seconds to walk from the nearest lot to the stadium. The farthest lot is 3 times as far.
 * @param lotBias          0 means every lot is equally popular, 1 means the nearest lot draws twice as many cars as the farthest
 * @param speed            simulated seconds per real second
 * @param seed             random seed
 * @param layout           how the lots are drawn around the venue. It only changes the picture.
 */
public record ParkingConfig(
        int lots,
        int attendantsPerLot,
        int cars,
        double arrivalMinutes,
        double payingSeconds,
        int spacesPerLot,
        int queueCapacity,
        LotStrategy strategy,
        ArrivalShape shape,
        int producerThreads,
        double driveSeconds,
        double walkSeconds,
        double lotBias,
        double speed,
        long seed,
        ParkingLayout layout) {

    public static final double WAGE_PER_HOUR = 18.0;
    public static final double FEE_PER_CAR = 25.0;
    public static final int MAX_ATTENDANT_THREADS = 2000;

    public ParkingConfig {
        check(lots >= 1 && lots <= 100, "Lots must be from 1 to 100.");
        check(attendantsPerLot >= 1 && attendantsPerLot <= 50, "Attendants per lot must be from 1 to 50.");
        check((long) lots * attendantsPerLot <= MAX_ATTENDANT_THREADS,
                "Lots times attendants per lot can be at most " + MAX_ATTENDANT_THREADS + ".");
        check(cars >= 1 && cars <= 1_000_000, "Cars must be from 1 to 1,000,000.");
        check(arrivalMinutes > 0 && arrivalMinutes <= 1000, "Arrival window must be more than 0 and at most 1000 minutes.");
        check(payingSeconds > 0 && payingSeconds <= 600, "Payment time must be more than 0 and at most 600 seconds.");
        check(spacesPerLot >= 1 && spacesPerLot <= 100_000, "Spaces per lot must be from 1 to 100,000.");
        check(queueCapacity >= 1 && queueCapacity <= 10_000, "Queue capacity must be from 1 to 10,000.");
        check(strategy != null, "Pick a lot strategy.");
        check(shape != null, "Pick an arrival pattern.");
        check(producerThreads >= 1 && producerThreads <= 32, "Producer threads must be from 1 to 32.");
        check(driveSeconds >= 0 && driveSeconds <= 3600, "Drive time must be from 0 to 3600 seconds.");
        check(walkSeconds >= 0 && walkSeconds <= 3600, "Walk time must be from 0 to 3600 seconds.");
        check(lotBias >= 0 && lotBias <= 10, "Lot bias must be from 0 to 10.");
        check(speed >= 0.1 && speed <= 10_000, "Speed must be from 0.1 to 10,000.");
        check(layout != null, "Pick a lot layout.");
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    public int totalAttendants() {
        return lots * attendantsPerLot;
    }

    public long totalSpaces() {
        return (long) lots * spacesPerLot;
    }

    public double arrivalSeconds() {
        return arrivalMinutes * 60;
    }

    /** Walk time from lot number lotIndex (0 is the nearest) to the stadium. */
    public double walkSecondsFor(int lotIndex) {
        if (lots == 1) {
            return walkSeconds;
        }
        return walkSeconds * (1 + 2.0 * lotIndex / (lots - 1));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .lots(lots).attendantsPerLot(attendantsPerLot).cars(cars)
                .arrivalMinutes(arrivalMinutes).payingSeconds(payingSeconds)
                .spacesPerLot(spacesPerLot).queueCapacity(queueCapacity).strategy(strategy).shape(shape)
                .producerThreads(producerThreads).driveSeconds(driveSeconds).walkSeconds(walkSeconds)
                .lotBias(lotBias).speed(speed).seed(seed).layout(layout);
    }

    /** Starts from the football-game numbers, so a caller only sets what differs. */
    public static final class Builder {
        private int lots = 6;
        private int attendantsPerLot = 2;
        private int cars = 1700;
        private double arrivalMinutes = 90;
        private double payingSeconds = 15;
        private int spacesPerLot = 300;
        private int queueCapacity = 25;
        private LotStrategy strategy = LotStrategy.ASSIGNED_LOT;
        private ArrivalShape shape = ArrivalShape.SURGE;
        private int producerThreads = 2;
        private double driveSeconds = 40;
        private double walkSeconds = 120;
        private double lotBias = 1.0;
        private double speed = 120;
        private long seed = 311;
        private ParkingLayout layout = ParkingLayout.SURROUNDING;

        public Builder lots(int value) {
            lots = value;
            return this;
        }

        public Builder attendantsPerLot(int value) {
            attendantsPerLot = value;
            return this;
        }

        public Builder cars(int value) {
            cars = value;
            return this;
        }

        public Builder arrivalMinutes(double value) {
            arrivalMinutes = value;
            return this;
        }

        public Builder payingSeconds(double value) {
            payingSeconds = value;
            return this;
        }

        public Builder spacesPerLot(int value) {
            spacesPerLot = value;
            return this;
        }

        public Builder queueCapacity(int value) {
            queueCapacity = value;
            return this;
        }

        public Builder strategy(LotStrategy value) {
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

        public Builder driveSeconds(double value) {
            driveSeconds = value;
            return this;
        }

        public Builder walkSeconds(double value) {
            walkSeconds = value;
            return this;
        }

        public Builder lotBias(double value) {
            lotBias = value;
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

        public Builder layout(ParkingLayout value) {
            layout = value;
            return this;
        }

        public ParkingConfig build() {
            return new ParkingConfig(lots, attendantsPerLot, cars, arrivalMinutes, payingSeconds, spacesPerLot,
                    queueCapacity, strategy, shape, producerThreads, driveSeconds, walkSeconds, lotBias, speed, seed,
                    layout);
        }
    }
}
