package runner;

import java.util.ArrayList;
import java.util.List;

/**
 * A very small test toolkit, so the tests run with nothing but the JDK (no Maven, Gradle or
 * JUnit download). A test is a name and a body. The body throws if something is wrong, and the
 * helpers below throw AssertionError with a clear message.
 *
 * To move to JUnit later, each Check.test(...) becomes a @Test method and each helper becomes the
 * matching assertion.
 */
public final class Check {

    /** The body of a test. It may throw any exception. */
    public interface Body {
        void run() throws Exception;
    }

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    private Check() {
    }

    /** Runs one test, prints PASS or FAIL, and keeps going if it fails. */
    public static void test(String name, Body body) {
        long start = System.nanoTime();
        try {
            body.run();
            passed++;
            System.out.printf("  PASS  %s (%.1f s)%n", name, (System.nanoTime() - start) / 1e9);
        } catch (AssertionError e) {
            failures.add(name);
            System.out.printf("  FAIL  %s%n          %s%n", name, e.getMessage());
        } catch (Throwable e) {
            failures.add(name);
            System.out.printf("  FAIL  %s%n          %s%n", name, e);
            e.printStackTrace(System.out);
        }
    }

    public static void that(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void equal(long expected, long actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    public static void equal(Object expected, Object actual, String what) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    public static void near(double expected, double actual, double tolerance, String what) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(what + ": expected " + expected + " (within " + tolerance + ") but was " + actual);
        }
    }

    /** Fails unless the code throws an IllegalArgumentException whose message contains the given text. */
    public static void rejects(String messagePart, Body body) {
        try {
            body.run();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() == null || !e.getMessage().contains(messagePart)) {
                throw new AssertionError("expected a message containing \"" + messagePart + "\" but was \"" + e.getMessage() + "\"");
            }
            return;
        } catch (Exception e) {
            throw new AssertionError("expected IllegalArgumentException but got " + e);
        }
        throw new AssertionError("expected IllegalArgumentException (" + messagePart + ") but nothing was thrown");
    }

    /**
     * Waits up to timeoutMs for every thread whose name starts with one of the prefixes to be gone.
     * This is how the tests check that a simulation shut down cleanly and left nothing running.
     */
    public static void noThreadsLeft(long timeoutMs, String... prefixes) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long left;
        do {
            left = Thread.getAllStackTraces().keySet().stream()
                    .filter(Thread::isAlive)
                    .filter(t -> {
                        for (String prefix : prefixes) {
                            if (t.getName().startsWith(prefix)) {
                                return true;
                            }
                        }
                        return false;
                    }).count();
            if (left == 0) {
                return;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError(left + " simulation thread(s) were still running after " + timeoutMs + " ms");
    }

    /** Prints the totals. Returns 0 if everything passed, 1 otherwise, for use as an exit code. */
    public static int finish() {
        System.out.println();
        System.out.printf("%d passed, %d failed%n", passed, failures.size());
        for (String name : failures) {
            System.out.println("  failed: " + name);
        }
        return failures.isEmpty() ? 0 : 1;
    }
}
