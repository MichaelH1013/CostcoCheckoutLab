package experiments;

import java.util.List;

/**
 * Mean and spread of a number measured in several runs. A single run of a simulation that uses
 * real threads can differ from the next one, so results are reported as mean ± standard
 * deviation over several runs instead of one lucky or unlucky number.
 *
 * @param mean   the average of the runs
 * @param stdev  the sample standard deviation (0 when there is only one run)
 * @param min    the smallest run
 * @param max    the largest run
 * @param n      the number of runs
 */
public record Stats(double mean, double stdev, double min, double max, int n) {

    public static Stats of(double[] values) {
        int n = values.length;
        if (n == 0) {
            return new Stats(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
        }
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double mean = sum / n;
        double squares = 0;
        for (double v : values) {
            squares += (v - mean) * (v - mean);
        }
        double stdev = n > 1 ? Math.sqrt(squares / (n - 1)) : 0;
        return new Stats(mean, stdev, min, max, n);
    }

    public static Stats of(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return of(array);
    }

    /** The standard error of the mean: how far the mean itself is likely to be from the true value. */
    public double standardError() {
        return n > 1 ? stdev / Math.sqrt(n) : Double.NaN;
    }

    /** For example "2.1 ± 0.3". The digits are the number of decimals. */
    public String format(int digits) {
        return String.format("%." + digits + "f ± %." + digits + "f", mean, stdev);
    }
}
