package ui;

import model.Message;
import model.SystemMessage;

import javax.swing.*;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Headless presentation tests for ChatFrame.
 */
public class ChatFrameTest {
    
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    private static final int TOTAL_TESTS = 19;

    public static void main(String[] args) {
        System.out.println("=== ChatFrame Presentation Tests ===");

        // Check if environment is headless
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: ChatFrame tests - Environment is headless");
            testsSkipped = TOTAL_TESTS;
            System.out.println("\n=== Test Summary ===");
            System.out.println("Passed: " + testsPassed);
            System.out.println("Failed: " + testsFailed);
            System.out.println("Skipped: " + testsSkipped);
            return;
        }

        try {
            testInitialPresentation();
            testLocalFormatting();
            testRemoteFormatting();
            testSystemFormatting();
            testCharacterCount();
            testMessagingEnablement();
            testConnectionStatus();
            testHostLeaveButton();
            testClientLeaveButton();
            testGroupLocalMessage();
            testGroupRemoteMessage();
            testTrustedSystemMessage();
            testHostOnlineUserLabels();
            testClientOnlineUserLabels();
            testDuplicateOnlineUsers();
            testIncrementalOnlineUsers();
            testClearOnlineUsers();
            testRoomStatus();
            testRoomControls();
        } catch (java.awt.HeadlessException e) {
            System.out.println("SKIP: ChatFrame tests - HeadlessException in current environment");
            testsSkipped = TOTAL_TESTS;
            System.out.println("\n=== Test Summary ===");
            System.out.println("Passed: " + testsPassed);
            System.out.println("Failed: " + testsFailed);
            System.out.println("Skipped: " + testsSkipped);
            return;
        }

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);

        if (testsFailed > 0) {
            System.exit(1);
        }
    }
    
    private static void testInitialPresentation() {
        System.out.print("Test 1: Initial presentation... ");

        final ChatFrame[] frameHolder = new ChatFrame[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    frameHolder[0] = new ChatFrame("Alice", "Host");
                }
            });

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ChatFrame frame = frameHolder[0];
                    if (!frame.getTitle().equals("LAN Chat - Alice (Host)")) {
                        System.out.println("FAIL - Title mismatch");
                        testsFailed++;
                        return;
                    }
                    if (frame.getChatArea().isEditable()) {
                        System.out.println("FAIL - Chat area should not be editable");
                        testsFailed++;
                        return;
                    }
                    if (!frame.getConnectionStatusText().equals("Connected to peer")) {
                        System.out.println("FAIL - Initial status mismatch");
                        testsFailed++;
                        return;
                    }
                    String chatText = frame.getChatArea().getText();
                    if (!chatText.contains("[System] Connected as Host.")) {
                        System.out.println("FAIL - Initial system message missing");
                        testsFailed++;
                        return;
                    }

                    System.out.println("PASS");
                    testsPassed++;
                }
            });

        } catch (Exception e) {
            if (e.getCause() instanceof java.awt.HeadlessException) {
                throw (java.awt.HeadlessException) e.getCause();
            }
            System.out.println("FAIL - Exception: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            testsFailed++;
        } finally {
            disposeFrame(frameHolder[0]);
        }
    }
    
    private static void testLocalFormatting() {
        System.out.print("Test 2: Local message formatting... ");

        final ChatFrame[] frameHolder = new ChatFrame[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    frameHolder[0] = new ChatFrame("Alice", "Host");
                }
            });

            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message message = new Message("Alice", "Hello Bob", timestamp);

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ChatFrame frame = frameHolder[0];
                    frame.appendLocalMessage(message);
                    String chatText = frame.getChatArea().getText();
                    String expectedLine = "[14:32] You: Hello Bob";
                    if (!chatText.contains(expectedLine)) {
                        System.out.println("FAIL - Expected line not found: " + expectedLine);
                        testsFailed++;
                        return;
                    }

                    System.out.println("PASS");
                    testsPassed++;
                }
            });

        } catch (Exception e) {
            if (e.getCause() instanceof java.awt.HeadlessException) {
                throw (java.awt.HeadlessException) e.getCause();
            }
            System.out.println("FAIL - Exception: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            testsFailed++;
        } finally {
            disposeFrame(frameHolder[0]);
        }
    }
    
    private static void testRemoteFormatting() {
        System.out.print("Test 3: Remote message formatting... ");

        final ChatFrame[] frameHolder = new ChatFrame[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    frameHolder[0] = new ChatFrame("Alice", "Host");
                }
            });

            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 33, 10);
            Message message = new Message("Bob", "Hi Alice", timestamp);

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ChatFrame frame = frameHolder[0];
                    frame.appendRemoteMessage(message);
                    String chatText = frame.getChatArea().getText();
                    String expectedLine = "[14:33] Bob: Hi Alice";
                    if (!chatText.contains(expectedLine)) {
                        System.out.println("FAIL - Expected line not found: " + expectedLine);
                        testsFailed++;
                        return;
                    }

                    System.out.println("PASS");
                    testsPassed++;
                }
            });

        } catch (Exception e) {
            if (e.getCause() instanceof java.awt.HeadlessException) {
                throw (java.awt.HeadlessException) e.getCause();
            }
            System.out.println("FAIL - Exception: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            testsFailed++;
        } finally {
            disposeFrame(frameHolder[0]);
        }
    }
    
    private static void testSystemFormatting() {
        System.out.print("Test 4: System message formatting... ");

        final ChatFrame[] frameHolder = new ChatFrame[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    frameHolder[0] = new ChatFrame("Alice", "Host");
                }
            });

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ChatFrame frame = frameHolder[0];
                    frame.appendSystemMessage("Testing");
                    String chatText = frame.getChatArea().getText();
                    String expectedLine = "[System] Testing";
                    if (!chatText.contains(expectedLine)) {
                        System.out.println("FAIL - Expected line not found: " + expectedLine);
                        testsFailed++;
                        return;
                    }

                    System.out.println("PASS");
                    testsPassed++;
                }
            });

        } catch (Exception e) {
            if (e.getCause() instanceof java.awt.HeadlessException) {
                throw (java.awt.HeadlessException) e.getCause();
            }
            System.out.println("FAIL - Exception: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            testsFailed++;
        } finally {
            disposeFrame(frameHolder[0]);
        }
    }
    
    private static void testCharacterCount() {
        System.out.print("Test 5: Character count... ");

        final ChatFrame[] frameHolder = new ChatFrame[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    frameHolder[0] = new ChatFrame("Alice", "Host");
                }
            });

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ChatFrame frame = frameHolder[0];
                    // Set text length to 10
                    frame.getMessageField().setText("1234567890");
                    frame.updateCharacterCount(10);
                    if (!frame.getCharacterCountText().equals("10 / 1000")) {
                        System.out.println("FAIL - Character count mismatch");
                        testsFailed++;
                        return;
                    }

                    // Set text length to 1001
                    frame.getMessageField().setText("");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 1001; i++) {
                        sb.append("a");
                    }
                    frame.getMessageField().setText(sb.toString());
                    frame.updateCharacterCount(1001);
                    if (!frame.getCharacterCountText().equals("1001 / 1000")) {
                        System.out.println("FAIL - Overflow count mismatch");
                        testsFailed++;
                        return;
                    }
                    if (!frame.getCharacterCountForeground().equals(Color.RED)) {
                        System.out.println("FAIL - Foreground should be red on overflow");
                        testsFailed++;
                        return;
                    }

                    // Return to valid length
                    frame.getMessageField().setText("123");
                    frame.updateCharacterCount(3);
                    Color currentForeground = frame.getCharacterCountForeground();
                    if (currentForeground.equals(Color.RED)) {
                        System.out.println("FAIL - Foreground should be restored on valid length");
                        testsFailed++;
                        return;
                    }

                    System.out.println("PASS");
                    testsPassed++;
                }
            });

        } catch (Exception e) {
            if (e.getCause() instanceof java.awt.HeadlessException) {
                throw (java.awt.HeadlessException) e.getCause();
            }
            System.out.println("FAIL - Exception: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            testsFailed++;
        } finally {
            disposeFrame(frameHolder[0]);
        }
    }
    
    private static void testMessagingEnablement() {
        System.out.print("Test 6: Messaging enablement... ");

        final ChatFrame[] frameHolder = new ChatFrame[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    frameHolder[0] = new ChatFrame("Alice", "Host");
                }
            });

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ChatFrame frame = frameHolder[0];
                    frame.setMessagingEnabled(true);
                    if (!frame.getMessageField().isEnabled()) {
                        System.out.println("FAIL - Message field should be enabled");
                        testsFailed++;
                        return;
                    }
                    if (!frame.getSendButton().isEnabled()) {
                        System.out.println("FAIL - Send button should be enabled");
                        testsFailed++;
                        return;
                    }

                    frame.setMessagingEnabled(false);
                    if (frame.getMessageField().isEnabled()) {
                        System.out.println("FAIL - Message field should be disabled");
                        testsFailed++;
                        return;
                    }
                    if (frame.getSendButton().isEnabled()) {
                        System.out.println("FAIL - Send button should be disabled");
                        testsFailed++;
                        return;
                    }

                    System.out.println("PASS");
                    testsPassed++;
                }
            });

        } catch (Exception e) {
            if (e.getCause() instanceof java.awt.HeadlessException) {
                throw (java.awt.HeadlessException) e.getCause();
            }
            System.out.println("FAIL - Exception: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            testsFailed++;
        } finally {
            disposeFrame(frameHolder[0]);
        }
    }
    
    private static void testConnectionStatus() {
        System.out.print("Test 7: Connection status... ");

        final ChatFrame[] frameHolder = new ChatFrame[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    frameHolder[0] = new ChatFrame("Alice", "Host");
                }
            });

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ChatFrame frame = frameHolder[0];
                    frame.setConnectionStatus("Disconnected");
                    if (!frame.getConnectionStatusText().equals("Disconnected")) {
                        System.out.println("FAIL - Status mismatch");
                        testsFailed++;
                        return;
                    }

                    System.out.println("PASS");
                    testsPassed++;
                }
            });

        } catch (Exception e) {
            if (e.getCause() instanceof java.awt.HeadlessException) {
                throw (java.awt.HeadlessException) e.getCause();
            }
            System.out.println("FAIL - Exception: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            testsFailed++;
        } finally {
            disposeFrame(frameHolder[0]);
        }
    }
    
    // ---------------- Step 9C-2 group presentation tests ----------------

    private static ChatFrame createFrameOnEdt(final String userName, final String role) throws Exception {
        final ChatFrame[] holder = new ChatFrame[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                holder[0] = new ChatFrame(userName, role);
            }
        });
        return holder[0];
    }

    private static void rethrowHeadless(Exception e) throws java.awt.HeadlessException {
        if (e.getCause() instanceof java.awt.HeadlessException) {
            throw (java.awt.HeadlessException) e.getCause();
        }
    }

    private static void testHostLeaveButton() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ok[0] = f.getDisconnectButton().getText().equals("Close Room")
                        && "Close this chat room".equals(f.getDisconnectButton().getToolTipText());
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: host close-room presentation");
                testsFailed++;
                return;
            }
            System.out.println("PASS: host close-room presentation");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: host close-room presentation - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testClientLeaveButton() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Bob", "Client");
            final ChatFrame f = frame;
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ok[0] = f.getDisconnectButton().getText().equals("Leave Room")
                        && "Leave this chat room".equals(f.getDisconnectButton().getToolTipText());
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: client leave-room presentation");
                testsFailed++;
                return;
            }
            System.out.println("PASS: client leave-room presentation");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: client leave-room presentation - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testGroupLocalMessage() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final Message message = new Message("Alice", "Hello group",
                LocalDateTime.of(2026, 10, 3, 14, 32, 0));
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.appendGroupMessage(message, "Alice");
                    ok[0] = f.getChatArea().getText().contains("[14:32] You: Hello group");
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: group local message formatting");
                testsFailed++;
                return;
            }
            System.out.println("PASS: group local message formatting");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: group local message formatting - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testGroupRemoteMessage() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final Message message = new Message("Bob", "Hello Alice",
                LocalDateTime.of(2026, 10, 3, 14, 33, 0));
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.appendGroupMessage(message, "Alice");
                    ok[0] = f.getChatArea().getText().contains("[14:33] Bob: Hello Alice");
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: group remote message formatting");
                testsFailed++;
                return;
            }
            System.out.println("PASS: group remote message formatting");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: group remote message formatting - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testTrustedSystemMessage() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final SystemMessage message = new SystemMessage(
                SystemMessage.EventType.USER_JOINED, "Bob joined the chat.",
                LocalDateTime.of(2026, 10, 3, 14, 32, 0));
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.appendTrustedSystemMessage(message);
                    String text = f.getChatArea().getText();
                    ok[0] = text.contains("[System] Bob joined the chat.")
                        && !text.contains("USER_JOINED");
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: trusted system formatting");
                testsFailed++;
                return;
            }
            System.out.println("PASS: trusted system formatting");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: trusted system formatting - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testHostOnlineUserLabels() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final List<String> users = Arrays.asList("Alice", "Bob", "Charlie");
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.setOnlineUsers(users, "Alice", "Alice");
                    List<String> displayed = f.getDisplayedOnlineUsers();
                    ok[0] = displayed.equals(Arrays.asList("Alice (You, Host)", "Bob", "Charlie"))
                        && f.getOnlineUsersTitle().equals("Online Users (3)");
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: host online-user labels");
                testsFailed++;
                return;
            }
            System.out.println("PASS: host online-user labels");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: host online-user labels - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testClientOnlineUserLabels() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Bob", "Client");
            final ChatFrame f = frame;
            final List<String> users = Arrays.asList("Charlie", "Bob", "Alice");
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.setOnlineUsers(users, "Bob", "Alice");
                    List<String> displayed = f.getDisplayedOnlineUsers();
                    ok[0] = displayed.equals(
                            Arrays.asList("Alice (Host)", "Bob (You)", "Charlie"));
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: client online-user labels");
                testsFailed++;
                return;
            }
            System.out.println("PASS: client online-user labels");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: client online-user labels - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testDuplicateOnlineUsers() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final List<String> users = Arrays.asList("Alice", "alice", "Bob", "BOB", "Charlie");
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.setOnlineUsers(users, "Alice", "Alice");
                    ok[0] = f.getDisplayedOnlineUsers().size() == 3
                        && f.getOnlineUsersTitle().equals("Online Users (3)");
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: duplicate online users filtered");
                testsFailed++;
                return;
            }
            System.out.println("PASS: duplicate online users filtered");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: duplicate online users filtered - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testIncrementalOnlineUsers() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.setOnlineUsers(Arrays.asList("Alice", "Bob"), "Alice", "Alice");
                    f.addOnlineUser("Charlie", "Alice", "Alice");
                    f.removeOnlineUser("Bob");
                    f.removeOnlineUser("Bob");
                    List<String> displayed = f.getDisplayedOnlineUsers();
                    ok[0] = displayed.equals(Arrays.asList("Alice (You, Host)", "Charlie"));
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: incremental online-user updates");
                testsFailed++;
                return;
            }
            System.out.println("PASS: incremental online-user updates");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: incremental online-user updates - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testClearOnlineUsers() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.setOnlineUsers(Arrays.asList("Alice", "Bob", "Charlie"), "Alice", "Alice");
                    f.clearOnlineUsers();
                    ok[0] = f.getDisplayedOnlineUsers().isEmpty()
                        && f.getOnlineUsersTitle().equals("Online Users (0)");
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: online users cleared");
                testsFailed++;
                return;
            }
            System.out.println("PASS: online users cleared");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: online users cleared - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testRoomStatus() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.setRoomStatus("Joining room...");
                    f.setRoomStatus("Connected");
                    ok[0] = f.getConnectionStatusText().equals("Connected");
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: group room status");
                testsFailed++;
                return;
            }
            System.out.println("PASS: group room status");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: group room status - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void testRoomControls() {
        ChatFrame frame = null;
        try {
            frame = createFrameOnEdt("Alice", "Host");
            final ChatFrame f = frame;
            final boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    f.setRoomControlsEnabled(true);
                    boolean enabled = f.getMessageField().isEnabled()
                        && f.getSendButton().isEnabled()
                        && f.getDisconnectButton().isEnabled();
                    f.setRoomControlsEnabled(false);
                    boolean disabled = !f.getMessageField().isEnabled()
                        && !f.getSendButton().isEnabled()
                        && !f.getDisconnectButton().isEnabled();
                    ok[0] = enabled && disabled;
                }
            });
            if (!ok[0]) {
                System.out.println("FAIL: group room controls");
                testsFailed++;
                return;
            }
            System.out.println("PASS: group room controls");
            testsPassed++;
        } catch (Exception e) {
            rethrowHeadless(e);
            System.out.println("FAIL: group room controls - " + e.getMessage());
            testsFailed++;
        } finally {
            disposeFrame(frame);
        }
    }

    private static void disposeFrame(final ChatFrame frame) {        if (frame != null) {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        frame.dispose();
                    }
                });
            } catch (Exception e) {
                // Ignore dispose errors
            }
        }
    }
}
