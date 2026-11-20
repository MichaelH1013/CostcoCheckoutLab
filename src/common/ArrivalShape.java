package common;

/**
 * How fans are spread over the arrival window. fractionArrived(u) is the share of all fans who
 * have shown up once u (0 to 1) of the window has passed.
 */
public enum ArrivalShape {

    UNIFORM("Steady stream"),
    SURGE("Surge before the start"),
    LATE_RUSH("Late rush");

    private final String label;

    ArrivalShape(String label) {
        this.label = label;
    }

    public double fractionArrived(double u) {
        double x = Math.max(0, Math.min(1, u));
        return switch (this) {
            case UNIFORM -> x;
            case SURGE -> 4 * x * x * x - 3 * x * x * x * x;
            case LATE_RUSH -> x * x * x * x;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
