package experiments;

/**
 * Textbook formulas for the average time spent waiting in a queue, used to check that the
 * simulation gives the right answer for cases where the answer is known.
 *
 * Every formula is for a first come, first served line with random (Poisson) arrivals and c
 * servers. Times are in the same unit as the arguments, for example seconds.
 */
public final class QueueingTheory {

    private QueueingTheory() {
    }

    /**
     * The Erlang C formula: the chance that an arriving customer has to wait, when c servers share
     * one line and the offered load is a = (arrival rate) x (mean service time). It needs a < c.
     */
    public static double erlangC(int c, double a) {
        if (c < 1 || a < 0 || a >= c) {
            throw new IllegalArgumentException("Erlang C needs c >= 1 and 0 <= a < c, but c=" + c + " and a=" + a);
        }
        double rho = a / c;
        double term = 1;             // a^k / k!
        double sum = 0;
        for (int k = 0; k < c; k++) {
            sum += term;
            term = term * a / (k + 1);
        }
        double waiting = term / (1 - rho);   // a^c / c! / (1 - rho)
        return waiting / (sum + waiting);
    }

    /**
     * The average wait in the line for a line with c servers, Poisson arrivals and any service
     * time distribution, using the Allen-Cunneen approximation:
     *
     *   wait = ErlangC(c, a) / (c / meanService - arrivalRate) x (1 + cv2) / 2
     *
     * where cv2 is the squared coefficient of variation of the service time (variance divided by
     * the mean squared). With exponential service cv2 is 1 and this is the exact M/M/c answer.
     * With one server it is exact for any service distribution (the Pollaczek-Khinchine formula).
     */
    public static double meanWait(int c, double arrivalRate, double meanService, double cv2) {
        double a = arrivalRate * meanService;
        double chanceOfWaiting = erlangC(c, a);
        return chanceOfWaiting / (c / meanService - arrivalRate) * (1 + cv2) / 2;
    }

    /**
     * The exact Pollaczek-Khinchine average wait for one server, from the first two moments of the
     * service time: meanService = E[S] and secondMoment = E[S squared].
     */
    public static double pollaczekKhinchine(double arrivalRate, double meanService, double secondMoment) {
        double rho = arrivalRate * meanService;
        if (rho >= 1) {
            throw new IllegalArgumentException("The line grows without limit when the load is 1 or more.");
        }
        return arrivalRate * secondMoment / (2 * (1 - rho));
    }
}
