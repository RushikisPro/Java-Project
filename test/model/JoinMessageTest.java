package model;

import java.time.LocalDateTime;

/**
 * Unit tests for JoinMessage.
 */
public class JoinMessageTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;

    public static void main(String[] args) {
        System.out.println("=== JoinMessage Tests ===");

        testValidFullConstructor();
        testConvenienceTimestamp();
        testNullSenderRejection();
        testBlankSenderRejection();
        testSenderNewlineRejection();
        testSenderCarriageReturnRejection();
        testMaxSenderLengthAcceptance();
        testOverMaxSenderRejection();
        testNullTimestampRejection();
        testEqualsHashCode();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void pass(String label) {
        testsPassed++;
        System.out.println("PASS: " + label);
    }

    private static void fail(String label) {
        testsFailed++;
        System.out.println("FAIL: " + label);
    }

    private static void testValidFullConstructor() {
        try {
            LocalDateTime ts = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            JoinMessage m = new JoinMessage("Bob", ts);
            if (!m.getSender().equals("Bob") || !m.getTimestamp().equals(ts)) {
                fail("valid join message");
                return;
            }
            // Trimming check
            JoinMessage trimmed = new JoinMessage("  Bob  ", ts);
            if (!trimmed.getSender().equals("Bob")) {
                fail("valid join message");
                return;
            }
            pass("valid join message");
        } catch (Exception e) {
            fail("valid join message");
        }
    }

    private static void testConvenienceTimestamp() {
        try {
            LocalDateTime before = LocalDateTime.now().minusSeconds(5);
            JoinMessage m = new JoinMessage("Bob");
            LocalDateTime after = LocalDateTime.now().plusSeconds(5);
            if (!m.getSender().equals("Bob")) {
                fail("convenience timestamp");
                return;
            }
            if (m.getTimestamp() == null || m.getTimestamp().isBefore(before) || m.getTimestamp().isAfter(after)) {
                fail("convenience timestamp");
                return;
            }
            pass("convenience timestamp");
        } catch (Exception e) {
            fail("convenience timestamp");
        }
    }

    private static void testNullSenderRejection() {
        try {
            new JoinMessage(null, LocalDateTime.now());
            fail("invalid sender rejection");
        } catch (IllegalArgumentException e) {
            pass("invalid sender rejection");
        } catch (Exception e) {
            fail("invalid sender rejection");
        }
    }

    private static void testBlankSenderRejection() {
        try {
            new JoinMessage("   ", LocalDateTime.now());
            fail("invalid sender rejection");
        } catch (IllegalArgumentException e) {
            // second blank variant: empty string
            try {
                new JoinMessage("", LocalDateTime.now());
                fail("invalid sender rejection");
            } catch (IllegalArgumentException e2) {
                testsPassed++;
                System.out.println("PASS: invalid sender rejection");
            } catch (Exception e2) {
                fail("invalid sender rejection");
            }
        } catch (Exception e) {
            fail("invalid sender rejection");
        }
    }

    private static void testSenderNewlineRejection() {
        try {
            new JoinMessage("Bo\nb", LocalDateTime.now());
            fail("sender line-break rejection");
        } catch (IllegalArgumentException e) {
            pass("sender line-break rejection");
        } catch (Exception e) {
            fail("sender line-break rejection");
        }
    }

    private static void testSenderCarriageReturnRejection() {
        try {
            new JoinMessage("Bo\rb", LocalDateTime.now());
            fail("sender line-break rejection");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: sender line-break rejection");
        } catch (Exception e) {
            fail("sender line-break rejection");
        }
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static void testMaxSenderLengthAcceptance() {
        try {
            String max = repeat('a', Message.MAX_SENDER_LENGTH);
            JoinMessage m = new JoinMessage(max, LocalDateTime.now());
            if (!m.getSender().equals(max)) {
                fail("sender length validation");
                return;
            }
            pass("sender length validation");
        } catch (Exception e) {
            fail("sender length validation");
        }
    }

    private static void testOverMaxSenderRejection() {
        try {
            String over = repeat('a', Message.MAX_SENDER_LENGTH + 1);
            new JoinMessage(over, LocalDateTime.now());
            fail("sender length validation");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: sender length validation");
        } catch (Exception e) {
            fail("sender length validation");
        }
    }

    private static void testNullTimestampRejection() {
        try {
            new JoinMessage("Bob", null);
            fail("timestamp rejection");
        } catch (IllegalArgumentException e) {
            pass("timestamp rejection");
        } catch (Exception e) {
            fail("timestamp rejection");
        }
    }

    private static void testEqualsHashCode() {
        try {
            LocalDateTime ts = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            JoinMessage a = new JoinMessage("Bob", ts);
            JoinMessage b = new JoinMessage("Bob", ts);
            JoinMessage c = new JoinMessage("Alice", ts);
            if (!a.equals(b) || a.hashCode() != b.hashCode()) {
                fail("equality contract");
                return;
            }
            if (a.equals(c) || a.equals(null) || a.equals("Bob")) {
                fail("equality contract");
                return;
            }
            if (!a.toString().contains("Bob")) {
                fail("equality contract");
                return;
            }
            pass("equality contract");
        } catch (Exception e) {
            fail("equality contract");
        }
    }
}
