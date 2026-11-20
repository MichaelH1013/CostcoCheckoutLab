package parking;

/**
 * How the parking is laid out around the venue. This only changes the picture, not the
 * simulation: in both layouts the lots are numbered from the one nearest the stadium.
 */
public enum ParkingLayout {

    SURROUNDING("Lots all around the stadium (MetLife)"),
    BEHIND_VENUE("One big lot behind the venue (Jones Beach)");

    private final String label;

    ParkingLayout(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
