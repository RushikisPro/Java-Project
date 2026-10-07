package network;

import model.Message;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Step 9C-1 client session tests. Real GroupChatServer rooms on localhost
 * with ephemeral ports; sessions under test use GroupChatClientSession only.
 */
public class GroupChatClientSessionTest {

    private static final long TIMEOUT_MS = 10000;
    private static final String LOCALHOST = "127.0.0.1";

    // ------------------------------------------------------------------
    // Listener probe
    // ------------------------------------------------------------------

    private static class Probe implements GroupChatClientSession.GroupChatClientSessionListener {
        final CountDownLatch connectingLatch = new CountDownLatch(1);
        final CountDownLatch joinedLatch = new CountDownLatch(1);
        final CountDownLatch rejectedLatch = new CountDownLatch(1);
        final CountDownLatch roomClosedLatch = new CountDownLatch(1);
        final CountDownLatch unexpectedLatch = new CountDownLatch(1);
        final CountDownLatch errorLatch = new CountDownLatch(1);
        final CountDownLatch stoppedLatch = new CountDownLatch(1);
        final CountDownLatch userListLatch = new CountDownLatch(1);
        final AtomicInteger connectingCount = new AtomicInteger(0);
        final AtomicInteger joinedCount = new AtomicInteger(0);
        final AtomicInteger rejectedCount = new AtomicInteger(0);
        final AtomicInteger roomClosedCount = new AtomicInteger(0);
        final AtomicInteger unexpectedCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        final AtomicInteger stoppedCount = new AtomicInteger(0);
        volatile String joinedName;
        volatile String rejectedReason;
        volatile String roomClosedReason;
        volatile Exception error;
        final List<Message> chats = new ArrayList<>();
        final List<model.SystemMessage> systems = new ArrayList<>();
        final List<model.UserListMessage> userLists = new ArrayList<>();
        final Object lock = new Object();

        @Override
        public void onConnecting(String hostAddress, int port) {
            connectingCount.incrementAndGet();
            connectingLatch.countDown();
        }

        @Override
        public void onJoined(String username) {
            joinedName = username;
            joinedCount.incrementAndGet();
            joinedLatch.countDown();
        }

        @Override
        public void onChatMessage(Message message) {
            synchronized (lock) {
                chats.add(message);
            }
        }

        @Override
        public void onSystemMessage(model.SystemMessage message) {
            synchronized (lock) {
                systems.add(message);
            }
        }

        @Override
        public void onUserList(model.UserListMessage message) {
            synchronized (lock) {
                userLists.add(message);
            }
            userListLatch.countDown();
        }

        @Override
        public void onUsernameRejected(String reason) {
            rejectedReason = reason;
            rejectedCount.incrementAndGet();
            rejectedLatch.countDown();
        }

        @Override
        public void onRoomClosed(String reason) {
            roomClosedReason = reason;
            roomClosedCount.incrementAndGet();
            roomClosedLatch.countDown();
        }

        @Override
        public void onDisconnectedUnexpectedly() {
            unexpectedCount.incrementAndGet();
            unexpectedLatch.countDown();
        }

        @Override
        public void onConnectionError(Exception exception) {
            error = exception;
            errorCount.incrementAndGet();
            errorLatch.countDown();
        }

        @Override
        public void onSessionStopped() {
            stoppedCount.incrementAndGet();
            stoppedLatch.countDown();
        }

        int chatCount() {
            synchronized (lock) {
                return chats.size();
            }
        }

        List<Message> chatsSnapshot() {
            synchronized (lock) {
                return new ArrayList<>(chats);
            }
        }

        List<model.SystemMessage> systemsSnapshot() {
            synchronized (lock) {
                return new ArrayList<>(systems);
            }
        }

        int systemCount() {
            synchronized (lock) {
                return systems.size();
            }
        }

        List<model.UserListMessage> userListsSnapshot() {
            synchronized (lock) {
                return new ArrayList<>(userLists);
            }
        }

        boolean awaitUserList() throws InterruptedException {
            return userListLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        boolean awaitJoined() throws InterruptedException {
            return joinedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        boolean awaitStopped() throws InterruptedException {
            return stoppedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static class Room {
        GroupChatServer server;
        GroupChatServer.GroupChatServerListener listener;
        int port;
        final CountDownLatch startedLatch = new CountDownLatch(1);
        volatile int startedPort = -1;
        final List<Message> serverChats = new ArrayList<>();
        final Object lock = new Object();

        int serverChatCount() {
            synchronized (lock) {
                return serverChats.size();
            }
        }
    }

    private static Room startRoom(String host) throws Exception {
        Room r = new Room();
        r.server = new GroupChatServer(0, host);
        r.listener = new GroupChatServer.GroupChatServerListener() {
            @Override public void onRoomStarted(String ip, int port) {
                r.startedPort = port;
                r.startedLatch.countDown();
            }
            @Override public void onUserJoined(String u) { }
            @Override public void onUserLeft(String u) { }
            @Override public void onChatMessage(Message m) {
                synchronized (r.lock) {
                    r.serverChats.add(m);
                }
            }
            @Override public void onRoomError(Exception e) { }
            @Override public void onRoomStopped() { }
        };
        r.server.setListener(r.listener);
        r.server.start();
        if (!r.startedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Room did not start");
        }
        r.port = r.startedPort;
        return r;
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

    private static boolean lastFailed = false;

    private static boolean run(String label, Test t) {
        lastFailed = false;
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
            lastFailed = true;
            System.out.println("FAIL: " + label);
            return false;
        }
    }

    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;

        if (run("client session joins room", new Test() {
            @Override public boolean run() throws Exception { return testJoin(); }
        })) { passed++; } else { failed++; }

        if (run("client receives server broadcast copy", new Test() {
            @Override public boolean run() throws Exception { return testBroadcastCopy(); }
        })) { passed++; } else { failed++; }

        if (run("client receives host broadcast", new Test() {
            @Override public boolean run() throws Exception { return testHostBroadcast(); }
        })) { passed++; } else { failed++; }

        if (run("client observes another user join", new Test() {
            @Override public boolean run() throws Exception { return testObservesJoin(); }
        })) { passed++; } else { failed++; }

        if (run("client observes another user leave", new Test() {
            @Override public boolean run() throws Exception { return testObservesLeave(); }
        })) { passed++; } else { failed++; }

        if (run("duplicate username rejected cleanly", new Test() {
            @Override public boolean run() throws Exception { return testDuplicateRejected(); }
        })) { passed++; } else { failed++; }

        if (run("room close is terminal without duplicate EOF", new Test() {
            @Override public boolean run() throws Exception { return testRoomClose(); }
        })) { passed++; } else { failed++; }

        if (run("unexpected server EOF reported once", new Test() {
            @Override public boolean run() throws Exception { return testUnexpectedEof(); }
        })) { passed++; } else { failed++; }

        if (run("local disconnect is graceful and idempotent", new Test() {
            @Override public boolean run() throws Exception { return testLocalDisconnect(); }
        })) { passed++; } else { failed++; }

        if (run("chat blocked before join", new Test() {
            @Override public boolean run() throws Exception { return testChatBlockedBeforeJoin(); }
        })) { passed++; } else { failed++; }

        if (run("outgoing chat validation", new Test() {
            @Override public boolean run() throws Exception { return testValidation(); }
        })) { passed++; } else { failed++; }

        if (run("three client sessions receive group broadcast", new Test() {
            @Override public boolean run() throws Exception { return testThreeSessions(); }
        })) { passed++; } else { failed++; }

        if (run("concurrent client sessions", new Test() {
            @Override public boolean run() throws Exception { return testConcurrentSessions(); }
        })) { passed++; } else { failed++; }

        if (run("malformed server protocol limit", new Test() {
            @Override public boolean run() throws Exception { return testMalformedServer(); }
        })) { passed++; } else { failed++; }

        if (run("client session receives user list", new Test() {
            @Override public boolean run() throws Exception { return testReceivesUserList(); }
        })) { passed++; } else { failed++; }

        if (run("late client session receives existing users", new Test() {
            @Override public boolean run() throws Exception { return testLateReceivesExisting(); }
        })) { passed++; } else { failed++; }

        if (run("malformed user-list counts as protocol failure", new Test() {
            @Override public boolean run() throws Exception { return testMalformedUserList(); }
        })) { passed++; } else { failed++; }

        System.out.println("\n=== GroupChatClientSessionTest Summary ===");
        System.out.println("PASS: " + passed);
        System.out.println("FAIL: " + failed);
        System.out.println("SKIP: " + skipped);

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Session helpers
    // ------------------------------------------------------------------

    private static GroupChatClientSession newSession(int port, String username, Probe probe) {
        GroupChatClientSession session = new GroupChatClientSession(LOCALHOST, port, username);
        session.setListener(probe);
        return session;
    }

    private static void stopSession(GroupChatClientSession session) {
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception e) {
                // Ignore
            }
        }
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

    /** Minimal raw room peer: plain socket + JOIN, used to trigger server events. */
    private static class RawPeer {
        Socket socket;
        MessageHandler handler;

        void join(int port, String name) throws Exception {
            socket = new Socket(LOCALHOST, port);
            handler = new MessageHandler(socket);
            handler.setMessageListener(new MessageHandler.MessageListener() {
                @Override public void onMessageReceived(String line) { }
                @Override public void onDisconnected() { }
                @Override public void onMessageError(Exception e) { }
            });
            if (!handler.start()) {
                throw new IllegalStateException("Raw peer handler failed");
            }
            if (!handler.sendMessage(MessageCodec.encodeJoin(
                    new model.JoinMessage(name, LocalDateTime.now())))) {
                throw new IllegalStateException("Raw peer JOIN failed");
            }
        }

        boolean sendChat(String sender, String text) {
            return handler.sendMessage(MessageCodec.encode(
                    new Message(sender, text, LocalDateTime.now())));
        }

        boolean sendDisconnect(String sender) {
            return handler.sendMessage(MessageCodec.encodeDisconnect(
                    new model.DisconnectMessage(sender, LocalDateTime.now())));
        }

        void close() {
            if (handler != null) {
                try {
                    handler.stop();
                } catch (Exception e) {
                    // Ignore
                }
            }
            closeQuietly(socket);
        }
    }

    // ------------------------------------------------------------------
    // Test 1: Successful JOIN
    // ------------------------------------------------------------------

    private static boolean testJoin() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        try {
            r = startRoom("Alice");
            Probe probe = new Probe();
            session = newSession(r.port, "Bob", probe);
            if (!session.connect()) {
                System.out.println("  (diagnostic) connect() returned false");
                return false;
            }
            if (!probe.awaitJoined()) {
                System.out.println("  (diagnostic) onJoined never fired");
                return false;
            }
            if (probe.connectingCount.get() != 1) {
                System.out.println("  (diagnostic) onConnecting count=" + probe.connectingCount.get());
                return false;
            }
            if (probe.joinedCount.get() != 1 || !"Bob".equals(probe.joinedName)) {
                System.out.println("  (diagnostic) onJoined count/name wrong");
                return false;
            }
            if (!session.isJoined() || session.isTerminal()) {
                return false;
            }
            if (probe.errorCount.get() != 0) {
                return false;
            }
            final Room room = r;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return room.server.getJoinedUsernames().contains("Alice")
                        && room.server.getJoinedUsernames().contains("Bob");
                }
            }, TIMEOUT_MS)) {
                return false;
            }
            return true;
        } finally {
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 2: Client sends CHAT, receives broadcast copy (no local echo)
    // ------------------------------------------------------------------

    private static boolean testBroadcastCopy() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        try {
            r = startRoom("Alice");
            final Probe probe = new Probe();
            session = newSession(r.port, "Bob", probe);
            session.connect();
            if (!probe.awaitJoined()) return false;

            int before = probe.chatCount();
            if (!session.sendChat("Hello everyone")) {
                System.out.println("  (diagnostic) sendChat returned false");
                return false;
            }
            // No local echo: nothing delivered synchronously by the send call.
            // The single callback must arrive via server broadcast.
            final int base = before;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return probe.chatCount() > base; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) broadcast copy never arrived");
                return false;
            }
            sleep(500);
            if (probe.chatCount() - base != 1) {
                System.out.println("  (diagnostic) duplicate callbacks: " + (probe.chatCount() - base));
                return false;
            }
            Message got = probe.chatsSnapshot().get(probe.chatsSnapshot().size() - 1);
            if (!got.getSender().equals("Bob") || !got.getText().equals("Hello everyone")) {
                return false;
            }
            return true;
        } finally {
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 3: Host message received with exact content
    // ------------------------------------------------------------------

    private static boolean testHostBroadcast() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        try {
            r = startRoom("Alice");
            final Probe probe = new Probe();
            session = newSession(r.port, "Bob", probe);
            session.connect();
            if (!probe.awaitJoined()) return false;

            LocalDateTime ts = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            if (!r.server.broadcastHostMessage(new Message("Alice", "Welcome Bob", ts))) {
                System.out.println("  (diagnostic) broadcastHostMessage failed");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return probe.chatCount() >= 1; }
            }, TIMEOUT_MS)) {
                return false;
            }
            sleep(400);
            if (probe.chatCount() != 1) return false;
            Message got = probe.chatsSnapshot().get(0);
            if (!got.getSender().equals("Alice") || !got.getText().equals("Welcome Bob")
                    || !got.getTimestamp().equals(ts)) {
                System.out.println("  (diagnostic) content mismatch: " + got);
                return false;
            }
            return true;
        } finally {
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 4: Another client joins
    // ------------------------------------------------------------------

    private static boolean testObservesJoin() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        RawPeer charlie = new RawPeer();
        try {
            r = startRoom("Alice");
            final Probe probe = new Probe();
            session = newSession(r.port, "Bob", probe);
            session.connect();
            if (!probe.awaitJoined()) return false;
            final int sysBefore = probe.systemCount();

            charlie.join(r.port, "Charlie");
            final Room room = r;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return probe.systemCount() > sysBefore; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) USER_JOINED for Charlie not observed");
                return false;
            }
            boolean sawCharlie = false;
            for (model.SystemMessage s : probe.systemsSnapshot()) {
                if (s.getEventType() == model.SystemMessage.EventType.USER_JOINED
                        && s.getText().equals("Charlie joined the chat.")) {
                    sawCharlie = true;
                }
            }
            if (!sawCharlie) return false;
            if (!session.isJoined() || session.isTerminal()) return false;
            if (probe.joinedCount.get() != 1) {
                System.out.println("  (diagnostic) spurious onJoined");
                return false;
            }
            return true;
        } finally {
            charlie.close();
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 5: Another client leaves
    // ------------------------------------------------------------------

    private static boolean testObservesLeave() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        RawPeer charlie = new RawPeer();
        try {
            r = startRoom("Alice");
            final Probe probe = new Probe();
            session = newSession(r.port, "Bob", probe);
            session.connect();
            if (!probe.awaitJoined()) return false;

            charlie.join(r.port, "Charlie");
            final Room room = r;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) return false;

            if (!charlie.sendDisconnect("Charlie")) {
                System.out.println("  (diagnostic) Charlie DISCONNECT send failed");
                return false;
            }
            // Wait for Charlie's leave event itself: a count baseline is racy
            // because Charlie's own join SYSTEM may still be in flight.
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    for (model.SystemMessage s : probe.systemsSnapshot()) {
                        if (s.getEventType() == model.SystemMessage.EventType.USER_LEFT
                                && s.getText().equals("Charlie left the chat.")) {
                            return true;
                        }
                    }
                    return false;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) USER_LEFT for Charlie not observed");
                return false;
            }
            boolean sawLeft = false;
            for (model.SystemMessage s : probe.systemsSnapshot()) {
                if (s.getEventType() == model.SystemMessage.EventType.USER_LEFT
                        && s.getText().equals("Charlie left the chat.")) {
                    sawLeft = true;
                }
            }
            if (!sawLeft) return false;
            if (!session.isJoined()) return false;

            // Bob can still send: the server records the chat.
            final int serverChatsBefore = room.serverChatCount();
            if (!session.sendChat("still here")) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.serverChatCount() > serverChatsBefore; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) survivor chat never reached server");
                return false;
            }
            return true;
        } finally {
            charlie.close();
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 6: Duplicate username rejection
    // ------------------------------------------------------------------

    private static boolean testDuplicateRejected() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        try {
            r = startRoom("Alice");
            Probe probe = new Probe();
            session = newSession(r.port, "alice", probe);
            session.connect();
            if (!probe.rejectedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.out.println("  (diagnostic) onUsernameRejected never fired");
                return false;
            }
            if (probe.rejectedCount.get() != 1) return false;
            if (probe.joinedCount.get() != 0) {
                System.out.println("  (diagnostic) onJoined fired for duplicate");
                return false;
            }
            if (session.isJoined()) return false;
            if (probe.errorCount.get() != 0) {
                System.out.println("  (diagnostic) rejection reported as connection error");
                return false;
            }
            if (!probe.awaitStopped()) {
                System.out.println("  (diagnostic) onSessionStopped missing after rejection");
                return false;
            }
            if (probe.stoppedCount.get() != 1) return false;
            if (!session.isTerminal()) return false;
            return true;
        } finally {
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 7: Room closes
    // ------------------------------------------------------------------

    private static boolean testRoomClose() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        try {
            r = startRoom("Alice");
            Probe probe = new Probe();
            session = newSession(r.port, "Bob", probe);
            session.connect();
            if (!probe.awaitJoined()) return false;

            r.server.stop();
            if (!probe.roomClosedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.out.println("  (diagnostic) onRoomClosed never fired");
                return false;
            }
            if (!"The Host closed the chat room.".equals(probe.roomClosedReason)) {
                System.out.println("  (diagnostic) wrong reason: " + probe.roomClosedReason);
                return false;
            }
            if (!probe.awaitStopped()) return false;
            sleep(500);
            if (probe.unexpectedCount.get() != 0) {
                System.out.println("  (diagnostic) unexpected-disconnect after ROOM_CLOSED");
                return false;
            }
            if (probe.errorCount.get() != 0) {
                System.out.println("  (diagnostic) connection-error after ROOM_CLOSED");
                return false;
            }
            if (probe.stoppedCount.get() != 1) {
                System.out.println("  (diagnostic) stopped count=" + probe.stoppedCount.get());
                return false;
            }
            return true;
        } finally {
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 8: Unexpected server EOF via abrupt hook
    // ------------------------------------------------------------------

    private static boolean testUnexpectedEof() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        try {
            r = startRoom("Alice");
            Probe probe = new Probe();
            session = newSession(r.port, "Bob", probe);
            session.connect();
            if (!probe.awaitJoined()) return false;

            r.server.abruptStopForTest();
            if (!probe.unexpectedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.out.println("  (diagnostic) onDisconnectedUnexpectedly never fired");
                return false;
            }
            if (!probe.awaitStopped()) return false;
            sleep(400);
            if (probe.unexpectedCount.get() != 1) {
                System.out.println("  (diagnostic) unexpected count=" + probe.unexpectedCount.get());
                return false;
            }
            if (probe.roomClosedCount.get() != 0) {
                System.out.println("  (diagnostic) ROOM_CLOSED must not fire on abrupt EOF");
                return false;
            }
            if (probe.stoppedCount.get() != 1) return false;
            return true;
        } finally {
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 9: Local disconnect
    // ------------------------------------------------------------------

    private static boolean testLocalDisconnect() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        RawPeer charlie = new RawPeer();
        try {
            r = startRoom("Alice");
            final Room room = r;
            Probe probe = new Probe();
            session = newSession(room.port, "Bob", probe);
            session.connect();
            if (!probe.awaitJoined()) return false;
            charlie.join(room.port, "Charlie");
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) return false;

            session.disconnect();
            if (!probe.awaitStopped()) {
                System.out.println("  (diagnostic) stopped missing after disconnect");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 2; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) server did not remove Bob");
                return false;
            }
            sleep(400);
            if (probe.unexpectedCount.get() != 0 || probe.errorCount.get() != 0) {
                System.out.println("  (diagnostic) local disconnect emitted error callbacks");
                return false;
            }
            if (probe.stoppedCount.get() != 1) return false;

            // Idempotent repeat.
            session.disconnect();
            sleep(200);
            if (probe.stoppedCount.get() != 1) return false;

            // Username freed: a raw peer can now join as Bob.
            RawPeer bobAgain = new RawPeer();
            try {
                bobAgain.join(room.port, "Bob");
                if (!waitFor(new Condition() {
                    @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
                }, TIMEOUT_MS)) {
                    System.out.println("  (diagnostic) username not freed after disconnect");
                    return false;
                }
            } finally {
                bobAgain.close();
            }
            return true;
        } finally {
            charlie.close();
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 10: sendChat before JOIN
    // ------------------------------------------------------------------

    private static boolean testChatBlockedBeforeJoin() throws Exception {
        // Never connected: must refuse.
        GroupChatClientSession idle =
                new GroupChatClientSession(LOCALHOST, 49999, "Bob");
        idle.setListener(new Probe());
        if (idle.sendChat("Hello")) {
            System.out.println("  (diagnostic) sendChat succeeded in NEW state");
            return false;
        }

        // JOINING (server accepts but never confirms): must refuse.
        ServerSocket gate = null;
        GroupChatClientSession joining = null;
        try {
            gate = new ServerSocket(0);
            final int gatePort = gate.getLocalPort();
            final ServerSocket gateRef = gate;
            Thread acceptor = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket s = gateRef.accept();
                        // Hold the connection open without any reply.
                        Thread.sleep(5000);
                        try {
                            s.close();
                        } catch (Exception e) {
                            // Ignore
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            Probe probe = new Probe();
            joining = new GroupChatClientSession(LOCALHOST, gatePort, "Bob");
            joining.setListener(probe);
            if (!joining.connect()) return false;
            // The fixture never sends USER_JOINED, so the session must sit in
            // JOINING: neither joined nor terminal.
            sleep(800);
            if (joining.isJoined() || joining.isTerminal()) {
                System.out.println("  (diagnostic) session left JOINING unexpectedly");
                return false;
            }
            if (joining.sendChat("Hello")) {
                System.out.println("  (diagnostic) sendChat succeeded while JOINING");
                return false;
            }
            return true;
        } finally {
            stopSession(joining);
            closeQuietly(gate);
        }
    }

    // ------------------------------------------------------------------
    // Test 11: Validation
    // ------------------------------------------------------------------

    private static boolean testValidation() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        // Constructor validation.
        try {
            new GroupChatClientSession(null, "Bob");
            System.out.println("  (diagnostic) null host accepted");
            return false;
        } catch (IllegalArgumentException expected) {
        }
        try {
            new GroupChatClientSession("  ", "Bob");
            System.out.println("  (diagnostic) blank host accepted");
            return false;
        } catch (IllegalArgumentException expected) {
        }
        try {
            new GroupChatClientSession(LOCALHOST, 0, "Bob");
            System.out.println("  (diagnostic) bad port accepted");
            return false;
        } catch (IllegalArgumentException expected) {
        }
        try {
            new GroupChatClientSession(LOCALHOST, "  ");
            System.out.println("  (diagnostic) blank username accepted");
            return false;
        } catch (IllegalArgumentException expected) {
        }
        try {
            GroupChatClientSession s = new GroupChatClientSession(LOCALHOST, "Bob");
            s.setListener(null);
            System.out.println("  (diagnostic) null listener accepted");
            return false;
        } catch (IllegalArgumentException expected) {
        }

        try {
            r = startRoom("Alice");
            final Room room = r;
            Probe probe = new Probe();
            session = newSession(room.port, "Bob", probe);
            session.connect();
            if (!probe.awaitJoined()) return false;

            StringBuilder longText = new StringBuilder();
            for (int i = 0; i < model.Message.MAX_MESSAGE_LENGTH + 1; i++) {
                longText.append('x');
            }
            String[] bad = new String[]{
                null, "", "   ", "a\nb", "a\rb", longText.toString()
            };
            for (String text : bad) {
                if (session.sendChat(text)) {
                    System.out.println("  (diagnostic) invalid chat accepted");
                    return false;
                }
            }
            sleep(500);
            if (probe.chatCount() != 0) {
                System.out.println("  (diagnostic) server emitted chat for invalid input");
                return false;
            }
            return true;
        } finally {
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 12: Three sessions, one broadcast each
    // ------------------------------------------------------------------

    private static boolean testThreeSessions() throws Exception {
        Room r = null;
        GroupChatClientSession bobS = null;
        GroupChatClientSession charlieS = null;
        GroupChatClientSession danaS = null;
        try {
            r = startRoom("Alice");
            final Room room = r;
            Probe bobP = new Probe();
            Probe charlieP = new Probe();
            Probe danaP = new Probe();
            bobS = newSession(room.port, "Bob", bobP);
            charlieS = newSession(room.port, "Charlie", charlieP);
            danaS = newSession(room.port, "Dana", danaP);
            bobS.connect();
            charlieS.connect();
            danaS.connect();
            if (!bobP.awaitJoined() || !charlieP.awaitJoined() || !danaP.awaitJoined()) {
                System.out.println("  (diagnostic) not all sessions joined");
                return false;
            }
            if (!bobS.isJoined() || !charlieS.isJoined() || !danaS.isJoined()) return false;

            if (!bobS.sendChat("hello all")) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return bobP.chatCount() >= 1 && charlieP.chatCount() >= 1 && danaP.chatCount() >= 1;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) broadcast missing a recipient");
                return false;
            }
            sleep(600);
            if (bobP.chatCount() != 1 || charlieP.chatCount() != 1 || danaP.chatCount() != 1) {
                System.out.println("  (diagnostic) counts b=" + bobP.chatCount()
                    + " c=" + charlieP.chatCount() + " d=" + danaP.chatCount());
                return false;
            }
            return true;
        } finally {
            stopSession(bobS);
            stopSession(charlieS);
            stopSession(danaS);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 13: Concurrent sends
    // ------------------------------------------------------------------

    private static boolean testConcurrentSessions() throws Exception {
        Room r = null;
        GroupChatClientSession bobS = null;
        GroupChatClientSession charlieS = null;
        GroupChatClientSession danaS = null;
        try {
            r = startRoom("Alice");
            final Room room = r;
            final Probe bobP = new Probe();
            final Probe charlieP = new Probe();
            final Probe danaP = new Probe();
            bobS = newSession(room.port, "Bob", bobP);
            charlieS = newSession(room.port, "Charlie", charlieP);
            danaS = newSession(room.port, "Dana", danaP);
            bobS.connect();
            charlieS.connect();
            danaS.connect();
            if (!bobP.awaitJoined() || !charlieP.awaitJoined() || !danaP.awaitJoined()) return false;

            final int perSender = 5;
            final Set<String> expected = new HashSet<>();
            for (String sender : new String[]{"Bob", "Charlie", "Dana"}) {
                for (int i = 0; i < perSender; i++) {
                    expected.add(sender + "|" + "msg-" + i);
                }
            }
            final GroupChatClientSession bS = bobS;
            final GroupChatClientSession cS = charlieS;
            final GroupChatClientSession dS = danaS;
            Thread tb = new Thread(new Runnable() {
                @Override public void run() {
                    for (int i = 0; i < perSender; i++) {
                        bS.sendChat("msg-" + i);
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
            Thread tc = new Thread(new Runnable() {
                @Override public void run() {
                    for (int i = 0; i < perSender; i++) {
                        cS.sendChat("msg-" + i);
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
            Thread td = new Thread(new Runnable() {
                @Override public void run() {
                    for (int i = 0; i < perSender; i++) {
                        dS.sendChat("msg-" + i);
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
            tb.start();
            tc.start();
            td.start();
            tb.join(TIMEOUT_MS);
            tc.join(TIMEOUT_MS);
            td.join(TIMEOUT_MS);
            if (tb.isAlive() || tc.isAlive() || td.isAlive()) {
                System.out.println("  (diagnostic) sender deadlock");
                return false;
            }
            final int total = perSender * 3;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return bobP.chatCount() >= total && charlieP.chatCount() >= total
                        && danaP.chatCount() >= total;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) counts b=" + bobP.chatCount()
                    + " c=" + charlieP.chatCount() + " d=" + danaP.chatCount());
                return false;
            }
            sleep(800);
            Probe[] probes = new Probe[]{bobP, charlieP, danaP};
            String[] names = new String[]{"Bob", "Charlie", "Dana"};
            for (int k = 0; k < probes.length; k++) {
                if (probes[k].chatCount() != total) {
                    System.out.println("  (diagnostic) " + names[k] + " count="
                        + probes[k].chatCount());
                    return false;
                }
                Set<String> got = new HashSet<>();
                for (Message m : probes[k].chatsSnapshot()) {
                    got.add(m.getSender() + "|" + m.getText());
                }
                if (!got.equals(expected)) {
                    System.out.println("  (diagnostic) " + names[k] + " content mismatch");
                    return false;
                }
            }
            return true;
        } finally {
            stopSession(bobS);
            stopSession(charlieS);
            stopSession(danaS);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 14: Malformed server records via controlled fixture
    // ------------------------------------------------------------------

    private static boolean testMalformedServer() throws Exception {
        ServerSocket gate = null;
        Socket serverSide = null;
        BufferedReader gateIn = null;
        PrintWriter gateOut = null;
        GroupChatClientSession session = null;
        try {
            gate = new ServerSocket(0);
            final int gatePort = gate.getLocalPort();
            final ServerSocket gateRef = gate;
            final Socket[] accepted = new Socket[1];
            Thread acceptor = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        accepted[0] = gateRef.accept();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            Probe probe = new Probe();
            session = new GroupChatClientSession(LOCALHOST, gatePort, "Bob");
            session.setListener(probe);
            if (!session.connect()) return false;

            acceptor.join(TIMEOUT_MS);
            serverSide = accepted[0];
            if (serverSide == null) {
                System.out.println("  (diagnostic) fixture never accepted");
                return false;
            }
            gateIn = new BufferedReader(new InputStreamReader(
                    serverSide.getInputStream(), StandardCharsets.UTF_8));
            gateOut = new PrintWriter(serverSide.getOutputStream(), true);
            serverSide.setSoTimeout((int) TIMEOUT_MS);
            String joinLine = gateIn.readLine();
            if (joinLine == null || !joinLine.startsWith("JOIN|")) {
                System.out.println("  (diagnostic) no JOIN from session: " + joinLine);
                return false;
            }

            // One malformed line must not terminate.
            gateOut.println("GARBAGE-ONE");
            sleep(500);
            if (session.isTerminal()) {
                System.out.println("  (diagnostic) single malformed line terminated session");
                return false;
            }
            if (probe.errorCount.get() != 0 || probe.stoppedCount.get() != 0) {
                System.out.println("  (diagnostic) single malformed line emitted terminal callbacks");
                return false;
            }

            // Two more malformed lines reach the limit of three.
            gateOut.println("GARBAGE-TWO");
            sleep(200);
            gateOut.println("GARBAGE-THREE");
            if (!probe.errorLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.out.println("  (diagnostic) onConnectionError never fired after 3 malformed");
                return false;
            }
            if (probe.errorCount.get() != 1) {
                System.out.println("  (diagnostic) error count=" + probe.errorCount.get());
                return false;
            }
            if (!probe.awaitStopped()) {
                System.out.println("  (diagnostic) stopped missing after malformed limit");
                return false;
            }
            sleep(300);
            if (probe.stoppedCount.get() != 1) return false;
            if (!session.isTerminal()) return false;
            return true;
        } finally {
            stopSession(session);
            closeQuietly(gateIn);
            closeQuietly(gateOut);
            closeQuietly(serverSide);
            closeQuietly(gate);
        }
    }

    // ------------------------------------------------------------------
    // Test 15: Client receives user list
    // ------------------------------------------------------------------

    private static boolean testReceivesUserList() throws Exception {
        Room r = null;
        GroupChatClientSession session = null;
        try {
            r = startRoom("Alice");
            Probe probe = new Probe();
            session = newSession(r.port, "Bob", probe);
            session.connect();
            if (!probe.awaitJoined()) {
                System.out.println("  (diagnostic) Bob never joined");
                return false;
            }
            if (!probe.awaitUserList()) {
                System.out.println("  (diagnostic) Bob never received USER_LIST");
                return false;
            }
            sleep(300);
            if (probe.userListsSnapshot().size() != 1) {
                System.out.println("  (diagnostic) lists=" + probe.userListsSnapshot().size());
                return false;
            }
            List<String> names = probe.userListsSnapshot().get(0).getUsernames();
            if (!names.equals(java.util.Arrays.asList("Alice", "Bob"))) {
                System.out.println("  (diagnostic) Bob list=" + names);
                return false;
            }
            if (!names.get(0).equals("Alice")) {
                System.out.println("  (diagnostic) host not first");
                return false;
            }
            if (!session.isJoined() || session.isTerminal()) {
                System.out.println("  (diagnostic) session disturbed by USER_LIST");
                return false;
            }
            return true;
        } finally {
            stopSession(session);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 16: Late client receives existing users
    // ------------------------------------------------------------------

    private static boolean testLateReceivesExisting() throws Exception {
        Room r = null;
        GroupChatClientSession bob = null;
        GroupChatClientSession charlie = null;
        try {
            r = startRoom("Alice");
            Probe bobProbe = new Probe();
            bob = newSession(r.port, "Bob", bobProbe);
            bob.connect();
            if (!bobProbe.awaitJoined()) return false;
            if (!bobProbe.awaitUserList()) return false;

            Probe charlieProbe = new Probe();
            charlie = newSession(r.port, "Charlie", charlieProbe);
            charlie.connect();
            if (!charlieProbe.awaitJoined()) {
                System.out.println("  (diagnostic) Charlie never joined");
                return false;
            }
            if (!charlieProbe.awaitUserList()) {
                System.out.println("  (diagnostic) Charlie never received USER_LIST");
                return false;
            }
            List<String> names = charlieProbe.userListsSnapshot().get(0).getUsernames();
            if (!names.equals(java.util.Arrays.asList("Alice", "Bob", "Charlie"))) {
                System.out.println("  (diagnostic) Charlie list=" + names);
                return false;
            }
            return true;
        } finally {
            stopSession(bob);
            stopSession(charlie);
            stopRoom(r);
        }
    }

    // ------------------------------------------------------------------
    // Test 17: Malformed USER_LIST counts as protocol failure
    // ------------------------------------------------------------------

    private static boolean testMalformedUserList() throws Exception {
        ServerSocket gate = null;
        Socket serverSide = null;
        BufferedReader gateIn = null;
        PrintWriter gateOut = null;
        GroupChatClientSession session = null;
        try {
            gate = new ServerSocket(0);
            final int gatePort = gate.getLocalPort();
            final ServerSocket gateRef = gate;
            final Socket[] accepted = new Socket[1];
            Thread acceptor = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        accepted[0] = gateRef.accept();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            Probe probe = new Probe();
            session = new GroupChatClientSession(LOCALHOST, gatePort, "Bob");
            session.setListener(probe);
            if (!session.connect()) return false;

            acceptor.join(TIMEOUT_MS);
            serverSide = accepted[0];
            if (serverSide == null) {
                System.out.println("  (diagnostic) fixture never accepted");
                return false;
            }
            gateIn = new BufferedReader(new InputStreamReader(
                    serverSide.getInputStream(), StandardCharsets.UTF_8));
            gateOut = new PrintWriter(serverSide.getOutputStream(), true);
            serverSide.setSoTimeout((int) TIMEOUT_MS);
            String joinLine = gateIn.readLine();
            if (joinLine == null || !joinLine.startsWith("JOIN|")) {
                System.out.println("  (diagnostic) no JOIN from session: " + joinLine);
                return false;
            }

            // One malformed USER_LIST must not terminate.
            gateOut.println("USER_LIST|!!!not-base64!!!|2026-10-03T14:32:10");
            sleep(500);
            if (session.isTerminal()) {
                System.out.println("  (diagnostic) single malformed USER_LIST terminated session");
                return false;
            }
            if (probe.errorCount.get() != 0 || probe.stoppedCount.get() != 0) {
                System.out.println("  (diagnostic) single malformed USER_LIST emitted terminal callbacks");
                return false;
            }
            if (!probe.userListsSnapshot().isEmpty()) {
                System.out.println("  (diagnostic) malformed USER_LIST was forwarded");
                return false;
            }

            // Two more malformed USER_LIST records reach the limit of three.
            gateOut.println("USER_LIST|!!!not-base64!!!|2026-10-03T14:32:10");
            sleep(200);
            gateOut.println("USER_LIST|!!!not-base64!!!|2026-10-03T14:32:10");
            if (!probe.errorLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.out.println("  (diagnostic) onConnectionError never fired after 3 malformed");
                return false;
            }
            if (probe.errorCount.get() != 1) {
                System.out.println("  (diagnostic) error count=" + probe.errorCount.get());
                return false;
            }
            if (!probe.awaitStopped()) {
                System.out.println("  (diagnostic) stopped missing after malformed limit");
                return false;
            }
            sleep(300);
            if (probe.stoppedCount.get() != 1) return false;
            if (!session.isTerminal()) return false;
            return true;
        } finally {
            stopSession(session);
            closeQuietly(gateIn);
            closeQuietly(gateOut);
            closeQuietly(serverSide);
            closeQuietly(gate);
        }
    }
}
