package parking;

/**
 * How cars choose a parking lot.
 */
public enum LotStrategy {

    ASSIGNED_LOT("Assigned lot (nearer lots busier)"),
    SHORTEST_LINE("Shortest line (skips full lots)"),
    MOST_FREE_SPACES("Most free spaces (skips full lots)");

    private final String label;

    LotStrategy(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
