package model;

import java.time.LocalDateTime;

/**
 * Unit tests for SystemMessage.
 */
public class SystemMessageTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;

    public static void main(String[] args) {
        System.out.println("=== SystemMessage Tests ===");

        testEveryValidEventType();
        testConvenienceTimestamp();
        testNullEventTypeRejection();
        testNullTextRejection();
        testBlankTextRejection();
        testNewlineRejection();
        testCarriageReturnRejection();
        testMaxTextAcceptance();
        testOverMaxTextRejection();
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

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static void testEveryValidEventType() {
        try {
            LocalDateTime ts = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            for (SystemMessage.EventType type : SystemMessage.EventType.values()) {
                SystemMessage m = new SystemMessage(type, "some event text", ts);
                if (m.getEventType() != type || !m.getText().equals("some event text")
                        || !m.getTimestamp().equals(ts)) {
                    fail("valid system message");
                    return;
                }
            }
            if (SystemMessage.EventType.values().length != 4) {
                fail("valid system message");
                return;
            }
            pass("valid system message");
        } catch (Exception e) {
            fail("valid system message");
        }
    }

    private static void testConvenienceTimestamp() {
        try {
            LocalDateTime before = LocalDateTime.now().minusSeconds(5);
            SystemMessage m = new SystemMessage(SystemMessage.EventType.USER_JOINED, "Bob joined the chat.");
            LocalDateTime after = LocalDateTime.now().plusSeconds(5);
            if (m.getTimestamp() == null || m.getTimestamp().isBefore(before) || m.getTimestamp().isAfter(after)) {
                fail("convenience timestamp");
                return;
            }
            pass("convenience timestamp");
        } catch (Exception e) {
            fail("convenience timestamp");
        }
    }

    private static void testNullEventTypeRejection() {
        try {
            new SystemMessage(null, "text", LocalDateTime.now());
            fail("event type validation");
        } catch (IllegalArgumentException e) {
            pass("event type validation");
        } catch (Exception e) {
            fail("event type validation");
        }
    }

    private static void testNullTextRejection() {
        try {
            new SystemMessage(SystemMessage.EventType.USER_JOINED, null, LocalDateTime.now());
            fail("invalid text rejection");
        } catch (IllegalArgumentException e) {
            pass("invalid text rejection");
        } catch (Exception e) {
            fail("invalid text rejection");
        }
    }

    private static void testBlankTextRejection() {
        try {
            new SystemMessage(SystemMessage.EventType.USER_JOINED, "   ", LocalDateTime.now());
            fail("invalid text rejection");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: invalid text rejection");
        } catch (Exception e) {
            fail("invalid text rejection");
        }
    }

    private static void testNewlineRejection() {
        try {
            new SystemMessage(SystemMessage.EventType.USER_JOINED, "line1\nline2", LocalDateTime.now());
            fail("text line-break rejection");
        } catch (IllegalArgumentException e) {
            pass("text line-break rejection");
        } catch (Exception e) {
            fail("text line-break rejection");
        }
    }

    private static void testCarriageReturnRejection() {
        try {
            new SystemMessage(SystemMessage.EventType.USER_JOINED, "line1\rline2", LocalDateTime.now());
            fail("text line-break rejection");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: text line-break rejection");
        } catch (Exception e) {
            fail("text line-break rejection");
        }
    }

    private static void testMaxTextAcceptance() {
        try {
            String max = repeat('x', SystemMessage.MAX_TEXT_LENGTH);
            SystemMessage m = new SystemMessage(SystemMessage.EventType.USER_JOINED, max, LocalDateTime.now());
            if (!m.getText().equals(max)) {
                fail("text length validation");
                return;
            }
            pass("text length validation");
        } catch (Exception e) {
            fail("text length validation");
        }
    }

    private static void testOverMaxTextRejection() {
        try {
            String over = repeat('x', SystemMessage.MAX_TEXT_LENGTH + 1);
            new SystemMessage(SystemMessage.EventType.USER_JOINED, over, LocalDateTime.now());
            fail("text length validation");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: text length validation");
        } catch (Exception e) {
            fail("text length validation");
        }
    }

    private static void testNullTimestampRejection() {
        try {
            new SystemMessage(SystemMessage.EventType.USER_JOINED, "text", null);
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
            SystemMessage a = new SystemMessage(SystemMessage.EventType.USER_JOINED, "Bob joined the chat.", ts);
            SystemMessage b = new SystemMessage(SystemMessage.EventType.USER_JOINED, "Bob joined the chat.", ts);
            SystemMessage c = new SystemMessage(SystemMessage.EventType.USER_LEFT, "Bob joined the chat.", ts);
            if (!a.equals(b) || a.hashCode() != b.hashCode()) {
                fail("equality contract");
                return;
            }
            if (a.equals(c) || a.equals(null) || a.equals("text")) {
                fail("equality contract");
                return;
            }
            if (!a.toString().contains("USER_JOINED")) {
                fail("equality contract");
                return;
            }
            pass("equality contract");
        } catch (Exception e) {
            fail("equality contract");
        }
    }
}
