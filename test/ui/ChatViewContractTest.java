package ui;

import controller.FakeChatView;
import model.Message;
import model.SystemMessage;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Headless-safe ChatView contract tests using FakeChatView (no JFrame).
 * Mirrors the group presentation rules so the contract holds even where
 * ChatFrame cannot be instantiated.
 */
public class ChatViewContractTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;

    public static void main(String[] args) {
        System.out.println("=== ChatView Contract Tests ===");

        testGroupMessageContract();
        testTrustedSystemContract();
        testOnlineUsersContract();
        testRoomStatusContract();
        testRoomControlsContract();
        testLeaveButtonContract();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void testGroupMessageContract() {
        try {
            FakeChatView view = new FakeChatView();
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 0);
            view.appendGroupMessage(new Message("Alice", "Hello group", timestamp), "Alice");
            view.appendGroupMessage(new Message("Bob", "Hi Alice", timestamp), "Alice");

            List<String> lines = view.getGroupMessages();
            if (lines.size() != 2) {
                System.out.println("FAIL: group message contract - expected 2 lines");
                testsFailed++;
                return;
            }
            if (!lines.get(0).contains("You") || !lines.get(0).contains("Hello group")) {
                System.out.println("FAIL: group message contract - local not You");
                testsFailed++;
                return;
            }
            if (!lines.get(1).contains("Bob") || !lines.get(1).contains("Hi Alice")) {
                System.out.println("FAIL: group message contract - remote sender missing");
                testsFailed++;
                return;
            }
            System.out.println("PASS: group message contract");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL: group message contract - " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testTrustedSystemContract() {
        try {
            FakeChatView view = new FakeChatView();
            SystemMessage message = new SystemMessage(
                SystemMessage.EventType.USER_JOINED, "Bob joined the chat.",
                LocalDateTime.of(2026, 10, 3, 14, 32, 0));
            view.appendTrustedSystemMessage(message);

            List<SystemMessage> recorded = view.getTrustedSystemMessages();
            if (recorded.size() != 1) {
                System.out.println("FAIL: trusted system contract - not recorded");
                testsFailed++;
                return;
            }
            SystemMessage got = recorded.get(0);
            if (got.getEventType() != SystemMessage.EventType.USER_JOINED
                    || !got.getText().equals("Bob joined the chat.")) {
                System.out.println("FAIL: trusted system contract - content mismatch");
                testsFailed++;
                return;
            }
            System.out.println("PASS: trusted system contract");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL: trusted system contract - " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testOnlineUsersContract() {
        try {
            FakeChatView view = new FakeChatView();
            view.setOnlineUsers(Arrays.asList("Charlie", "Bob", "Alice"), "Bob", "Alice");
            List<String> displayed = view.getDisplayedOnlineUsers();
            if (!displayed.equals(Arrays.asList("Alice (Host)", "Bob (You)", "Charlie"))) {
                System.out.println("FAIL: online users contract - labels wrong: " + displayed);
                testsFailed++;
                return;
            }

            view.addOnlineUser("Dana", "Bob", "Alice");
            if (view.getDisplayedOnlineUsers().size() != 4) {
                System.out.println("FAIL: online users contract - incremental add failed");
                testsFailed++;
                return;
            }
            view.addOnlineUser("dana", "Bob", "Alice");
            if (view.getDisplayedOnlineUsers().size() != 4) {
                System.out.println("FAIL: online users contract - duplicate add not filtered");
                testsFailed++;
                return;
            }

            view.removeOnlineUser("DANA");
            if (view.getDisplayedOnlineUsers().size() != 3) {
                System.out.println("FAIL: online users contract - case-insensitive remove failed");
                testsFailed++;
                return;
            }
            view.clearOnlineUsers();
            if (!view.getDisplayedOnlineUsers().isEmpty()) {
                System.out.println("FAIL: online users contract - clear failed");
                testsFailed++;
                return;
            }
            System.out.println("PASS: online users contract");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL: online users contract - " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testRoomStatusContract() {
        try {
            FakeChatView view = new FakeChatView();
            view.setRoomStatus("Joining room...");
            view.setRoomStatus("Connected");
            if (!"Connected".equals(view.getRoomStatus())) {
                System.out.println("FAIL: room status contract - latest status not kept");
                testsFailed++;
                return;
            }
            System.out.println("PASS: room status contract");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL: room status contract - " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testRoomControlsContract() {
        try {
            FakeChatView view = new FakeChatView();
            view.setRoomControlsEnabled(true);
            if (!view.isRoomControlsEnabled() || !view.isMessagingEnabled()
                    || !view.isDisconnectEnabled()) {
                System.out.println("FAIL: room controls contract - enable failed");
                testsFailed++;
                return;
            }
            view.setRoomControlsEnabled(false);
            if (view.isRoomControlsEnabled() || view.isMessagingEnabled()
                    || view.isDisconnectEnabled()) {
                System.out.println("FAIL: room controls contract - disable failed");
                testsFailed++;
                return;
            }
            System.out.println("PASS: room controls contract");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL: room controls contract - " + e.getMessage());
            testsFailed++;
        }
    }

    private static void testLeaveButtonContract() {
        try {
            FakeChatView view = new FakeChatView();
            view.setLeaveButtonText("Close Room");
            if (!"Close Room".equals(view.getLeaveButtonText())) {
                System.out.println("FAIL: leave button contract - text not recorded");
                testsFailed++;
                return;
            }
            view.setLeaveButtonText("Leave Room");
            if (!"Leave Room".equals(view.getLeaveButtonText())) {
                System.out.println("FAIL: leave button contract - replacement failed");
                testsFailed++;
                return;
            }
            System.out.println("PASS: leave button contract");
            testsPassed++;
        } catch (Exception e) {
            System.out.println("FAIL: leave button contract - " + e.getMessage());
            testsFailed++;
        }
    }
}
