package stadium;

/**
 * How fans choose where to line up.
 */
public enum QueueStrategy {

    ASSIGNED_GATE("Assigned gate (own line per gate)"),
    SHORTEST_LINE("Shortest line (own line per gate)"),
    SHARED_QUEUE("One shared line for every gate");

    private final String label;

    QueueStrategy(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
