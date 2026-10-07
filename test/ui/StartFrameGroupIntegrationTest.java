package ui;

import controller.ClientGroupChatController;
import controller.HostGroupChatController;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * Step 9C-4A graphical StartFrame group-routing tests. Multiple StartFrame
 * windows drive real Host/Client group controllers on ephemeral ports.
 * Skips accurately when JFrame is unavailable; skips never count as passed.
 */
public class StartFrameGroupIntegrationTest {

    private static final long TIMEOUT_MS = 15000;
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    private static final int TOTAL_TESTS = 9;

    // ------------------------------------------------------------------
    // EDT helpers
    // ------------------------------------------------------------------

    private interface EdtAction<T> {
        T run() throws Exception;
    }

    private static <T> T onEdt(final EdtAction<T> action) throws Exception {
        final FutureTask<T> task = new FutureTask<>(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return action.run();
            }
        });
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    task.run();
                }
            });
        }
        try {
            return task.get();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private interface Condition {
        boolean check() throws Exception;
    }

    private static boolean waitFor(Condition c, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (c.check()) {
                return true;
            }
            Thread.sleep(100);
        }
        return c.check();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Main
    // ------------------------------------------------------------------

    private interface Test {
        boolean run() throws Exception;
    }

    private static boolean run(String label, Test t) {
        boolean ok;
        try {
            ok = t.run();
        } catch (Exception e) {
            ok = false;
            System.out.println("  (diagnostic) exception: " + e);
        }
        if (ok) {
            System.out.println("PASS: " + label);
            return true;
        } else {
            System.out.println("FAIL: " + label);
            return false;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== StartFrame Group Integration Tests ===");

        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: StartFrame group tests - Environment is headless");
            testsSkipped = TOTAL_TESTS;
            printSummary();
            return;
        }

        try {
            if (run("StartFrame launches host group room", new Test() {
                @Override public boolean run() throws Exception { return testHostLaunch(); }
            })) { testsPassed++; } else { testsFailed++; }

            if (run("StartFrame launches client group session", new Test() {
                @Override public boolean run() throws Exception { return testClientLaunch(); }
            })) { testsPassed++; } else { testsFailed++; }

            if (run("third window joins visible group room", new Test() {
                @Override public boolean run() throws Exception { return testThirdJoins(); }
            })) { testsPassed++; } else { testsFailed++; }

            if (run("client leave keeps host and peers active", new Test() {
                @Override public boolean run() throws Exception { return testClientLeave(); }
            })) { testsPassed++; } else { testsFailed++; }

            if (run("remaining group continues after client leave", new Test() {
                @Override public boolean run() throws Exception { return testRemainingUsable(); }
            })) { testsPassed++; } else { testsFailed++; }

            if (run("host close returns all users to start", new Test() {
                @Override public boolean run() throws Exception { return testHostClose(); }
            })) { testsPassed++; } else { testsFailed++; }

            if (run("second group session without restart", new Test() {
                @Override public boolean run() throws Exception { return testSecondSession(); }
            })) { testsPassed++; } else { testsFailed++; }

            if (run("duplicate username returns client to start", new Test() {
                @Override public boolean run() throws Exception { return testDuplicateRejected(); }
            })) { testsPassed++; } else { testsFailed++; }

            if (run("failed join can retry successfully", new Test() {
                @Override public boolean run() throws Exception { return testFailureRecovery(); }
            })) { testsPassed++; } else { testsFailed++; }
        } catch (java.awt.HeadlessException e) {
            System.out.println("SKIP: StartFrame group tests - HeadlessException in current environment");
            testsSkipped = TOTAL_TESTS;
            printSummary();
            return;
        }

        printSummary();
        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void printSummary() {
        System.out.println("\n=== StartFrameGroupIntegrationTest Summary ===");
        System.out.println("PASS: " + testsPassed);
        System.out.println("FAIL: " + testsFailed);
        System.out.println("SKIP: " + testsSkipped);
    }

    // ------------------------------------------------------------------
    // Window helpers (tests share one room per test for isolation)
    // ------------------------------------------------------------------

    private static class Windows {
        final List<StartFrame> frames = new ArrayList<>();
    }

    private static StartFrame createStartFrame(final int port, final Windows windows) throws Exception {
        StartFrame frame = onEdt(new EdtAction<StartFrame>() {
            @Override
            public StartFrame run() {
                StartFrame f = new StartFrame(port);
                f.setVisible(true);
                return f;
            }
        });
        windows.frames.add(frame);
        return frame;
    }

    private static void setField(final javax.swing.text.JTextComponent field, final String text)
            throws Exception {
        onEdt(new EdtAction<Object>() {
            @Override
            public Object run() {
                field.setText(text);
                return null;
            }
        });
    }

    private static void destroyAll(final Windows windows) {
        // Tear down clients BEFORE hosts: closing a host first broadcasts
        // ROOM_CLOSED, and a client navigation queued after disposal would
        // re-show its StartFrame. Closed clients ignore the broadcast.
        for (StartFrame frame : windows.frames) {
            try {
                ClientGroupChatController client = frame.getClientGroupControllerForTest();
                if (client != null) {
                    client.close();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        for (StartFrame frame : windows.frames) {
            try {
                HostGroupChatController host = frame.getHostGroupControllerForTest();
                if (host != null) {
                    host.close();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        try {
            onEdt(new EdtAction<Object>() {
                @Override
                public Object run() {
                    for (StartFrame frame : windows.frames) {
                        try {
                            ChatFrame chat = frame.getCurrentChatFrameForTest();
                            if (chat != null) {
                                chat.dispose();
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                        try {
                            frame.dispose();
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            // Ignore
        }
        sleep(500);
        // Self-verifying teardown: a lifecycle navigation racing disposal can
        // re-show a StartFrame after the first dispose pass. Retry disposal
        // until no visible windows remain and log any resurrection.
        try {
            for (int round = 0; round < 3; round++) {
                final List<String> survivors = onEdt(new EdtAction<List<String>>() {
                    @Override
                    public List<String> run() {
                        List<String> found = new ArrayList<>();
                        for (StartFrame frame : windows.frames) {
                            ChatFrame chat = null;
                            try {
                                chat = frame.getCurrentChatFrameForTest();
                            } catch (Exception e) {
                                // Ignore
                            }
                            if (chat != null && chat.isVisible()) {
                                found.add("chat:" + chat.getTitle());
                                chat.dispose();
                            }
                            if (frame.isVisible()) {
                                String user;
                                try {
                                    user = frame.getUserNameField().getText();
                                } catch (Exception e) {
                                    user = "?";
                                }
                                found.add("start:" + user);
                                frame.dispose();
                            }
                        }
                        return found;
                    }
                });
                if (survivors.isEmpty()) {
                    break;
                }
                System.out.println("  (teardown) round " + round + " re-disposed: " + survivors);
                sleep(300);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private static int hostPort(final StartFrame hostFrame) throws Exception {
        for (int i = 0; i < 150; i++) {
            int port = hostFrame.getActiveRoomPortForTest();
            if (port > 0) {
                return port;
            }
            Thread.sleep(100);
        }
        return -1;
    }

    private static String chatText(final ChatFrame frame) throws Exception {
        return onEdt(new EdtAction<String>() {
            @Override
            public String run() {
                return frame.getChatArea().getText();
            }
        });
    }

    private static List<String> onlineUsers(final ChatFrame frame) throws Exception {
        return onEdt(new EdtAction<List<String>>() {
            @Override
            public List<String> run() {
                return frame.getDisplayedOnlineUsers();
            }
        });
    }

    private static boolean frameVisible(final java.awt.Window window) throws Exception {
        return onEdt(new EdtAction<Boolean>() {
            @Override
            public Boolean run() {
                return window.isVisible();
            }
        });
    }

    private static String startStatus(final StartFrame frame) throws Exception {
        return onEdt(new EdtAction<String>() {
            @Override
            public String run() {
                return frame.getStatusTextForTest();
            }
        });
    }

    // ------------------------------------------------------------------
    // Shared room setup: host Alice + clients Bob and Charlie
    // ------------------------------------------------------------------

    private static class Room {
        Windows windows = new Windows();
        StartFrame hostFrame;
        StartFrame bobFrame;
        StartFrame charlieFrame;
        int port;
    }

    private static Room startRoomWithThree() throws Exception {
        Room room = new Room();
        room.hostFrame = createStartFrame(0, room.windows);
        setField(room.hostFrame.getUserNameField(), "Alice");
        room.hostFrame.getCreateChatButton().doClick();
        room.port = hostPort(room.hostFrame);
        if (room.port <= 0) {
            throw new IllegalStateException("Host room never bound a port");
        }
        room.bobFrame = createStartFrame(room.port, room.windows);
        setField(room.bobFrame.getUserNameField(), "Bob");
        setField(room.bobFrame.getHostIPField(), "127.0.0.1");
        room.bobFrame.getJoinChatButton().doClick();
        room.charlieFrame = createStartFrame(room.port, room.windows);
        setField(room.charlieFrame.getUserNameField(), "Charlie");
        setField(room.charlieFrame.getHostIPField(), "127.0.0.1");
        room.charlieFrame.getJoinChatButton().doClick();

        final Room r = room;
        if (!waitFor(new Condition() {
            @Override public boolean check() throws Exception {
                ClientGroupChatController bob = r.bobFrame.getClientGroupControllerForTest();
                ClientGroupChatController charlie = r.charlieFrame.getClientGroupControllerForTest();
                return bob != null && charlie != null && bob.isJoined() && charlie.isJoined();
            }
        }, TIMEOUT_MS)) {
            throw new IllegalStateException("Clients never joined");
        }
        return room;
    }

    // ------------------------------------------------------------------
    // Test 1: Host group launch
    // ------------------------------------------------------------------

    private static boolean testHostLaunch() throws Exception {
        Windows windows = new Windows();
        try {
            StartFrame hostFrame = createStartFrame(0, windows);
            setField(hostFrame.getUserNameField(), "Alice");
            hostFrame.getCreateChatButton().doClick();

            final StartFrame hf = hostFrame;
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    ChatFrame chat = hf.getCurrentChatFrameForTest();
                    return chat != null && frameVisible(chat);
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) host ChatFrame never opened");
                return false;
            }
            HostGroupChatController controller = hostFrame.getHostGroupControllerForTest();
            if (controller == null) {
                System.out.println("  (diagnostic) no HostGroupChatController");
                return false;
            }
            if (hostFrame.getClientGroupControllerForTest() != null) {
                System.out.println("  (diagnostic) unexpected client controller");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return controller.isRoomStarted();
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) room never started");
                return false;
            }
            return true;
        } finally {
            destroyAll(windows);
        }
    }

    // ------------------------------------------------------------------
    // Test 2: Client group launch
    // ------------------------------------------------------------------

    private static boolean testClientLaunch() throws Exception {
        Windows windows = new Windows();
        try {
            StartFrame hostFrame = createStartFrame(0, windows);
            setField(hostFrame.getUserNameField(), "Alice");
            hostFrame.getCreateChatButton().doClick();
            int port = hostPort(hostFrame);
            if (port <= 0) {
                System.out.println("  (diagnostic) host never bound");
                return false;
            }

            final StartFrame clientFrame = createStartFrame(port, windows);
            setField(clientFrame.getUserNameField(), "Bob");
            setField(clientFrame.getHostIPField(), "127.0.0.1");
            clientFrame.getJoinChatButton().doClick();

            if (clientFrame.getClientGroupControllerForTest() == null) {
                System.out.println("  (diagnostic) no ClientGroupChatController");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    ClientGroupChatController c = clientFrame.getClientGroupControllerForTest();
                    return c != null && c.isJoined();
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) client never joined");
                return false;
            }
            return true;
        } finally {
            destroyAll(windows);
        }
    }

    // ------------------------------------------------------------------
    // Test 3: Third window joins
    // ------------------------------------------------------------------

    private static boolean testThirdJoins() throws Exception {
        Room room = null;
        Windows windows = new Windows();
        try {
            StartFrame hostFrame = createStartFrame(0, windows);
            setField(hostFrame.getUserNameField(), "Alice");
            hostFrame.getCreateChatButton().doClick();
            int port = hostPort(hostFrame);
            if (port <= 0) return false;

            StartFrame bobFrame = createStartFrame(port, windows);
            setField(bobFrame.getUserNameField(), "Bob");
            setField(bobFrame.getHostIPField(), "127.0.0.1");
            bobFrame.getJoinChatButton().doClick();

            StartFrame charlieFrame = createStartFrame(port, windows);
            setField(charlieFrame.getUserNameField(), "Charlie");
            setField(charlieFrame.getHostIPField(), "127.0.0.1");
            charlieFrame.getJoinChatButton().doClick();

            final ChatFrame hostChat = hostFrame.getCurrentChatFrameForTest();
            if (hostChat == null) {
                System.out.println("  (diagnostic) no host chat frame");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    List<String> shown = onlineUsers(hostChat);
                    return containsUser(shown, "Alice")
                        && containsUser(shown, "Bob")
                        && containsUser(shown, "Charlie");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) host online=" + onlineUsers(hostChat));
                return false;
            }
            return true;
        } finally {
            destroyAll(windows);
        }
    }

    private static boolean containsUser(List<String> displayed, String bare) {
        for (String row : displayed) {
            String name = row.replaceAll(" \\(.*\\)$", "");
            if (name.equals(bare)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Test 4: Client leaves, host and peers stay
    // ------------------------------------------------------------------

    private static boolean testClientLeave() throws Exception {
        Room room = null;
        try {
            room = startRoomWithThree();
            final Room r = room;

            // Bob leaves gracefully through the controller API (no dialog).
            r.bobFrame.getClientGroupControllerForTest().leaveRoomForApplicationExit();

            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return frameVisible(r.bobFrame)
                        && "You left the chat.".equals(startStatus(r.bobFrame));
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Bob not back at start");
                return false;
            }
            final ChatFrame hostChat = r.hostFrame.getCurrentChatFrameForTest();
            final ChatFrame charlieChat = r.charlieFrame.getCurrentChatFrameForTest();
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return hostChat != null && frameVisible(hostChat)
                        && charlieChat != null && frameVisible(charlieChat);
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) host or Charlie frame gone");
                return false;
            }
            ClientGroupChatController charlieCtrl =
                r.charlieFrame.getClientGroupControllerForTest();
            if (charlieCtrl == null || !charlieCtrl.isJoined()) {
                // Poll once more; Charlie must remain connected.
                if (!waitFor(new Condition() {
                    @Override public boolean check() throws Exception {
                        ClientGroupChatController c =
                            r.charlieFrame.getClientGroupControllerForTest();
                        return c != null && c.isJoined();
                    }
                }, TIMEOUT_MS)) {
                    System.out.println("  (diagnostic) Charlie dropped");
                    return false;
                }
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return chatText(hostChat).contains("Bob left the chat.");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) host missed Bob-left line");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return !containsUser(onlineUsers(hostChat), "Bob");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Bob still listed");
                return false;
            }
            return true;
        } finally {
            if (room != null) {
                destroyAll(room.windows);
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 5: Remaining group keeps messaging
    // ------------------------------------------------------------------

    private static boolean testRemainingUsable() throws Exception {
        Room room = null;
        try {
            room = startRoomWithThree();
            final Room r = room;

            r.bobFrame.getClientGroupControllerForTest().leaveRoomForApplicationExit();
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return frameVisible(r.bobFrame);
                }
            }, TIMEOUT_MS)) return false;

            final ChatFrame hostChat = r.hostFrame.getCurrentChatFrameForTest();
            final ChatFrame charlieChat = r.charlieFrame.getCurrentChatFrameForTest();
            final String beforeHost = chatText(hostChat);
            setField(charlieChat.getMessageField(), "hi Alice");
            charlieChat.getSendButton().doClick();
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    String text = chatText(hostChat);
                    return text.length() > beforeHost.length() && text.contains("hi Alice");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Alice never received Charlie");
                return false;
            }
            final String beforeCharlie = chatText(charlieChat);
            setField(hostChat.getMessageField(), "hi Charlie");
            hostChat.getSendButton().doClick();
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    String text = chatText(charlieChat);
                    return text.length() > beforeCharlie.length() && text.contains("hi Charlie");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Charlie never received Alice");
                return false;
            }
            return true;
        } finally {
            if (room != null) {
                destroyAll(room.windows);
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 6: Host close returns everyone to start
    // ------------------------------------------------------------------

    private static boolean testHostClose() throws Exception {
        Room room = null;
        try {
            room = startRoomWithThree();
            final Room r = room;
            final int port = r.port;

            r.hostFrame.getHostGroupControllerForTest().closeRoomForApplicationExit();

            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return frameVisible(r.hostFrame)
                        && "You closed the chat room.".equals(startStatus(r.hostFrame));
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) host not back at start");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return frameVisible(r.bobFrame)
                        && "The Host closed the chat room.".equals(startStatus(r.bobFrame))
                        && frameVisible(r.charlieFrame)
                        && "The Host closed the chat room.".equals(startStatus(r.charlieFrame));
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) clients not back at start");
                return false;
            }
            sleep(500);
            boolean refused = false;
            Socket probe = null;
            try {
                probe = new Socket("127.0.0.1", port);
            } catch (java.io.IOException expected) {
                refused = true;
            } finally {
                if (probe != null) {
                    try {
                        probe.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            if (!refused) {
                System.out.println("  (diagnostic) port still accepts connections");
                return false;
            }
            return true;
        } finally {
            if (room != null) {
                destroyAll(room.windows);
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 7: Second session without restart
    // ------------------------------------------------------------------

    private static boolean testSecondSession() throws Exception {
        Room room = null;
        try {
            room = startRoomWithThree();
            final Room r = room;

            r.hostFrame.getHostGroupControllerForTest().closeRoomForApplicationExit();
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return frameVisible(r.hostFrame);
                }
            }, TIMEOUT_MS)) return false;

            // Same StartFrame process hosts again (username preserved).
            r.hostFrame.getCreateChatButton().doClick();
            final int port2 = hostPort(r.hostFrame);
            if (port2 <= 0) {
                System.out.println("  (diagnostic) second room never bound");
                return false;
            }
            // Rejoin through new client windows on the new port.
            Windows extra = new Windows();
            StartFrame bob2 = createStartFrame(port2, extra);
            r.windows.frames.addAll(extra.frames);
            setField(bob2.getUserNameField(), "Bob");
            setField(bob2.getHostIPField(), "127.0.0.1");
            bob2.getJoinChatButton().doClick();
            final StartFrame bobFrame2 = bob2;
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    ClientGroupChatController c = bobFrame2.getClientGroupControllerForTest();
                    return c != null && c.isJoined();
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Bob never rejoined");
                return false;
            }
            final ChatFrame hostChat = r.hostFrame.getCurrentChatFrameForTest();
            final String before = chatText(hostChat);
            ChatFrame bobChat = bob2.getCurrentChatFrameForTest();
            setField(bobChat.getMessageField(), "second room works");
            bobChat.getSendButton().doClick();
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    String text = chatText(hostChat);
                    return text.length() > before.length()
                        && text.contains("second room works");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) second-room messaging broken");
                return false;
            }
            return true;
        } finally {
            if (room != null) {
                destroyAll(room.windows);
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 8: Duplicate username
    // ------------------------------------------------------------------

    private static boolean testDuplicateRejected() throws Exception {
        Room room = null;
        try {
            room = startRoomWithThree();
            final Room r = room;

            Windows extra = new Windows();
            final StartFrame eveFrame = createStartFrame(r.port, extra);
            r.windows.frames.addAll(extra.frames);
            setField(eveFrame.getUserNameField(), "alice");
            setField(eveFrame.getHostIPField(), "127.0.0.1");
            eveFrame.getJoinChatButton().doClick();

            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return frameVisible(eveFrame)
                        && "Username is already in use.".equals(startStatus(eveFrame));
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Eve not rejected to start");
                return false;
            }
            final ChatFrame hostChat = r.hostFrame.getCurrentChatFrameForTest();
            if (!frameVisible(hostChat)) {
                System.out.println("  (diagnostic) host room died on duplicate");
                return false;
            }
            HostGroupChatController hostCtrl = r.hostFrame.getHostGroupControllerForTest();
            if (hostCtrl == null || !hostCtrl.isRoomStarted()) {
                System.out.println("  (diagnostic) host controller gone");
                return false;
            }
            return true;
        } finally {
            if (room != null) {
                destroyAll(room.windows);
            }
        }
    }

    // ------------------------------------------------------------------
    // Test 9: Connection failure recovery
    // ------------------------------------------------------------------

    private static boolean testFailureRecovery() throws Exception {
        Windows windows = new Windows();
        try {
            int freePort;
            ServerSocket occupier = new ServerSocket(0);
            try {
                freePort = occupier.getLocalPort();
            } finally {
                occupier.close();
            }
            final StartFrame failFrame = createStartFrame(freePort, windows);
            setField(failFrame.getUserNameField(), "Zoe");
            setField(failFrame.getHostIPField(), "127.0.0.1");
            failFrame.getJoinChatButton().doClick();

            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    return frameVisible(failFrame)
                        && "Unable to connect to the chat room.".equals(startStatus(failFrame));
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) failure never returned to start");
                return false;
            }
            final boolean[] enabled = new boolean[1];
            onEdt(new EdtAction<Object>() {
                @Override
                public Object run() {
                    enabled[0] = failFrame.getCreateChatButton().isEnabled()
                        && failFrame.getJoinChatButton().isEnabled()
                        && failFrame.getUserNameField().isEnabled()
                        && failFrame.getHostIPField().isEnabled();
                    return null;
                }
            });
            if (!enabled[0]) {
                System.out.println("  (diagnostic) controls not re-enabled");
                return false;
            }

            // Start a host and retry successfully.
            StartFrame hostFrame = createStartFrame(0, windows);
            setField(hostFrame.getUserNameField(), "Alice");
            hostFrame.getCreateChatButton().doClick();
            int port = hostPort(hostFrame);
            if (port <= 0) return false;

            final StartFrame retryFrame = createStartFrame(port, windows);
            setField(retryFrame.getUserNameField(), "Zoe");
            setField(retryFrame.getHostIPField(), "127.0.0.1");
            retryFrame.getJoinChatButton().doClick();
            if (!waitFor(new Condition() {
                @Override public boolean check() throws Exception {
                    ClientGroupChatController c = retryFrame.getClientGroupControllerForTest();
                    return c != null && c.isJoined();
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) retry never joined");
                return false;
            }
            return true;
        } finally {
            destroyAll(windows);
        }
    }
}
