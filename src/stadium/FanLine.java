package stadium;

import common.ArrivalLine;

import java.util.List;

/**
 * The line at one gate: the bounded buffer between fans (producers) and gate scanners
 * (consumers). All of the synchronization lives in the generic ArrivalLine. This class only says
 * what a fan is and keeps the names the stadium code uses: a fan walks to the line.
 */
public final class FanLine extends ArrivalLine<Fan> {

    public FanLine(String name, int capacity) {
        this(name, capacity, 0);
    }

    /** maxOutside is the most fans allowed to wait outside a full lane, or 0 for no limit. */
    public FanLine(String name, int capacity, int maxOutside) {
        super(name, capacity, Fan.STOP, Fan::getArriveAt, Fan::getId, Fan::markWaiting, maxOutside,
                Fan::markGaveUp);
    }

    /** Called by a producer thread: the fan starts walking toward this line. */
    public void startWalking(Fan fan) {
        startApproaching(fan);
    }

    public int walkingSize() {
        return approachingSize();
    }

    /** Fans in the lane, front of the line first. */
    public List<Fan> laneFans(int max) {
        return laneItems(max);
    }

    /** Fans waiting outside the lane, first to arrive first. */
    public List<Fan> outsideFans(int max) {
        return outsideItems(max);
    }

    /** Fans still walking toward the line, the ones nearest to arriving first. */
    public List<Fan> walkingFans(int max) {
        return approachingItems(max);
    }
}
