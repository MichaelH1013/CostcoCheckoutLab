package runner;

import common.ArrivalLineTest;
import common.SimClockTest;
import event.EventTest;
import experiments.ExperimentsTest;
import parking.ParkingTest;
import stadium.StadiumTest;
import costco.CostcoTest;
/**
 * Runs every test and sets the exit code, so it works in a terminal, in IntelliJ and in the
 * automatic build on GitHub. Run it from the project folder:
 *
 *   javac -d out src/*&#47;*.java test/*&#47;*.java
 *   java -cp out runner.RunAllTests
 */
public class RunAllTests {

    public static void main(String[] args) {
        long start = System.nanoTime();
        ArrivalLineTest.run();
        ExperimentsTest.run();
        SimClockTest.run();
        StadiumTest.run();
        ParkingTest.run();
        EventTest.run();
        CostcoTest.run();
        System.out.printf("%nTime taken: %.0f seconds%n", (System.nanoTime() - start) / 1e9);
        System.exit(Check.finish());
    }
}
