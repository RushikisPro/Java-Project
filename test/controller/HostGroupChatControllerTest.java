package controller;

import model.Message;
import model.SystemMessage;
import network.GroupChatClientSession;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Step 9C-3C host group controller tests. The controller owns a real
 * GroupChatServer on ephemeral ports; FakeChatView records presentation.
 */
public class HostGroupChatControllerTest {

    private static final long TIMEOUT_MS = 10000;
    private static final String LOCALHOST = "127.0.0.1";

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static class LifecycleProbe
            implements HostGroupChatController.HostGroupChatLifecycleListener {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicReference<String> reason = new AtomicReference<>();
        volatile boolean initiatedLocally;

        @Override
        public void onHostRoomEnded(String reason, boolean initiatedLocally) {
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
        final CountDownLatch roomClosedLatch = new CountDownLatch(1);
        final AtomicInteger joinedCount = new AtomicInteger(0);
        final AtomicInteger roomClosedCount = new AtomicInteger(0);
        volatile String roomClosedReason;
        final List<Message> chats = new java.util.ArrayList<>();
        final Object lock = new Object();

        @Override public void onConnecting(String h, int p) { }
        @Override public void onJoined(String u) {
            joinedCount.incrementAndGet();
            joinedLatch.countDown();
        }
        @Override public void onChatMessage(Message m) {
            synchronized (lock) {
                chats.add(m);
            }
        }
        @Override public void onSystemMessage(SystemMessage m) { }
        @Override public void onUserList(model.UserListMessage m) { }
        @Override public void onUsernameRejected(String r) { }
        @Override public void onRoomClosed(String r) {
            roomClosedReason = r;
            roomClosedCount.incrementAndGet();
            roomClosedLatch.countDown();
        }
        @Override public void onDisconnectedUnexpectedly() { }
        @Override public void onConnectionError(Exception e) { }
        @Override public void onSessionStopped() { }

        boolean awaitJoined() throws InterruptedException {
            return joinedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        int chatCount() {
            synchronized (lock) {
                return chats.size();
            }
        }
    }

    /** JTextField normalizes embedded line breaks, so this raw-tracking field
     * delivers exactly what setText received for validation tests. */
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

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                // Ignore
            }
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

        if (run("host controller initial state", new Test() {
            @Override public boolean run() throws Exception { return testInitialState(); }
        })) { passed++; } else { failed++; }

        if (run("host room starts and enables controls", new Test() {
            @Override public boolean run() throws Exception { return testRoomStarts(); }
        })) { passed++; } else { failed++; }

        if (run("host sends with zero clients", new Test() {
            @Override public boolean run() throws Exception { return testHostSendsAlone(); }
        })) { passed++; } else { failed++; }

        if (run("host controller handles client join", new Test() {
            @Override public boolean run() throws Exception { return testClientJoin(); }
        })) { passed++; } else { failed++; }

        if (run("host controller displays client message", new Test() {
            @Override public boolean run() throws Exception { return testClientMessage(); }
        })) { passed++; } else { failed++; }

        if (run("host controller handles client leave", new Test() {
            @Override public boolean run() throws Exception { return testClientLeave(); }
        })) { passed++; } else { failed++; }

        if (run("host broadcast reaches client", new Test() {
            @Override public boolean run() throws Exception { return testHostBroadcast(); }
        })) { passed++; } else { failed++; }

        if (run("host close cancellation", new Test() {
            @Override public boolean run() throws Exception { return testCloseCancelled(); }
        })) { passed++; } else { failed++; }

        if (run("host closes room gracefully", new Test() {
            @Override public boolean run() throws Exception { return testCloseConfirmed(); }
        })) { passed++; } else { failed++; }

        if (run("host controller validates outgoing text", new Test() {
            @Override public boolean run() throws Exception { return testValidation(); }
        })) { passed++; } else { failed++; }

        if (run("host controller cleans listeners", new Test() {
            @Override public boolean run() throws Exception { return testListenerCleanup(); }
        })) { passed++; } else { failed++; }

        if (run("host controller ignores late callbacks", new Test() {
            @Override public boolean run() throws Exception { return testLateCallbacksIgnored(); }
        })) { passed++; } else { failed++; }

        if (run("host controller handles bind failure", new Test() {
            @Override public boolean run() throws Exception { return testBindFailure(); }
        })) { passed++; } else { failed++; }

        if (run("host application-exit shutdown is graceful", new Test() {
            @Override public boolean run() throws Exception { return testApplicationExitShutdown(); }
        })) { passed++; } else { failed++; }

        System.out.println("\n=== HostGroupChatControllerTest Summary ===");
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

    private static HostGroupChatController newController(
            FakeChatView view, int port, String hostUsername,
            LifecycleProbe lifecycle, boolean confirmClose) {
        RoomCloseConfirmation confirmation = new RoomCloseConfirmation() {
            @Override
            public boolean confirm(java.awt.Component parent) {
                return confirmClose;
            }
        };
        return new HostGroupChatController(
                view, port, hostUsername, lifecycle, confirmation);
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
    // Test 1: Initial state
    // ------------------------------------------------------------------

    private static boolean testInitialState() throws Exception {
        HostGroupChatController controller = null;
        try {
            FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);

            if (!"Close Room".equals(view.getDisconnectButton().getText())) {
                System.out.println("  (diagnostic) button=" + view.getDisconnectButton().getText());
                return false;
            }
            if (!"Starting room...".equals(view.getRoomStatus())) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus());
                return false;
            }
            if (view.isRoomControlsEnabled()) {
                System.out.println("  (diagnostic) controls enabled too early");
                return false;
            }
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) room never started");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 2: Room starts
    // ------------------------------------------------------------------

    private static boolean testRoomStarts() throws Exception {
        HostGroupChatController controller = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;
            if (controller.getPort() <= 0) {
                System.out.println("  (diagnostic) port=" + controller.getPort());
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    String status = view.getRoomStatus();
                    return status != null && status.startsWith("Waiting for users at ")
                        && view.isRoomControlsEnabled();
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus());
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getDisplayedOnlineUsers().contains("Alice (You, Host)");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) online=" + view.getDisplayedOnlineUsers());
                return false;
            }
            if (!controller.getOnlineUserSnapshot().contains("Alice")) {
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 3: Host sends with zero clients
    // ------------------------------------------------------------------

    private static boolean testHostSendsAlone() throws Exception {
        HostGroupChatController controller = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.isRoomControlsEnabled(); }
            }, TIMEOUT_MS)) return false;

            view.getMessageField().setText("Hello empty room");
            view.getSendButton().doClick();
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() >= 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) host message not displayed with zero clients");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getMessageField().getText().isEmpty(); }
            }, TIMEOUT_MS)) return false;
            sleep(400);
            List<String> lines = view.getGroupMessages();
            if (lines.size() != 1) {
                System.out.println("  (diagnostic) lines=" + lines.size());
                return false;
            }
            if (!lines.get(0).contains("You") || !lines.get(0).contains("Hello empty room")) {
                System.out.println("  (diagnostic) line=" + lines.get(0));
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 4: Client joins
    // ------------------------------------------------------------------

    private static boolean testClientJoin() throws Exception {
        HostGroupChatController controller = null;
        GroupChatClientSession bob = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;

            SessionProbe bobProbe = new SessionProbe();
            bob = new GroupChatClientSession(LOCALHOST, controller.getPort(), "Bob");
            bob.setListener(bobProbe);
            bob.connect();
            if (!bobProbe.awaitJoined()) {
                System.out.println("  (diagnostic) Bob never joined");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return countTrusted(view, SystemMessage.EventType.USER_JOINED) >= 1;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) host never rendered join");
                return false;
            }
            sleep(400);
            if (countTrusted(view, SystemMessage.EventType.USER_JOINED) != 1) {
                System.out.println("  (diagnostic) duplicate join lines");
                return false;
            }
            boolean sawBob = false;
            for (SystemMessage m : view.getTrustedSystemMessages()) {
                if (m.getText().equals("Bob joined the chat.")) {
                    sawBob = true;
                }
            }
            if (!sawBob) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getDisplayedOnlineUsers().contains("Bob");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) online=" + view.getDisplayedOnlineUsers());
                return false;
            }
            if (lifecycle.count.get() != 0) {
                System.out.println("  (diagnostic) lifecycle fired on join");
                return false;
            }
            return true;
        } finally {
            if (bob != null) {
                try {
                    bob.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 5: Client message displayed
    // ------------------------------------------------------------------

    private static boolean testClientMessage() throws Exception {
        HostGroupChatController controller = null;
        GroupChatClientSession bob = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;

            SessionProbe bobProbe = new SessionProbe();
            bob = new GroupChatClientSession(LOCALHOST, controller.getPort(), "Bob");
            bob.setListener(bobProbe);
            bob.connect();
            if (!bobProbe.awaitJoined()) return false;
            if (!bob.sendChat("Hi Alice")) return false;

            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() >= 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) client message not displayed");
                return false;
            }
            sleep(400);
            List<String> lines = view.getGroupMessages();
            if (lines.size() != 1) {
                System.out.println("  (diagnostic) lines=" + lines.size());
                return false;
            }
            String line = lines.get(0);
            if (!line.contains("Bob") || !line.contains("Hi Alice") || line.contains("You")) {
                System.out.println("  (diagnostic) line=" + line);
                return false;
            }
            return true;
        } finally {
            if (bob != null) {
                try {
                    bob.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 6: Client leaves, host stays
    // ------------------------------------------------------------------

    private static boolean testClientLeave() throws Exception {
        HostGroupChatController controller = null;
        GroupChatClientSession bob = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;

            SessionProbe bobProbe = new SessionProbe();
            bob = new GroupChatClientSession(LOCALHOST, controller.getPort(), "Bob");
            bob.setListener(bobProbe);
            bob.connect();
            if (!bobProbe.awaitJoined()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getDisplayedOnlineUsers().contains("Bob");
                }
            }, TIMEOUT_MS)) return false;
            final int groupsBefore = view.getGroupMessages().size();

            bob.disconnect();
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return countTrusted(view, SystemMessage.EventType.USER_LEFT) >= 1;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) leave never rendered");
                return false;
            }
            sleep(400);
            if (countTrusted(view, SystemMessage.EventType.USER_LEFT) != 1) {
                System.out.println("  (diagnostic) duplicate leave lines");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return !view.getDisplayedOnlineUsers().contains("Bob");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Bob still listed");
                return false;
            }
            if (lifecycle.count.get() != 0) {
                System.out.println("  (diagnostic) host terminated on client leave");
                return false;
            }
            // Host remains active.
            view.getMessageField().setText("still hosting");
            view.getSendButton().doClick();
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() > groupsBefore; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) host cannot send after leave");
                return false;
            }
            return true;
        } finally {
            if (bob != null) {
                try {
                    bob.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 7: Host broadcast reaches client
    // ------------------------------------------------------------------

    private static boolean testHostBroadcast() throws Exception {
        HostGroupChatController controller = null;
        GroupChatClientSession bob = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;

            final SessionProbe bobProbe = new SessionProbe();
            bob = new GroupChatClientSession(LOCALHOST, controller.getPort(), "Bob");
            bob.setListener(bobProbe);
            bob.connect();
            if (!bobProbe.awaitJoined()) return false;

            view.getMessageField().setText("Welcome Bob");
            view.getSendButton().doClick();
            if (!waitFor(new Condition() {
                @Override public boolean check() { return bobProbe.chatCount() >= 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) client never received host message");
                return false;
            }
            Message got;
            synchronized (bobProbe.lock) {
                got = bobProbe.chats.get(bobProbe.chats.size() - 1);
            }
            if (!got.getSender().equals("Alice") || !got.getText().equals("Welcome Bob")) {
                System.out.println("  (diagnostic) wrong content: " + got);
                return false;
            }
            return true;
        } finally {
            if (bob != null) {
                try {
                    bob.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 8: Cancel close
    // ------------------------------------------------------------------

    private static boolean testCloseCancelled() throws Exception {
        HostGroupChatController controller = null;
        GroupChatClientSession bob = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, false);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;

            final SessionProbe bobProbe = new SessionProbe();
            bob = new GroupChatClientSession(LOCALHOST, controller.getPort(), "Bob");
            bob.setListener(bobProbe);
            bob.connect();
            if (!bobProbe.awaitJoined()) return false;

            view.getDisconnectButton().doClick();
            sleep(500);
            if (lifecycle.count.get() != 0) {
                System.out.println("  (diagnostic) lifecycle fired on cancel");
                return false;
            }
            if (!view.isRoomControlsEnabled()) {
                System.out.println("  (diagnostic) controls disabled after cancel");
                return false;
            }
            // Messaging continues both ways.
            final int groupsBefore = view.getGroupMessages().size();
            if (!bob.sendChat("still here")) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() > groupsBefore; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) messaging broken after cancel");
                return false;
            }
            return true;
        } finally {
            if (bob != null) {
                try {
                    bob.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 9: Confirm close
    // ------------------------------------------------------------------

    private static boolean testCloseConfirmed() throws Exception {
        HostGroupChatController controller = null;
        GroupChatClientSession bob = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;
            final int port = controller.getPort();

            final SessionProbe bobProbe = new SessionProbe();
            bob = new GroupChatClientSession(LOCALHOST, port, "Bob");
            bob.setListener(bobProbe);
            bob.connect();
            if (!bobProbe.awaitJoined()) return false;

            view.getDisconnectButton().doClick();
            if (!lifecycle.await()) {
                System.out.println("  (diagnostic) close lifecycle never fired");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return view.getSystemMessages().contains("You closed the chat room.");
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) close line missing");
                return false;
            }
            sleep(400);
            int closeLines = 0;
            for (String s : view.getSystemMessages()) {
                if (s.equals("You closed the chat room.")) {
                    closeLines++;
                }
            }
            if (closeLines != 1) {
                System.out.println("  (diagnostic) close lines=" + closeLines);
                return false;
            }
            if (!"Room closed".equals(view.getRoomStatus()) || view.isRoomControlsEnabled()) {
                System.out.println("  (diagnostic) status=" + view.getRoomStatus());
                return false;
            }
            if (lifecycle.count.get() != 1 || !lifecycle.initiatedLocally
                    || !"You closed the chat room.".equals(lifecycle.reason.get())) {
                System.out.println("  (diagnostic) lifecycle wrong");
                return false;
            }
            // Client received the network ROOM_CLOSED before teardown.
            if (!bobProbe.roomClosedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.out.println("  (diagnostic) client missed ROOM_CLOSED");
                return false;
            }
            // Room no longer accepts connections.
            boolean refused = false;
            Socket probe = null;
            try {
                probe = new Socket(LOCALHOST, port);
            } catch (java.io.IOException expected) {
                refused = true;
            } finally {
                closeQuietly(probe);
            }
            if (!refused) {
                System.out.println("  (diagnostic) room still accepts connections");
                return false;
            }
            return true;
        } finally {
            if (bob != null) {
                try {
                    bob.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 10: Validation
    // ------------------------------------------------------------------

    private static boolean testValidation() throws Exception {
        HostGroupChatController controller = null;
        try {
            final FakeChatView view = new RawFakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
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
                    System.out.println("  (diagnostic) input '" + tags[i] + "' produced chat");
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
            if (lifecycle.count.get() != 0 || !view.isRoomControlsEnabled()) {
                System.out.println("  (diagnostic) validation disturbed room");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 11: Listener cleanup
    // ------------------------------------------------------------------

    private static boolean testListenerCleanup() throws Exception {
        HostGroupChatController controller = null;
        try {
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

            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;

            int sendBefore = view.getSendButton().getActionListeners().length;
            int fieldBefore = view.getMessageField().getActionListeners().length;
            int leaveBefore = view.getDisconnectButton().getActionListeners().length;
            int docBefore = ((javax.swing.text.AbstractDocument) view.getMessageField()
                    .getDocument()).getDocumentListeners().length;
            if (sendBefore != 2 || fieldBefore != 2 || leaveBefore != 2) {
                System.out.println("  (diagnostic) unexpected setup: send=" + sendBefore
                    + " field=" + fieldBefore + " leave=" + leaveBefore + " doc=" + docBefore);
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
            int docAfter = ((javax.swing.text.AbstractDocument) view.getMessageField()
                    .getDocument()).getDocumentListeners().length;
            if (docAfter != docBefore - 1) {
                System.out.println("  (diagnostic) doc listeners: before="
                    + docBefore + " after=" + docAfter);
                return false;
            }
            boolean extraDocPresent = false;
            for (DocumentListener d
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
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 12: Late callbacks ignored
    // ------------------------------------------------------------------

    private static boolean testLateCallbacksIgnored() throws Exception {
        HostGroupChatController controller = null;
        GroupChatClientSession bob = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;

            SessionProbe bobProbe = new SessionProbe();
            bob = new GroupChatClientSession(LOCALHOST, controller.getPort(), "Bob");
            bob.setListener(bobProbe);
            bob.connect();
            if (!bobProbe.awaitJoined()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return view.getGroupMessages().size() >= 0; }
            }, TIMEOUT_MS)) return false;

            int groupsBefore = view.getGroupMessages().size();
            int trustedBefore = view.getTrustedSystemMessages().size();
            int systemsBefore = view.getSystemMessages().size();

            controller.close();
            // Client traffic after close must not reach the closed view.
            bob.sendChat("late hello");
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
            // Host send after close is ignored as well.
            view.getMessageField().setText("late host send");
            view.getSendButton().doClick();
            sleep(300);
            if (view.getGroupMessages().size() != groupsBefore) {
                System.out.println("  (diagnostic) post-close host send displayed");
                return false;
            }
            return true;
        } finally {
            if (bob != null) {
                try {
                    bob.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 13: Bind failure surfaces as room error
    // ------------------------------------------------------------------

    private static boolean testBindFailure() throws Exception {
        ServerSocket occupier = null;
        HostGroupChatController controller = null;
        try {
            occupier = new ServerSocket(0);
            int occupiedPort = occupier.getLocalPort();

            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, occupiedPort, "Alice", lifecycle, true);

            if (!lifecycle.await()) {
                System.out.println("  (diagnostic) error lifecycle never fired");
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
            boolean warned = false;
            for (String s : view.getSystemMessages()) {
                if (s.equals("Unable to continue hosting the chat room.")) {
                    warned = true;
                }
            }
            if (!warned) {
                System.out.println("  (diagnostic) error line missing");
                return false;
            }
            sleep(400);
            if (lifecycle.count.get() != 1 || lifecycle.initiatedLocally
                    || !"Unable to continue hosting the chat room.".equals(lifecycle.reason.get())) {
                System.out.println("  (diagnostic) lifecycle wrong");
                return false;
            }
            return true;
        } finally {
            if (controller != null) {
                controller.close();
            }
            closeQuietly(occupier);
            sleep(300);
        }
    }

    // ------------------------------------------------------------------
    // Test 14: Application-exit shutdown is graceful and idempotent
    // ------------------------------------------------------------------

    private static boolean testApplicationExitShutdown() throws Exception {
        HostGroupChatController controller = null;
        GroupChatClientSession bob = null;
        try {
            final FakeChatView view = new FakeChatView();
            LifecycleProbe lifecycle = new LifecycleProbe();
            controller = newController(view, 0, "Alice", lifecycle, true);
            final HostGroupChatController c = controller;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return c.isRoomStarted(); }
            }, TIMEOUT_MS)) return false;

            final SessionProbe bobProbe = new SessionProbe();
            bob = new GroupChatClientSession(LOCALHOST, controller.getPort(), "Bob");
            bob.setListener(bobProbe);
            bob.connect();
            if (!bobProbe.awaitJoined()) return false;

            controller.closeRoomForApplicationExit();
            controller.closeRoomForApplicationExit();
            if (!lifecycle.await()) {
                System.out.println("  (diagnostic) exit lifecycle never fired");
                return false;
            }
            sleep(400);
            if (lifecycle.count.get() != 1 || !lifecycle.initiatedLocally) {
                System.out.println("  (diagnostic) lifecycle wrong");
                return false;
            }
            if (!bobProbe.roomClosedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.out.println("  (diagnostic) client missed ROOM_CLOSED on exit shutdown");
                return false;
            }
            return true;
        } finally {
            if (bob != null) {
                try {
                    bob.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (controller != null) {
                controller.close();
            }
            sleep(300);
        }
    }
}
