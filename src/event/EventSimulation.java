package event;

import common.SimClock;
import parking.Car;
import parking.Lot;
import parking.ParkingSimulation;
import stadium.StadiumSimulation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A whole event: cars park, then their fans walk to the stadium and go in through the gates.
 * It runs a ParkingSimulation and a StadiumSimulation on one shared clock and connects them:
 *
 *   parking lot -- car parks --> bridge -- fans arrive after the walk --> stadium gate lines
 *
 * Each time an attendant parks a car, the car's fans are put in a queue ordered by the time they
 * reach the stadium (the time the car parked plus the walk from that lot). The bridge, a single
 * thread, watches the clock and hands each group to the stadium when its time comes. When the
 * parking is finished and the last group has been handed over, the bridge tells the stadium that
 * no more fans are coming. The stadium finishes when the last fan is inside.
 *
 * Pausing, resuming and changing the speed act on the shared clock, so both halves stay in step.
 */
public final class EventSimulation {

    public enum State { NEW, RUNNING, PAUSED, FINISHED, STOPPED }

    /** Fans who parked and are walking to the stadium. They arrive at releaseAt (simulated seconds). */
    private record Group(double releaseAt, int fans) implements Comparable<Group> {
        @Override
        public int compareTo(Group other) {
            return Double.compare(releaseAt, other.releaseAt);
        }
    }

    private final EventConfig config;
    private final SimClock clock;
    private final ParkingSimulation parking;
    private final StadiumSimulation stadium;
    private final PriorityBlockingQueue<Group> walking = new PriorityBlockingQueue<>();
    private final AtomicInteger fansWalking = new AtomicInteger();
    private final ScheduledThreadPoolExecutor bridgePool;
    private final CountDownLatch finished = new CountDownLatch(1);

    private volatile State state = State.NEW;

    public EventSimulation(EventConfig config) {
        this.config = config;
        this.clock = new SimClock(config.parking().speed());
        this.parking = ParkingSimulation.withSharedClock(config.parking(), clock);
        this.stadium = StadiumSimulation.withOutsideFans(config.stadium(), clock);
        parking.setParkedListener(this::carParked);
        stadium.setFinishListener(this::stadiumFinished);
        bridgePool = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "bridge");
            thread.setDaemon(true);
            return thread;
        });
    }

    public EventConfig getConfig() {
        return config;
    }

    public SimClock getClock() {
        return clock;
    }

    public ParkingSimulation getParking() {
        return parking;
    }

    public StadiumSimulation getStadium() {
        return stadium;
    }

    public State getState() {
        return state;
    }

    /** Fans who parked and are still walking from their lot to the stadium. */
    public int getFansWalking() {
        return fansWalking.get();
    }

    /** Simulated seconds until the last fan was inside, or NaN while the event is not finished. */
    public double getFinishedAt() {
        return stadium.getFinishedAt();
    }

    public synchronized void start() {
        if (state != State.NEW) {
            throw new IllegalStateException("Already started.");
        }
        state = State.RUNNING;
        parking.start();
        stadium.start();
        bridgePool.schedule(new Bridge(), 0, TimeUnit.MILLISECONDS);
    }

    public synchronized void pause() {
        if (state == State.RUNNING) {
            parking.pause();
            stadium.pause();
            state = State.PAUSED;
        }
    }

    public synchronized void resume() {
        if (state == State.PAUSED) {
            parking.resume();
            stadium.resume();
            state = State.RUNNING;
        }
    }

    public void setSpeed(double speed) {
        clock.setSpeed(speed);
    }

    /** Ends the event early. */
    public synchronized void stop() {
        if (state == State.FINISHED || state == State.STOPPED) {
            return;
        }
        state = State.STOPPED;
        bridgePool.shutdownNow();
        parking.stop();
        stadium.stop();
        clock.pause();
        finished.countDown();
    }

    /** Waits until the event has finished or been stopped. The shared clock is already paused when this returns. */
    public boolean awaitFinished(long millis) throws InterruptedException {
        return finished.await(millis, TimeUnit.MILLISECONDS);
    }

    public boolean isFinished() {
        return state == State.FINISHED;
    }

    /** Runs on an attendant thread each time a car parks. */
    private void carParked(Car car) {
        Lot lot = parking.getLots().get(car.getServedLot());
        int fans = config.fansPerCar();
        stadium.addExpectedFans(fans);
        fansWalking.addAndGet(fans);
        walking.add(new Group(car.getFinishedAt() + lot.getWalkSeconds(), fans));
    }

    private synchronized void stadiumFinished() {
        if (state == State.STOPPED) {
            return;
        }
        clock.pause();
        state = State.FINISHED;
        bridgePool.shutdown();
        finished.countDown();
    }

    /** Hands fans on to the stadium as their walk from the lot ends. */
    private final class Bridge implements Runnable {

        @Override
        public void run() {
            try {
                double now = clock.now();
                Group group;
                while ((group = walking.peek()) != null && group.releaseAt() <= now) {
                    walking.poll();
                    stadium.injectFans(group.fans());
                    fansWalking.addAndGet(-group.fans());
                }
                if (parking.isFinished() && walking.isEmpty()) {
                    stadium.closeArrivals();
                    return;
                }
                bridgePool.schedule(this, tickMillis(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // the event was stopped
            } catch (RuntimeException e) {
                e.printStackTrace();
                stop();
            }
        }

        private long tickMillis() {
            return Math.max(1, Math.min(20, Math.round(1000.0 / clock.getSpeed())));
        }
    }
}
