package experiments;

import runner.Check;

import java.util.List;

/**
 * Tests for the helpers the experiments use: the statistics and the queueing theory formulas. The
 * formulas are checked against answers that can be worked out by hand.
 */
public final class ExperimentsTest {

    public static void run() {
        System.out.println("Experiments helpers");

        Check.test("mean and sample standard deviation are right", () -> {
            Stats s = Stats.of(new double[] {1, 2, 3, 4});
            Check.near(2.5, s.mean(), 1e-12, "mean");
            Check.near(1.2909944487, s.stdev(), 1e-9, "standard deviation");
            Check.equal(1.0, s.min(), "min");
            Check.equal(4.0, s.max(), "max");
            Check.equal(4, s.n(), "n");
            Check.near(1.2909944487 / 2, s.standardError(), 1e-9, "standard error");
            Check.equal("2.5 ± 1.3", s.format(1), "formatted text");
        });

        Check.test("one run has no spread, and no runs give NaN", () -> {
            Stats one = Stats.of(List.of(7.0));
            Check.equal(7.0, one.mean(), "mean of one");
            Check.equal(0.0, one.stdev(), "spread of one");
            Check.that(Double.isNaN(Stats.of(new double[0]).mean()), "mean of nothing");
        });

        Check.test("Erlang C gives the known answers", () -> {
            Check.near(0.5, QueueingTheory.erlangC(1, 0.5), 1e-12, "one server at load 0.5");
            Check.near(1.0 / 3.0, QueueingTheory.erlangC(2, 1.0), 1e-12, "two servers, offered load 1");
            Check.near(0.0, QueueingTheory.erlangC(3, 0.0), 1e-12, "no load");
            Check.rejects("Erlang C needs", () -> QueueingTheory.erlangC(1, 1.0));
            Check.rejects("Erlang C needs", () -> QueueingTheory.erlangC(0, 0.5));
        });

        Check.test("the average wait matches M/M/1, M/D/1 and M/M/2 worked out by hand", () -> {
            // M/M/1 at load 0.5 with a mean service time of 1: wait = rho / (mu - lambda) = 1
            Check.near(1.0, QueueingTheory.meanWait(1, 0.5, 1, 1), 1e-12, "M/M/1");
            // M/D/1 (no variation in the service time) waits exactly half as long
            Check.near(0.5, QueueingTheory.meanWait(1, 0.5, 1, 0), 1e-12, "M/D/1");
            // M/M/2 with arrival rate 1 and mean service 1: wait = ErlangC / (c / E[S] - lambda) = (1/3) / 1
            Check.near(1.0 / 3.0, QueueingTheory.meanWait(2, 1, 1, 1), 1e-12, "M/M/2");
        });

        Check.test("Pollaczek-Khinchine agrees with the general formula for one server", () -> {
            // exponential service with mean 1 has E[S squared] = 2
            Check.near(1.0, QueueingTheory.pollaczekKhinchine(0.5, 1, 2), 1e-12, "exponential service");
            // service uniform on 0.5 to 1.5 has mean 1 and E[S squared] = 13/12, so cv^2 = 1/12
            double viaMoments = QueueingTheory.pollaczekKhinchine(0.7, 1, 13.0 / 12.0);
            double viaCv = QueueingTheory.meanWait(1, 0.7, 1, 1.0 / 12.0);
            Check.near(viaMoments, viaCv, 1e-12, "uniform service, two ways");
            Check.rejects("without limit", () -> QueueingTheory.pollaczekKhinchine(1.0, 1, 2));
        });
    }
}
