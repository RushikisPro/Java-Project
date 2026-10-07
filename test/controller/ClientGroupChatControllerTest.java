package controller;

import model.Message;
import model.SystemMessage;
import model.UserListMessage;
import network.GroupChatClientSession;
import network.GroupChatServer;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Step 9C-3A client group controller tests. Real GroupChatServer rooms on
 * localhost with ephemeral ports; FakeChatView records presentation.
 */
public class ClientGroupChatControllerTest {

    private static final long TIMEOUT_MS = 10000;
    private static final String LOCALHOST = "127.0.0.1";

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static class Room {
        GroupChatServer server;
        int port;
        final CountDownLatch startedLatch = new CountDownLatch(1);
        volatile int startedPort = -1;
    }

    private static Room startRoom(String host) throws Exception {
        Room r = new Room();
        r.server = new GroupChatServer(0, host);
        r.server.setListener(new GroupChatServer.GroupChatServerListener() {
            @Override public void onRoomStarted(String ip, int port) {
                r.startedPort = port;
                r.startedLatch.countDown();
            }
            @Override public void onUserJoined(String u) { }
            @Override public void onUserLeft(String u) { }
            @Override public void onChatMessage(Message m) { }
            @Override public void onRoomError(Exception e) { }
            @Override public void onRoomStopped() { }
        });
        r.server.start();
        if (!r.startedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Room did not start");
        }
        r.port = r.startedPort;
        return r;
    }
    private static void stopRoom(Room r) {
        if (r != null && r.server != null) {
            try {
                r.server.stop();
            } catch (Exception e) {
                // Ignore
            }
        }
        sleep(300);
    }

    /**
     * Invoke GroupChatServer's package-private abrupt-stop test hook.
     * Reflection keeps this controller test in its own package without
     * widening production visibility.
     */
    private static void abruptStop(GroupChatServer server) throws Exception {
        java.lang.reflect.Method hook =
                GroupChatServer.class.getDeclaredMethod("abruptStopForTest");
        hook.setAccessible(true);
        hook.invoke(server);
    }

    private static class LifecycleProbe
            implements ClientGroupChatController.ClientGroupChatLifecycleListener {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicReference<String> reason = new AtomicReference<>();
        volatile boolean initiatedLocally;

        @Override
        public void onClientRoomEnded(String reason, boolean initiatedLocally) {
            this.reason.set(reason);
            this.initiatedLocally = initiatedLocally;
            count.incrementAndGet();
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static class SessionProbe
            implements GroupChatClientSession.GroupChatClientSessionListener {
        final CountDownLatch joinedLatch = new CountDownLatch(1);

        @Override public void onConnecting(String h, int p) { }
        @Override public void onJoined(String u) { joinedLatch.countDown(); }
        @Override public void onChatMessage(Message m) { }
        @Override public void onSystemMessage(SystemMessage m) { }
        @Override public void onUserList(model.UserListMessage m) { }
        @Override public void onUsernameRejected(String r) { }
        @Override public void onRoomClosed(String r) { }
        @Override public void onDisconnectedUnexpectedly() { }
        @Override public void onConnectionError(Exception e) { }
        @Override public void onSessionStopped() { }

        boolean awaitJoined() throws InterruptedException {
            return joinedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
            Thread.sleep(50);
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
        int passed = 0;
        int failed = 0;
        int skipped = 0;

        if (run("client controller initial state", new Test() {
            @Override public boolean run() throws Exception { return testInitialState(); }
        })) { passed++; } else { failed++; }

        if (run("client controller joins room", new Test() {
            @Override public boolean run() throws Exception { return testJoinEnablesRoom(); }
        })) { passed++; } else { failed++; }

        if (run("client controller avoids local echo", new Test() {
            @Override public boolean run() throws Exception { return testNoLocalEcho(); }
        })) { passed++; } else { failed++; }

        if (run("enter sends one group message", new Test() {
            @Override public boolean run() throws Exception { return testEnterSendsOnce(); }
        })) { passed++; } else { failed++; }

        if (run("client controller displays host message", new Test() {
            @Override public boolean run() throws Exception { return testHostMessage(); }
        })) { passed++; } else { failed++; }

        if (run("client controller handles user join", new Test() {
            @Override public boolean run() throws Exception { return testUserJoin(); }
        })) { passed++; } else { failed++; }

        if (run("client controller handles user leave", new Test() {
            @Override public boolean run() throws Exception { return testUserLeave(); }
        })) { passed++; } else { failed++; }

        if (run("client leave cancellation", new Test() {
            @Override public boolean run() throws Exception { return testLeaveCancelled(); }
        })) { passed++; } else { failed++; }

        if (run("client leaves room gracefully", new Test() {
            @Override public boolean run() throws Exception { return testLeaveConfirmed(); }
        })) { passed++; } else { failed++; }

        if (run("client controller handles username rejection", new Test() {
            @Override public boolean run() throws Exception { return testUsernameRejected(); }
        })) { passed++; } else { failed++; }

        if (run("client controller handles room close once", new Test() {
            @Override public boolean run() throws Exception { return testRoomClosed(); }
        })) { passed++; } else { failed++; }

        if (run("client controller handles abrupt room loss", new Test() {
            @Override public boolean run() throws Exception { return testAbruptLoss(); }
        })) { passed++; } else { failed++; }

        if (run("client controller validates outgoing text", new Test() {
            @Override public boolean run() throws Exception { return testValidation(); }
        })) { passed++; } else { failed++; }

        if (run("client controller cleans listeners", new Test() {
            @Override public boolean run() throws Exception { return testListenerCleanup(); }
        })) { passed++; } else { failed++; }

        if (run("client controller ignores late callbacks", new Test() {
            @Override public boolean run() throws Exception { return testLateCallbacksIgnored(); }
        })) { passed++; } else { failed++; }

        if (run("client application-exit shutdown is graceful", new Test() {
            @Override public boolean run() throws Exception { return testApplicationExitShutdown(); }
        })) { passed++; } else { failed++; }

        if (run("controller applies authoritative user list", new Test() {
            @Override public boolean run() throws Exception { return testAuthoritativeList(); }
        })) { passed++; } else { failed++; }

        if (run("late client controller sees existing users", new Test() {
            @Override public boolean run() throws Exception { return testLateControllerSeesAll(); }
        })) { passed++; } else { failed++; }

        if (run("controller updates snapshot incrementally", new Test() {
            @Override public boolean run() throws Exception { return testIncrementalAfterSnapshot(); }
        })) { passed++; } else { failed++; }

        if (run("invalid user list preserves previous state", new Test() {
            @Override public boolean run() throws Exception { return testInvalidListPreserves(); }
        })) { passed++; } else { failed++; }

        if (run("repeated invalid user lists terminate controller", new Test() {
            @Override public boolean run() throws Exception { return testRepeatedInvalidLists(); }
        })) { passed++; } else { failed++; }

        if (run("controller derives host from authoritative snapshot", new Test() {
            @Override public boolean run() throws Exception { return testHostFromSnapshot(); }
        })) { passed++; } else { failed++; }

        System.out.println("\n=== ClientGroupChatControllerTest Summary ===");
        System.out.println("PASS: " + passed);
        System.out.println("FAIL: " + failed);
        System.out.println("SKIP: " + skipped);

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ClientGroupChatController newController(
            FakeChatView view, int port, String username,
            LifecycleProbe lifecycle, boolean confirmLeave) {
        DisconnectConfirmation confirmation = new DisconnectConfirmation() {
            @Override
            public boolean confirm(java.awt.Component parent) {
                return confirmLeave;
            }
        };
        return new ClientGroupChatController(
                view, LOCALHOST, port, username, lifecycle, confirmation);
    }

    private static int countTrusted(FakeChatView view, SystemMessage.EventType type) {
        int n = 0;
        for (SystemMessage m : view.getTrustedSystemMessages()) {
            if (m.getEventType() == type) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Test 1: Initial connecting state
    // ------------------------------------------------------------------

    private static boolean testInitialState() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);

            if (!"Leave Room".equals(view.getLeaveButtonText())) {
                System.out.println("  (diagnostic) leave text=" + view.getLeaveButtonText());
                return false;
            }
            if (!"Connecting...".equals(view.getRoomStatus())) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus());
                return false;
            }
            if (view.isRoomControlsEnabled()) {
                System.out.println("  (diagnostic) controls enabled too early");
                return false;
            }
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) never joined after initial state");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 2: Join enables room
    // ------------------------------------------------------------------

    private static boolean testJoinEnablesRoom() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;

            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) never joined");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return "Connected".equals(view.getRoomStatus()) && view.isRoomControlsEnabled();
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus()
                    + " controls=" + view.isRoomControlsEnabled());
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getDisplayedOnlineUsers().contains("Bob (You)");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) online=" + view.getDisplayedOnlineUsers());
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 3: Send uses server broadcast copy (no local echo)
    // ------------------------------------------------------------------

    private static boolean testNoLocalEcho() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.isRoomControlsEnabled(); }
            }, TIMEOUT_MS)) return false;

            view.getMessageField().setText("Hello group");
            view.getSendButton().doClick();

            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() >= 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) broadcast copy never displayed");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getMessageField().getText().isEmpty(); }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) input not cleared after send");
                return false;
            }
            sleep(500);
            List<String> lines = view.getGroupMessages();
            if (lines.size() != 1) {
                System.out.println("  (diagnostic) group lines=" + lines.size() + " (local echo?)");
                return false;
            }
            String line = lines.get(0);
            if (!line.contains("You") || !line.contains("Hello group")) {
                System.out.println("  (diagnostic) line=" + line);
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 4: Enter key sends once
    // ------------------------------------------------------------------

    private static boolean testEnterSendsOnce() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.isRoomControlsEnabled(); }
            }, TIMEOUT_MS)) return false;

            view.getMessageField().setText("Enter sends this");
            view.getMessageField().postActionEvent();

            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() >= 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) enter did not send");
                return false;
            }
            sleep(500);
            if (view.getGroupMessages().size() != 1) {
                System.out.println("  (diagnostic) enter sent "
                    + view.getGroupMessages().size() + " copies");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 5: Host message received
    // ------------------------------------------------------------------

    private static boolean testHostMessage() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            LocalDateTime ts = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            if (!r.server.broadcastHostMessage(new Message("Alice", "Welcome Bob", ts))) {
                System.out.println("  (diagnostic) host broadcast failed");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() >= 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) host message not displayed");
                return false;
            }
            sleep(400);
            List<String> lines = view.getGroupMessages();
            if (lines.size() != 1) return false;
            String line = lines.get(0);
            if (!line.contains("Alice") || !line.contains("Welcome Bob") || line.contains("You")) {
                System.out.println("  (diagnostic) line=" + line);
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 6: Another client joins
    // ------------------------------------------------------------------

    private static boolean testUserJoin() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        GroupChatClientSession charlie = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;
            final int trustedBefore = view.getTrustedSystemMessages().size();

            SessionProbe charlieProbe = new SessionProbe();
            charlie = new GroupChatClientSession(LOCALHOST, r.port, "Charlie");
            charlie.setListener(charlieProbe);
            charlie.connect();
            if (!charlieProbe.awaitJoined()) {
                System.out.println("  (diagnostic) Charlie never joined");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getTrustedSystemMessages().size() > trustedBefore;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) USER_JOINED not recorded");
                return false;
            }
            boolean sawCharlie = false;
            for (SystemMessage m : view.getTrustedSystemMessages()) {
                if (m.getEventType() == SystemMessage.EventType.USER_JOINED
                        && m.getText().equals("Charlie joined the chat.")) {
                    sawCharlie = true;
                }
            }
            if (!sawCharlie) return false;
            final GroupChatClientSession ch = charlie;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getDisplayedOnlineUsers().contains("Charlie");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) online=" + view.getDisplayedOnlineUsers());
                return false;
            }
            if (!controller.isJoined() || !"Connected".equals(view.getRoomStatus())) {
                return false;
            }
            charlie.disconnect();
            return true;
        } finally {
            if (charlie != null) {
                try {
                    charlie.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 7: Another client leaves
    // ------------------------------------------------------------------

    private static boolean testUserLeave() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        GroupChatClientSession charlie = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            SessionProbe charlieProbe = new SessionProbe();
            charlie = new GroupChatClientSession(LOCALHOST, r.port, "Charlie");
            charlie.setListener(charlieProbe);
            charlie.connect();
            if (!charlieProbe.awaitJoined()) return false;
            final GroupChatClientSession ch = charlie;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getDisplayedOnlineUsers().contains("Charlie");
                }
            }, TIMEOUT_MS)) return false;
            final int trustedBefore = view.getTrustedSystemMessages().size();

            ch.disconnect();
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    for (SystemMessage m : view.getTrustedSystemMessages()) {
                        if (m.getEventType() == SystemMessage.EventType.USER_LEFT
                                && m.getText().equals("Charlie left the chat.")) {
                            return true;
                        }
                    }
                    return false;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) USER_LEFT not displayed");
                return false;
            }
            sleep(500);
            int leftCount = 0;
            for (SystemMessage m : view.getTrustedSystemMessages()) {
                if (m.getEventType() == SystemMessage.EventType.USER_LEFT) {
                    leftCount++;
                }
            }
            if (leftCount != 1) {
                System.out.println("  (diagnostic) USER_LEFT count=" + leftCount);
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return !view.getDisplayedOnlineUsers().contains("Charlie");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Charlie still listed");
                return false;
            }
            if (!controller.isJoined()) {
                System.out.println("  (diagnostic) Bob dropped after Charlie left");
                return false;
            }
            // Bob can still send.
            view.getMessageField().setText("still here");
            view.getSendButton().doClick();
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() >= 1; }
            }, TIMEOUT_MS)) return false;
            return true;
        } finally {
            if (charlie != null) {
                try {
                    charlie.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 8: Cancel Leave Room
    // ------------------------------------------------------------------

    private static boolean testLeaveCancelled() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, false);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            view.getDisconnectButton().doClick();
            sleep(500);
            if (!controller.isJoined()) {
                System.out.println("  (diagnostic) cancelled leave ended session");
                return false;
            }
            if (!view.isRoomControlsEnabled()) {
                System.out.println("  (diagnostic) controls disabled after cancel");
                return false;
            }
            if (lifecycle.count.get() != 0) {
                System.out.println("  (diagnostic) lifecycle fired on cancel");
                return false;
            }
            view.getMessageField().setText("still joined");
            view.getSendButton().doClick();
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() >= 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) messaging broken after cancel");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 9: Confirm Leave Room
    // ------------------------------------------------------------------

    private static boolean testLeaveConfirmed() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final Room room = r;
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, room.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            view.getDisconnectButton().doClick();
            if (!lifecycle.await()) {
                System.out.println("  (diagnostic) lifecycle never fired on leave");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getSystemMessages().contains("You left the chat.");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) leave line missing");
                return false;
            }
            sleep(400);
            int leaveLines = 0;
            for (String s : view.getSystemMessages()) {
                if (s.equals("You left the chat.")) {
                    leaveLines++;
                }
            }
            if (leaveLines != 1) {
                System.out.println("  (diagnostic) leave lines=" + leaveLines);
                return false;
            }
            if (view.isRoomControlsEnabled()) return false;
            if (!"Disconnected".equals(view.getRoomStatus())) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus());
                return false;
            }
            if (lifecycle.count.get() != 1 || !lifecycle.initiatedLocally
                    || !"You left the chat.".equals(lifecycle.reason.get())) {
                System.out.println("  (diagnostic) lifecycle wrong");
                return false;
            }
            if (controller.isJoined()) {
                System.out.println("  (diagnostic) still joined after leave");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) server did not remove Bob");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 10: Duplicate username rejection
    // ------------------------------------------------------------------

    private static boolean testUsernameRejected() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "alice", lifecycle, true);
            if (!lifecycle.await()) {
                System.out.println("  (diagnostic) rejection lifecycle never fired");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return countTrusted(view, SystemMessage.EventType.USERNAME_REJECTED) >= 1;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) trusted rejection never displayed");
                return false;
            }
            sleep(400);
            if (countTrusted(view, SystemMessage.EventType.USERNAME_REJECTED) != 1) {
                System.out.println("  (diagnostic) rejection displayed more than once");
                return false;
            }
            if (!"Username rejected".equals(view.getRoomStatus())) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus());
                return false;
            }
            if (view.isRoomControlsEnabled()) return false;
            if (lifecycle.count.get() != 1 || lifecycle.initiatedLocally
                    || !"Username is already in use.".equals(lifecycle.reason.get())) {
                System.out.println("  (diagnostic) lifecycle wrong");
                return false;
            }
            if (controller.isJoined()) {
                System.out.println("  (diagnostic) rejected controller reports joined");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 11: Room closed
    // ------------------------------------------------------------------

    private static boolean testRoomClosed() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            r.server.stop();
            if (!lifecycle.await()) {
                System.out.println("  (diagnostic) close lifecycle never fired");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return countTrusted(view, SystemMessage.EventType.ROOM_CLOSED) >= 1;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) ROOM_CLOSED never displayed");
                return false;
            }
            sleep(500);
            if (countTrusted(view, SystemMessage.EventType.ROOM_CLOSED) != 1) {
                System.out.println("  (diagnostic) ROOM_CLOSED duplicated");
                return false;
            }
            if (!"Room closed".equals(view.getRoomStatus())) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus());
                return false;
            }
            if (view.isRoomControlsEnabled()) return false;
            if (lifecycle.count.get() != 1 || lifecycle.initiatedLocally
                    || !"The Host closed the chat room.".equals(lifecycle.reason.get())) {
                System.out.println("  (diagnostic) lifecycle wrong");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 12: Abrupt room loss
    // ------------------------------------------------------------------

    private static boolean testAbruptLoss() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            abruptStop(r.server);
            if (!lifecycle.await()) {
                System.out.println("  (diagnostic) abrupt lifecycle never fired");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getSystemMessages().contains(
                        "Disconnected from the chat room unexpectedly.");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) unexpected line missing");
                return false;
            }
            sleep(400);
            int unexpectedLines = 0;
            for (String s : view.getSystemMessages()) {
                if (s.equals("Disconnected from the chat room unexpectedly.")) {
                    unexpectedLines++;
                }
            }
            if (unexpectedLines != 1) {
                System.out.println("  (diagnostic) unexpected lines=" + unexpectedLines);
                return false;
            }
            if (!"Disconnected".equals(view.getRoomStatus())) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus());
                return false;
            }
            if (view.isRoomControlsEnabled()) return false;
            if (lifecycle.count.get() != 1 || lifecycle.initiatedLocally) {
                System.out.println("  (diagnostic) lifecycle wrong");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 13: Validation
    // ------------------------------------------------------------------

    /**
     * JTextField normalizes embedded line breaks before the controller reads
     * them, so this raw-tracking field delivers exactly what setText received
     * and exercises the controller's own newline guard directly.
     */
    private static class RawTextField extends javax.swing.JTextField {
        private String raw = "";

        @Override
        public void setText(String text) {
            raw = (text == null) ? "" : text;
            super.setText(text);
        }

        @Override
        public String getText() {
            return raw;
        }
    }

    private static class RawFakeChatView extends FakeChatView {
        private final RawTextField rawField = new RawTextField();

        @Override
        public javax.swing.JTextField getMessageField() {
            return rawField;
        }
    }

    private static boolean testValidation() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new RawFakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.isRoomControlsEnabled(); }
            }, TIMEOUT_MS)) return false;

            StringBuilder longText = new StringBuilder();
            for (int i = 0; i < model.Message.MAX_MESSAGE_LENGTH + 1; i++) {
                longText.append('x');
            }
            String overlength = longText.toString();
            String[] bad = new String[]{"", "   ", "a\nb", "a\rb", overlength};
            String[] tags = new String[]{"empty", "spaces", "newline", "cr", "overlength"};
            for (int i = 0; i < bad.length; i++) {
                view.getMessageField().setText(bad[i]);
                view.getSendButton().doClick();
                sleep(200);
                if (!view.getGroupMessages().isEmpty()) {
                    System.out.println("  (diagnostic) input '" + tags[i]
                        + "' produced chat: " + view.getGroupMessages());
                    return false;
                }
            }
            if (!overlength.equals(view.getMessageField().getText())) {
                System.out.println("  (diagnostic) overlength input not preserved");
                return false;
            }
            boolean warned = false;
            for (String s : view.getSystemMessages()) {
                if (s.contains("too long")) {
                    warned = true;
                }
            }
            if (!warned) {
                System.out.println("  (diagnostic) no overlength warning");
                return false;
            }
            if (!controller.isJoined() || !view.isRoomControlsEnabled()) {
                System.out.println("  (diagnostic) validation disturbed session");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 14: Listener cleanup
    // ------------------------------------------------------------------

    private static boolean testListenerCleanup() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();

            final ActionListener extraSend = new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) { }
            };
            final ActionListener extraField = new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) { }
            };
            final ActionListener extraLeave = new ActionListener() {
                @Override public void actionPerformed(ActionEvent e) { }
            };
            final DocumentListener extraDoc = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { }
                @Override public void removeUpdate(DocumentEvent e) { }
                @Override public void changedUpdate(DocumentEvent e) { }
            };
            view.getSendButton().addActionListener(extraSend);
            view.getMessageField().addActionListener(extraField);
            view.getDisconnectButton().addActionListener(extraLeave);
            view.getMessageField().getDocument().addDocumentListener(extraDoc);

            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            int sendBefore = view.getSendButton().getActionListeners().length;
            int fieldBefore = view.getMessageField().getActionListeners().length;
            int leaveBefore = view.getDisconnectButton().getActionListeners().length;
            int docBefore = ((javax.swing.text.AbstractDocument) view.getMessageField()
                    .getDocument()).getDocumentListeners().length;
            if (sendBefore != 2 || fieldBefore != 2 || leaveBefore != 2) {
                System.out.println("  (diagnostic) unexpected listener setup: send="
                    + sendBefore + " field=" + fieldBefore + " leave=" + leaveBefore
                    + " doc=" + docBefore);
                return false;
            }

            controller.close();
            controller.close();

            ActionListener[] sendAfter = view.getSendButton().getActionListeners();
            ActionListener[] fieldAfter = view.getMessageField().getActionListeners();
            ActionListener[] leaveAfter = view.getDisconnectButton().getActionListeners();
            if (sendAfter.length != 1 || sendAfter[0] != extraSend) {
                System.out.println("  (diagnostic) send listeners wrong");
                return false;
            }
            if (fieldAfter.length != 1 || fieldAfter[0] != extraField) {
                System.out.println("  (diagnostic) field listeners wrong");
                return false;
            }
            if (leaveAfter.length != 1 || leaveAfter[0] != extraLeave) {
                System.out.println("  (diagnostic) leave listeners wrong");
                return false;
            }
            // Plain JTextField documents already carry Swing-internal listeners,
            // so assert relative removal of exactly the owned DocumentListener.
            int docAfter = ((javax.swing.text.AbstractDocument) view.getMessageField()
                    .getDocument()).getDocumentListeners().length;
            if (docAfter != docBefore - 1) {
                System.out.println("  (diagnostic) document listeners: before="
                    + docBefore + " after=" + docAfter);
                return false;
            }
            boolean extraDocPresent = false;
            for (javax.swing.event.DocumentListener d
                    : ((javax.swing.text.AbstractDocument) view.getMessageField()
                        .getDocument()).getDocumentListeners()) {
                if (d == extraDoc) {
                    extraDocPresent = true;
                }
            }
            if (!extraDocPresent) {
                System.out.println("  (diagnostic) unrelated document listener removed");
                return false;
            }
            if (lifecycle.count.get() != 0) {
                System.out.println("  (diagnostic) close notified lifecycle");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 15: Callbacks after close ignored
    // ------------------------------------------------------------------

    private static boolean testLateCallbacksIgnored() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            int groupsBefore = view.getGroupMessages().size();
            int trustedBefore = view.getTrustedSystemMessages().size();
            int systemsBefore = view.getSystemMessages().size();
            String statusBefore = view.getRoomStatus();

            controller.close();
            // Server keeps working, then shuts down: nothing may reach the view.
            r.server.broadcastHostMessage(new Message("Alice", "late hello",
                LocalDateTime.now()));
            sleep(300);
            r.server.stop();
            sleep(600);

            if (view.getGroupMessages().size() != groupsBefore) {
                System.out.println("  (diagnostic) late group message applied");
                return false;
            }
            if (view.getTrustedSystemMessages().size() != trustedBefore) {
                System.out.println("  (diagnostic) late trusted message applied");
                return false;
            }
            if (view.getSystemMessages().size() != systemsBefore) {
                System.out.println("  (diagnostic) late system message applied");
                return false;
            }
            String statusAfter = view.getRoomStatus();
            if (statusAfter == null ? statusBefore != null : !statusAfter.equals(statusBefore)) {
                System.out.println("  (diagnostic) late status change");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 16: Authoritative user list replaces partial state
    // ------------------------------------------------------------------

    private static boolean testAuthoritativeList() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        GroupChatClientSession charlie = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            // Charlie joins while Bob's snapshot settles; either order must
            // converge without duplicates.
            SessionProbe charlieProbe = new SessionProbe();
            charlie = new GroupChatClientSession(LOCALHOST, r.port, "Charlie");
            charlie.setListener(charlieProbe);
            charlie.connect();
            if (!charlieProbe.awaitJoined()) {
                System.out.println("  (diagnostic) Charlie never joined");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    List<String> shown = view.getDisplayedOnlineUsers();
                    return shown.contains("Bob (You)") && shown.contains("Charlie");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) online=" + view.getDisplayedOnlineUsers());
                return false;
            }
            sleep(500);
            List<String> shown = view.getDisplayedOnlineUsers();
            java.util.Set<String> lowered = new java.util.HashSet<>();
            for (String row : shown) {
                String bare = row.replaceAll(" \\(.*\\)$", "");
                if (!lowered.add(bare.toLowerCase(java.util.Locale.ROOT))) {
                    System.out.println("  (diagnostic) duplicate row: " + shown);
                    return false;
                }
            }
            if (!shown.contains("Alice (Host)") || !shown.contains("Bob (You)")
                    || !shown.contains("Charlie")) {
                System.out.println("  (diagnostic) final online=" + shown);
                return false;
            }
            return true;
        } finally {
            if (charlie != null) {
                try {
                    charlie.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 17: Late client sees existing users
    // ------------------------------------------------------------------

    private static boolean testLateControllerSeesAll() throws Exception {
        Room r = null;
        ClientGroupChatController bobController = null;
        ClientGroupChatController charlieController = null;
        try {
            r = startRoom("Alice");
            FakeChatView bobView = new FakeChatView();
            LifecycleProbe bobLifecycle = new LifecycleProbe();
            bobController = newController(bobView, r.port, "Bob", bobLifecycle, true);
            final ClientGroupChatController b = bobController;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return b.isJoined(); }
            }, TIMEOUT_MS)) return false;

            final FakeChatView charlieView = new FakeChatView();
            LifecycleProbe charlieLifecycle = new LifecycleProbe();
            charlieController = newController(charlieView, r.port, "Charlie", charlieLifecycle, true);
            final ClientGroupChatController ch = charlieController;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return ch.isJoined(); }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Charlie never joined");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return charlieView.getDisplayedOnlineUsers().equals(
                        java.util.Arrays.asList("Alice (Host)", "Bob", "Charlie (You)"));
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Charlie online="
                    + charlieView.getDisplayedOnlineUsers());
                return false;
            }
            return true;
        } finally {
            if (bobController != null) {
                bobController.close();
            }
            if (charlieController != null) {
                charlieController.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 18: Incremental events after snapshot
    // ------------------------------------------------------------------

    private static boolean testIncrementalAfterSnapshot() throws Exception {
        Room r = null;
        ClientGroupChatController bobController = null;
        ClientGroupChatController charlieController = null;
        GroupChatClientSession dana = null;
        try {
            r = startRoom("Alice");
            final Room room = r;
            FakeChatView bobView = new FakeChatView();
            LifecycleProbe bobLifecycle = new LifecycleProbe();
            bobController = newController(bobView, room.port, "Bob", bobLifecycle, true);
            final FakeChatView charlieView = new FakeChatView();
            LifecycleProbe charlieLifecycle = new LifecycleProbe();
            charlieController = newController(charlieView, room.port, "Charlie", charlieLifecycle, true);
            final ClientGroupChatController b = bobController;
            final ClientGroupChatController ch = charlieController;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return b.isJoined() && ch.isJoined(); }
            }, TIMEOUT_MS)) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return charlieView.getDisplayedOnlineUsers().contains("Bob");
                }
            }, TIMEOUT_MS)) return false;

            SessionProbe danaProbe = new SessionProbe();
            dana = new GroupChatClientSession(LOCALHOST, room.port, "Dana");
            dana.setListener(danaProbe);
            dana.connect();
            if (!danaProbe.awaitJoined()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return charlieView.getDisplayedOnlineUsers().contains("Dana");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Dana never appeared");
                return false;
            }

            // Bob leaves gracefully via his controller.
            bobView.getDisconnectButton().doClick();
            if (!bobLifecycle.await()) {
                System.out.println("  (diagnostic) Bob leave lifecycle missing");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    List<String> shown = charlieView.getDisplayedOnlineUsers();
                    return shown.equals(java.util.Arrays.asList(
                        "Alice (Host)", "Charlie (You)", "Dana"));
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Charlie online="
                    + charlieView.getDisplayedOnlineUsers());
                return false;
            }
            return true;
        } finally {
            if (dana != null) {
                try {
                    dana.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (bobController != null) {
                bobController.close();
            }
            if (charlieController != null) {
                charlieController.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 19: Invalid snapshot preserves previous state
    // ------------------------------------------------------------------

    private static boolean testInvalidListPreserves() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getDisplayedOnlineUsers().contains("Bob (You)");
                }
            }, TIMEOUT_MS)) return false;
            List<String> before = view.getDisplayedOnlineUsers();

            // Snapshot omitting the local user: warning, state kept, nonterminal.
            controller.onUserList(new UserListMessage(
                java.util.Arrays.asList("Alice", "Charlie"), LocalDateTime.now()));
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    for (String s : view.getSystemMessages()) {
                        if (s.equals("Received an invalid online-user list.")) {
                            return true;
                        }
                    }
                    return false;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) invalid-list warning missing");
                return false;
            }
            sleep(300);
            if (!view.getDisplayedOnlineUsers().equals(before)) {
                System.out.println("  (diagnostic) valid state clobbered: "
                    + view.getDisplayedOnlineUsers());
                return false;
            }
            if (!controller.isJoined() || lifecycle.count.get() != 0) {
                System.out.println("  (diagnostic) single invalid list was terminal");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 20: Repeated invalid snapshots terminate
    // ------------------------------------------------------------------

    private static boolean testRepeatedInvalidLists() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final Room room = r;
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, room.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            for (int i = 0; i < 3; i++) {
                controller.onUserList(new UserListMessage(
                    java.util.Arrays.asList("Alice", "Charlie"), LocalDateTime.now()));
            }
            if (!lifecycle.await()) {
                System.out.println("  (diagnostic) terminal lifecycle missing");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return "Connection error".equals(view.getRoomStatus())
                        && !view.isRoomControlsEnabled();
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus()
                    + " controls=" + view.isRoomControlsEnabled());
                return false;
            }
            if (lifecycle.count.get() != 1 || lifecycle.initiatedLocally
                    || !"Received invalid room state.".equals(lifecycle.reason.get())) {
                System.out.println("  (diagnostic) lifecycle wrong");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) session not disconnected");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 21: Host label comes from snapshot
    // ------------------------------------------------------------------

    private static boolean testHostFromSnapshot() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, r.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Bob never joined");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    List<String> shown = view.getDisplayedOnlineUsers();
                    return shown.contains("Alice (Host)") && shown.contains("Bob (You)");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) online=" + view.getDisplayedOnlineUsers());
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 22: Application-exit shutdown is graceful and idempotent
    // ------------------------------------------------------------------

    private static boolean testApplicationExitShutdown() throws Exception {
        Room r = null;
        ClientGroupChatController controller = null;
        try {
            r = startRoom("Alice");
            final Room room = r;
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, room.port, "Bob", lifecycle, true);
            final ClientGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isJoined(); }
            }, TIMEOUT_MS)) return false;

            controller.leaveRoomForApplicationExit();
            controller.leaveRoomForApplicationExit();
            if (!lifecycle.await()) {
                System.out.println("  (diagnostic) exit lifecycle never fired");
                return false;
            }
            sleep(300);
            if (lifecycle.count.get() != 1 || !lifecycle.initiatedLocally) {
                System.out.println("  (diagnostic) lifecycle wrong");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) server did not remove Bob on exit shutdown");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            stopRoom(r);
        }
    }
}
