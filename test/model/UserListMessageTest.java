package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for UserListMessage.
 */
public class UserListMessageTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;

    public static void main(String[] args) {
        System.out.println("=== UserListMessage Tests ===");

        testValidFullConstructor();
        testConvenienceTimestamp();
        testNullCollectionRejection();
        testEmptyCollectionRejection();
        testNullUsernameRejection();
        testBlankUsernameRejection();
        testUsernameNewlineRejection();
        testUsernameCarriageReturnRejection();
        testMaxUsernameLengthAcceptance();
        testOverMaxUsernameRejection();
        testDuplicateFiltering();
        testDisplayCasingPreserved();
        testOrderPreserved();
        testMaxUsersAccepted();
        testOverMaxUsersRejected();
        testNullTimestampRejection();
        testImmutableSnapshot();
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

    private static void testValidFullConstructor() {
        try {
            LocalDateTime ts = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            UserListMessage m = new UserListMessage(Arrays.asList("Alice", "Bob"), ts);
            if (!m.getUsernames().equals(Arrays.asList("Alice", "Bob"))) {
                fail("valid user-list message");
                return;
            }
            if (!m.getTimestamp().equals(ts)) {
                fail("valid user-list message");
                return;
            }
            pass("valid user-list message");
        } catch (Exception e) {
            fail("valid user-list message");
        }
    }

    private static void testConvenienceTimestamp() {
        try {
            LocalDateTime before = LocalDateTime.now().minusSeconds(5);
            UserListMessage m = new UserListMessage(Arrays.asList("Alice"));
            LocalDateTime after = LocalDateTime.now().plusSeconds(5);
            if (m.getTimestamp() == null || m.getTimestamp().isBefore(before)
                    || m.getTimestamp().isAfter(after)) {
                fail("convenience timestamp");
                return;
            }
            pass("convenience timestamp");
        } catch (Exception e) {
            fail("convenience timestamp");
        }
    }

    private static void testNullCollectionRejection() {
        try {
            new UserListMessage(null, LocalDateTime.now());
            fail("invalid collection rejection");
        } catch (IllegalArgumentException e) {
            pass("invalid collection rejection");
        } catch (Exception e) {
            fail("invalid collection rejection");
        }
    }

    private static void testEmptyCollectionRejection() {
        try {
            new UserListMessage(new ArrayList<String>(), LocalDateTime.now());
            fail("invalid collection rejection");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: invalid collection rejection");
        } catch (Exception e) {
            fail("invalid collection rejection");
        }
    }

    private static void testNullUsernameRejection() {
        try {
            new UserListMessage(Arrays.asList("Alice", null), LocalDateTime.now());
            fail("invalid username rejection");
        } catch (IllegalArgumentException e) {
            pass("invalid username rejection");
        } catch (Exception e) {
            fail("invalid username rejection");
        }
    }

    private static void testBlankUsernameRejection() {
        try {
            new UserListMessage(Arrays.asList("Alice", "   "), LocalDateTime.now());
            fail("invalid username rejection");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: invalid username rejection");
        } catch (Exception e) {
            fail("invalid username rejection");
        }
    }

    private static void testUsernameNewlineRejection() {
        try {
            new UserListMessage(Arrays.asList("Bo\nb"), LocalDateTime.now());
            fail("username line-break rejection");
        } catch (IllegalArgumentException e) {
            pass("username line-break rejection");
        } catch (Exception e) {
            fail("username line-break rejection");
        }
    }

    private static void testUsernameCarriageReturnRejection() {
        try {
            new UserListMessage(Arrays.asList("Bo\rb"), LocalDateTime.now());
            fail("username line-break rejection");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: username line-break rejection");
        } catch (Exception e) {
            fail("username line-break rejection");
        }
    }

    private static void testMaxUsernameLengthAcceptance() {
        try {
            UserListMessage m = new UserListMessage(
                Arrays.asList(repeat('a', Message.MAX_SENDER_LENGTH)), LocalDateTime.now());
            if (m.getUsernames().size() != 1) {
                fail("username length validation");
                return;
            }
            pass("username length validation");
        } catch (Exception e) {
            fail("username length validation");
        }
    }

    private static void testOverMaxUsernameRejection() {
        try {
            new UserListMessage(
                Arrays.asList(repeat('a', Message.MAX_SENDER_LENGTH + 1)), LocalDateTime.now());
            fail("username length validation");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: username length validation");
        } catch (Exception e) {
            fail("username length validation");
        }
    }

    private static void testDuplicateFiltering() {
        try {
            UserListMessage m = new UserListMessage(
                Arrays.asList("Alice", "alice", "BOB", "Bob", "Charlie"), LocalDateTime.now());
            if (m.getUsernames().size() != 3) {
                fail("duplicate filtering");
                return;
            }
            pass("duplicate filtering");
        } catch (Exception e) {
            fail("duplicate filtering");
        }
    }

    private static void testDisplayCasingPreserved() {
        try {
            UserListMessage m = new UserListMessage(
                Arrays.asList("alice", "ALICE", "bOb"), LocalDateTime.now());
            if (!m.getUsernames().equals(Arrays.asList("alice", "bOb"))) {
                fail("display casing preserved");
                return;
            }
            pass("display casing preserved");
        } catch (Exception e) {
            fail("display casing preserved");
        }
    }

    private static void testOrderPreserved() {
        try {
            UserListMessage m = new UserListMessage(
                Arrays.asList("Charlie", "Alice", "Bob"), LocalDateTime.now());
            if (!m.getUsernames().equals(Arrays.asList("Charlie", "Alice", "Bob"))) {
                fail("ordering preserved");
                return;
            }
            pass("ordering preserved");
        } catch (Exception e) {
            fail("ordering preserved");
        }
    }

    private static void testMaxUsersAccepted() {
        try {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < UserListMessage.MAX_USERS; i++) {
                names.add("User" + i);
            }
            UserListMessage m = new UserListMessage(names, LocalDateTime.now());
            if (m.getUsernames().size() != UserListMessage.MAX_USERS) {
                fail("maximum user count");
                return;
            }
            pass("maximum user count");
        } catch (Exception e) {
            fail("maximum user count");
        }
    }

    private static void testOverMaxUsersRejected() {
        try {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < UserListMessage.MAX_USERS + 1; i++) {
                names.add("User" + i);
            }
            new UserListMessage(names, LocalDateTime.now());
            fail("maximum user count");
        } catch (IllegalArgumentException e) {
            testsPassed++;
            System.out.println("PASS: maximum user count");
        } catch (Exception e) {
            fail("maximum user count");
        }
    }

    private static void testNullTimestampRejection() {
        try {
            new UserListMessage(Arrays.asList("Alice"), null);
            fail("timestamp rejection");
        } catch (IllegalArgumentException e) {
            pass("timestamp rejection");
        } catch (Exception e) {
            fail("timestamp rejection");
        }
    }

    private static void testImmutableSnapshot() {
        try {
            List<String> input = new ArrayList<>(Arrays.asList("Alice", "Bob"));
            UserListMessage m = new UserListMessage(input, LocalDateTime.now());
            input.add("Mallory");
            if (m.getUsernames().size() != 2) {
                fail("immutable snapshot");
                return;
            }
            try {
                m.getUsernames().add("Mallory");
                fail("immutable snapshot");
                return;
            } catch (UnsupportedOperationException expected) {
            }
            pass("immutable snapshot");
        } catch (Exception e) {
            fail("immutable snapshot");
        }
    }

    private static void testEqualsHashCode() {
        try {
            LocalDateTime ts = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            UserListMessage a = new UserListMessage(Arrays.asList("Alice", "Bob"), ts);
            UserListMessage b = new UserListMessage(Arrays.asList("Alice", "Bob"), ts);
            UserListMessage c = new UserListMessage(Arrays.asList("Alice", "Bob", "Cid"), ts);
            if (!a.equals(b) || a.hashCode() != b.hashCode()) {
                fail("equality contract");
                return;
            }
            if (a.equals(c) || a.equals(null) || a.equals("Alice")) {
                fail("equality contract");
                return;
            }
            if (!a.toString().contains("Alice")) {
                fail("equality contract");
                return;
            }
            pass("equality contract");
        } catch (Exception e) {
            fail("equality contract");
        }
    }
}
