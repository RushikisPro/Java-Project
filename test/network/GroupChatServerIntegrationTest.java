package network;

import model.DisconnectMessage;
import model.JoinMessage;
import model.Message;
import model.SystemMessage;

import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Step 9B network-only group-chat integration tests.
 * Simulated clients use real Sockets + MessageHandler + MessageCodec against
 * GroupChatServer on localhost with ephemeral ports. No Swing involved.
 */
public class GroupChatServerIntegrationTest {

    private static final long TIMEOUT_MS = 10000;
    private static final String LOCALHOST = "127.0.0.1";

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static class RoomListener implements GroupChatServer.GroupChatServerListener {
        final CountDownLatch startedLatch = new CountDownLatch(1);
        final CountDownLatch stoppedLatch = new CountDownLatch(1);
        volatile int startedPort = -1;
        final List<String> joined = new ArrayList<>();
        final List<String> left = new ArrayList<>();
        final List<Message> chats = new ArrayList<>();
        final AtomicInteger errorCount = new AtomicInteger(0);
        final AtomicInteger stoppedCount = new AtomicInteger(0);
        final Object lock = new Object();

        @Override
        public void onRoomStarted(String ipAddress, int port) {
            startedPort = port;
            startedLatch.countDown();
        }

        @Override
        public void onUserJoined(String username) {
            synchronized (lock) {
                joined.add(username);
            }
        }

        @Override
        public void onUserLeft(String username) {
            synchronized (lock) {
                left.add(username);
            }
        }

        @Override
        public void onChatMessage(Message message) {
            synchronized (lock) {
                chats.add(message);
            }
        }

        @Override
        public void onRoomError(Exception exception) {
            errorCount.incrementAndGet();
        }

        @Override
        public void onRoomStopped() {
            stoppedCount.incrementAndGet();
            stoppedLatch.countDown();
        }

        boolean awaitStarted() throws InterruptedException {
            return startedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        boolean awaitStopped() throws InterruptedException {
            return stoppedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        List<String> joinedSnapshot() {
            synchronized (lock) {
                return new ArrayList<>(joined);
            }
        }

        List<String> leftSnapshot() {
            synchronized (lock) {
                return new ArrayList<>(left);
            }
        }

        List<Message> chatsSnapshot() {
            synchronized (lock) {
                return new ArrayList<>(chats);
            }
        }
    }

    private static class TestClient {
        final String name;
        Socket socket;
        MessageHandler handler;
        final List<Message> chats = new ArrayList<>();
        final List<SystemMessage> systems = new ArrayList<>();
        final List<model.UserListMessage> userLists = new ArrayList<>();
        final AtomicInteger decodeErrors = new AtomicInteger(0);
        final CountDownLatch disconnectedLatch = new CountDownLatch(1);
        final Object lock = new Object();

        TestClient(String name) {
            this.name = name;
        }

        void connect(int port) throws Exception {
            socket = new Socket(LOCALHOST, port);
            handler = new MessageHandler(socket);
            handler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    try {
                        MessageCodec.ProtocolType type = MessageCodec.detectType(line);
                        if (type == MessageCodec.ProtocolType.CHAT) {
                            Message m = MessageCodec.decode(line);
                            synchronized (lock) {
                                chats.add(m);
                            }
                        } else if (type == MessageCodec.ProtocolType.SYSTEM) {
                            SystemMessage s = MessageCodec.decodeSystem(line);
                            synchronized (lock) {
                                systems.add(s);
                            }
                        } else if (type == MessageCodec.ProtocolType.USER_LIST) {
                            model.UserListMessage u = MessageCodec.decodeUserList(line);
                            synchronized (lock) {
                                userLists.add(u);
                            }
                        }
                    } catch (Exception e) {
                        decodeErrors.incrementAndGet();
                    }
                }

                @Override
                public void onDisconnected() {
                    disconnectedLatch.countDown();
                }

                @Override
                public void onMessageError(Exception exception) {
                    // Informational only
                }
            });
            if (!handler.start()) {
                throw new IllegalStateException("Client handler failed to start");
            }
        }

        boolean sendJoin() {
            return handler.sendMessage(MessageCodec.encodeJoin(new JoinMessage(name)));
        }

        boolean sendChat(String text) {
            return handler.sendMessage(MessageCodec.encode(
                    new Message(name, text, LocalDateTime.now())));
        }

        boolean sendChatMessage(Message message) {
            return handler.sendMessage(MessageCodec.encode(message));
        }

        boolean sendDisconnect() {
            return handler.sendMessage(MessageCodec.encodeDisconnect(
                    new DisconnectMessage(name, LocalDateTime.now())));
        }

        boolean sendRaw(String line) {
            return handler.sendMessage(line);
        }

        void abruptClose() {
            closeQuietly(socket);
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

        boolean awaitDisconnected(long timeoutMs) throws InterruptedException {
            return disconnectedLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        int chatCount() {
            synchronized (lock) {
                return chats.size();
            }
        }

        int systemCount() {
            synchronized (lock) {
                return systems.size();
            }
        }

        List<Message> chatsSnapshot() {
            synchronized (lock) {
                return new ArrayList<>(chats);
            }
        }

        List<SystemMessage> systemsSnapshot() {
            synchronized (lock) {
                return new ArrayList<>(systems);
            }
        }

        List<SystemMessage> systemsOfType(SystemMessage.EventType type) {
            List<SystemMessage> out = new ArrayList<>();
            synchronized (lock) {
                for (SystemMessage s : systems) {
                    if (s.getEventType() == type) {
                        out.add(s);
                    }
                }
            }
            return out;
        }

        int userListCount() {
            synchronized (lock) {
                return userLists.size();
            }
        }

        List<model.UserListMessage> userListsSnapshot() {
            synchronized (lock) {
                return new ArrayList<>(userLists);
            }
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

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                // Ignore
            }
        }
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

    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;

        passed += run("three clients join group room", new Test() {
            @Override public boolean run() throws Exception { return testThreeUserJoin(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("client message broadcasts to all", new Test() {
            @Override public boolean run() throws Exception { return testClientBroadcast(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("host message broadcasts to clients", new Test() {
            @Override public boolean run() throws Exception { return testHostBroadcast(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("one client leaves room stays active", new Test() {
            @Override public boolean run() throws Exception { return testClientLeaves(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("new client joins active room", new Test() {
            @Override public boolean run() throws Exception { return testFourthJoinsLater(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("duplicate usernames rejected", new Test() {
            @Override public boolean run() throws Exception { return testDuplicateRejected(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("sender spoofing rejected", new Test() {
            @Override public boolean run() throws Exception { return testSpoofingRejected(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("client system forgery rejected", new Test() {
            @Override public boolean run() throws Exception { return testSystemForgeryRejected(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("unexpected client EOF removes only client", new Test() {
            @Override public boolean run() throws Exception { return testUnexpectedEof(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("host closes group room", new Test() {
            @Override public boolean run() throws Exception { return testHostClosesRoom(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("concurrent group broadcasts", new Test() {
            @Override public boolean run() throws Exception { return testConcurrentBroadcasts(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("malformed handshake isolated", new Test() {
            @Override public boolean run() throws Exception { return testMalformedHandshake(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("late client receives authoritative user list", new Test() {
            @Override public boolean run() throws Exception { return testLateClientUserList(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("user list snapshot is unicast", new Test() {
            @Override public boolean run() throws Exception { return testUserListUnicast(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("user list excludes unjoined connections", new Test() {
            @Override public boolean run() throws Exception { return testUserListExcludesUnjoined(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("user list excludes rejected usernames", new Test() {
            @Override public boolean run() throws Exception { return testUserListExcludesRejected(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        passed += run("client user-list forgery rejected", new Test() {
            @Override public boolean run() throws Exception { return testUserListForgeryRejected(); }
        }) ? 1 : 0;
        failed += lastFailed ? 1 : 0;

        System.out.println("\n=== GroupChatServerIntegrationTest Summary ===");
        System.out.println("PASS: " + passed);
        System.out.println("FAIL: " + failed);
        System.out.println("SKIP: " + skipped);

        if (failed > 0) {
            System.exit(1);
        }
    }

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

    // ------------------------------------------------------------------
    // Room setup
    // ------------------------------------------------------------------

    private static class Room {
        GroupChatServer server;
        RoomListener listener;
        int port;
    }

    private static Room startRoom(String host) throws Exception {
        Room r = new Room();
        r.server = (host == null) ? new GroupChatServer(0) : new GroupChatServer(0, host);
        r.listener = new RoomListener();
        r.server.setListener(r.listener);
        r.server.start();
        if (!r.listener.awaitStarted()) {
            throw new IllegalStateException("Room did not start");
        }
        r.port = r.listener.startedPort;
        if (r.port <= 0) {
            throw new IllegalStateException("Invalid room port");
        }
        return r;
    }

    private static void stopRoom(Room r, TestClient... clients) {
        for (TestClient c : clients) {
            try {
                c.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        if (r != null && r.server != null) {
            try {
                r.server.stop();
            } catch (Exception e) {
                // Ignore
            }
        }
        sleep(300);
    }

    // ------------------------------------------------------------------
    // Test 1: Three-user join
    // ------------------------------------------------------------------

    private static boolean testThreeUserJoin() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;

            bob.connect(room.port);
            if (!bob.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 2; }
            }, TIMEOUT_MS)) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return bob.systemCount() >= 1; }
            }, TIMEOUT_MS)) return false;

            charlie.connect(room.port);
            if (!charlie.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) return false;

            dana.connect(room.port);
            if (!dana.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            // Join SYSTEM broadcasts: Bob sees 3, Charlie 2, Dana 1.
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return bob.systemsOfType(SystemMessage.EventType.USER_JOINED).size() == 3
                        && charlie.systemsOfType(SystemMessage.EventType.USER_JOINED).size() == 2
                        && dana.systemsOfType(SystemMessage.EventType.USER_JOINED).size() == 1;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) join system counts: bob=" + bob.systemCount()
                    + " charlie=" + charlie.systemCount() + " dana=" + dana.systemCount());
                return false;
            }

            Collection<String> names = room.server.getJoinedUsernames();
            Set<String> set = new HashSet<>(names);
            if (!(set.contains("Alice") && set.contains("Bob")
                    && set.contains("Charlie") && set.contains("Dana"))) {
                System.out.println("  (diagnostic) snapshot=" + names);
                return false;
            }
            if (room.server.getJoinedUserCount() != 4) return false;

            // Join callbacks exactly once per client.
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.listener.joinedSnapshot().size() == 3; }
            }, TIMEOUT_MS)) return false;
            sleep(400);
            List<String> joins = room.listener.joinedSnapshot();
            if (joins.size() != 3) {
                System.out.println("  (diagnostic) join callbacks=" + joins);
                return false;
            }
            Set<String> joinSet = new HashSet<>(joins);
            if (joinSet.size() != 3 || !joinSet.contains("Bob")
                    || !joinSet.contains("Charlie") || !joinSet.contains("Dana")) {
                return false;
            }
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 2: Bob broadcasts to everyone
    // ------------------------------------------------------------------

    private static boolean testClientBroadcast() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            int b0 = bob.chatCount();
            int c0 = charlie.chatCount();
            int d0 = dana.chatCount();
            LocalDateTime ts = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            if (!bob.sendChatMessage(new Message("Bob", "Hello everyone", ts))) return false;

            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return bob.chatCount() > b0 && charlie.chatCount() > c0 && dana.chatCount() > d0;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) chat not delivered to all");
                return false;
            }
            sleep(500); // settle: exactly-once check
            if (bob.chatCount() - b0 != 1 || charlie.chatCount() - c0 != 1 || dana.chatCount() - d0 != 1) {
                System.out.println("  (diagnostic) duplicate/missing delivery");
                return false;
            }
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                List<Message> got = c.chatsSnapshot();
                Message last = got.get(got.size() - 1);
                if (!last.getSender().equals("Bob") || !last.getText().equals("Hello everyone")
                        || !last.getTimestamp().equals(ts)) {
                    System.out.println("  (diagnostic) content mismatch: " + last);
                    return false;
                }
            }
            if (bob.decodeErrors.get() != 0 || charlie.decodeErrors.get() != 0
                    || dana.decodeErrors.get() != 0) return false;
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 3: Host broadcasts
    // ------------------------------------------------------------------

    private static boolean testHostBroadcast() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            int chatsBefore = room.listener.chatsSnapshot().size();
            if (!room.server.broadcastHostMessage(
                    new Message("Alice", "Welcome everyone", LocalDateTime.now()))) {
                System.out.println("  (diagnostic) broadcastHostMessage returned false");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return bob.chatCount() >= 1 && charlie.chatCount() >= 1 && dana.chatCount() >= 1;
                }
            }, TIMEOUT_MS)) return false;
            sleep(500);
            if (bob.chatCount() != 1 || charlie.chatCount() != 1 || dana.chatCount() != 1) return false;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                Message m = c.chatsSnapshot().get(0);
                if (!m.getSender().equals("Alice") || !m.getText().equals("Welcome everyone")) {
                    return false;
                }
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.listener.chatsSnapshot().size() > chatsBefore; }
            }, TIMEOUT_MS)) return false;
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 4: Client leaves without closing room
    // ------------------------------------------------------------------

    private static boolean testClientLeaves() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            int cSys0 = charlie.systemCount();
            int dSys0 = dana.systemCount();
            if (!bob.sendDisconnect()) return false;

            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) count did not drop to 3");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return charlie.systemsOfType(SystemMessage.EventType.USER_LEFT).size() >= 1
                        && dana.systemsOfType(SystemMessage.EventType.USER_LEFT).size() >= 1;
                }
            }, TIMEOUT_MS)) return false;

            boolean charlieSawBob = false;
            for (SystemMessage s : charlie.systemsSnapshot()) {
                if (s.getEventType() == SystemMessage.EventType.USER_LEFT
                        && s.getText().equals("Bob left the chat.")) {
                    charlieSawBob = true;
                }
            }
            boolean danaSawBob = false;
            for (SystemMessage s : dana.systemsSnapshot()) {
                if (s.getEventType() == SystemMessage.EventType.USER_LEFT
                        && s.getText().equals("Bob left the chat.")) {
                    danaSawBob = true;
                }
            }
            if (!charlieSawBob || !danaSawBob) return false;
            if (charlie.systemCount() <= cSys0 || dana.systemCount() <= dSys0) return false;

            if (!room.server.isRunning()) return false;
            Set<String> set = new HashSet<>(room.server.getJoinedUsernames());
            if (room.server.getJoinedUserCount() != 3 || !set.contains("Alice")
                    || !set.contains("Charlie") || !set.contains("Dana") || set.contains("Bob")) {
                return false;
            }

            // Bob removed exactly once (DISCONNECT then EOF must not duplicate).
            // The count drop is visible before the async listener callback
            // lands, so wait for the callback before asserting exactness.
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    for (String u : room.listener.leftSnapshot()) {
                        if (u.equals("Bob")) return true;
                    }
                    return false;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Bob leave callback missing");
                return false;
            }
            sleep(600);
            int bobLeaves = 0;
            for (String u : room.listener.leftSnapshot()) {
                if (u.equals("Bob")) bobLeaves++;
            }
            if (bobLeaves != 1) {
                System.out.println("  (diagnostic) Bob leave callbacks=" + bobLeaves);
                return false;
            }

            // Survivors still active: Dana chats, Charlie receives.
            int ch0 = charlie.chatCount();
            if (!dana.sendChat("still here")) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return charlie.chatCount() > ch0; }
            }, TIMEOUT_MS)) return false;
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 5: Fourth client joins later
    // ------------------------------------------------------------------

    private static boolean testFourthJoinsLater() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        TestClient eve = new TestClient("Eve");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            if (!bob.sendDisconnect()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) return false;

            eve.connect(room.port);
            if (!eve.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            Set<String> set = new HashSet<>(room.server.getJoinedUsernames());
            if (!(set.contains("Alice") && set.contains("Charlie")
                    && set.contains("Dana") && set.contains("Eve")) || set.contains("Bob")) {
                System.out.println("  (diagnostic) snapshot=" + set);
                return false;
            }
            // Existing users remain connected: Charlie chats, Dana and Eve receive.
            int d0 = dana.chatCount();
            int e0 = eve.chatCount();
            if (!charlie.sendChat("welcome Eve")) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return dana.chatCount() > d0 && eve.chatCount() > e0; }
            }, TIMEOUT_MS)) return false;
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana, eve);
        }
    }

    // ------------------------------------------------------------------
    // Test 6: Duplicate username rejection
    // ------------------------------------------------------------------

    private static boolean testDuplicateRejected() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient dupHost = new TestClient("alice");
        TestClient dupClient = new TestClient("BOB");
        try {
            r = startRoom("Alice");
            final Room room = r;
            bob.connect(room.port);
            if (!bob.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 2; }
            }, TIMEOUT_MS)) return false;
            // Registration is visible before the async listener callback lands:
            // wait for Bob's join callback before baselining.
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.listener.joinedSnapshot().size() == 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Bob join callback missing");
                return false;
            }
            final int joinsBefore = room.listener.joinedSnapshot().size();

            // Duplicate of host with different casing.
            dupHost.connect(room.port);
            if (!dupHost.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return dupHost.systemsOfType(SystemMessage.EventType.USERNAME_REJECTED).size() >= 1;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) no USERNAME_REJECTED for host duplicate");
                return false;
            }
            SystemMessage rej = dupHost.systemsOfType(SystemMessage.EventType.USERNAME_REJECTED).get(0);
            if (!rej.getText().equals("Username is already in use.")) return false;
            if (!dupHost.awaitDisconnected(TIMEOUT_MS)) {
                System.out.println("  (diagnostic) rejected host-duplicate not closed");
                return false;
            }

            // Duplicate of another client with different casing.
            dupClient.connect(room.port);
            if (!dupClient.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return dupClient.systemsOfType(SystemMessage.EventType.USERNAME_REJECTED).size() >= 1;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) no USERNAME_REJECTED for client duplicate");
                return false;
            }
            if (!dupClient.awaitDisconnected(TIMEOUT_MS)) return false;

            sleep(500);
            if (!room.server.isRunning()) return false;
            Set<String> set = new HashSet<>(room.server.getJoinedUsernames());
            if (room.server.getJoinedUserCount() != 2 || !set.contains("Alice") || !set.contains("Bob")) {
                System.out.println("  (diagnostic) snapshot changed: " + set);
                return false;
            }
            if (room.listener.joinedSnapshot().size() != joinsBefore) {
                System.out.println("  (diagnostic) join event broadcast for duplicate");
                return false;
            }
            // Original Bob unaffected.
            if (!bob.sendChat("im still here")) return false;
            return true;
        } finally {
            stopRoom(r, bob, dupHost, dupClient);
        }
    }

    // ------------------------------------------------------------------
    // Test 7: Sender spoofing
    // ------------------------------------------------------------------

    private static boolean testSpoofingRejected() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            int b0 = bob.chatCount();
            int c0 = charlie.chatCount();
            int d0 = dana.chatCount();
            int listenerChats0 = room.listener.chatsSnapshot().size();

            // Bob attempts to speak as Alice.
            Message forged = new Message("Alice", "fake message", LocalDateTime.now());
            if (!bob.sendChatMessage(forged)) return false;
            sleep(800);

            if (bob.chatCount() != b0 || charlie.chatCount() != c0 || dana.chatCount() != d0) {
                System.out.println("  (diagnostic) spoofed message was broadcast");
                return false;
            }
            if (room.listener.chatsSnapshot().size() != listenerChats0) {
                System.out.println("  (diagnostic) spoofed message reached room listener");
                return false;
            }
            if (!room.server.isRunning()) return false;
            if (room.server.getJoinedUserCount() != 4) return false;

            // Single violation must not eject Bob: a valid message still works.
            if (!bob.sendChat("real message")) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return charlie.chatCount() > c0 && dana.chatCount() > d0; }
            }, TIMEOUT_MS)) return false;
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 8: Client SYSTEM forgery
    // ------------------------------------------------------------------

    private static boolean testSystemForgeryRejected() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                c.connect(room.port);
                if (!c.sendJoin()) {
                    System.out.println("  (diagnostic) join send failed for " + c.name);
                    return false;
                }
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) joins never completed, count="
                    + room.server.getJoinedUserCount());
                return false;
            }

            // Join SYSTEM delivery lags registration: wait until every join
            // broadcast has landed (3+2+1 = 6) before taking baselines.
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return bob.systemCount() + charlie.systemCount() + dana.systemCount() == 6;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) join systems incomplete: bob=" + bob.systemCount()
                    + " charlie=" + charlie.systemCount() + " dana=" + dana.systemCount()
                    + " decodeErrors=" + bob.decodeErrors.get() + "/" + charlie.decodeErrors.get()
                    + "/" + dana.decodeErrors.get());
                return false;
            }

            int cSys0 = charlie.systemCount();
            int dSys0 = dana.systemCount();
            int bSys0 = bob.systemCount();

            SystemMessage forged = new SystemMessage(
                    SystemMessage.EventType.USER_LEFT, "Alice left the chat.", LocalDateTime.now());
            if (!bob.sendRaw(MessageCodec.encodeSystem(forged))) {
                System.out.println("  (diagnostic) forgery send failed");
                return false;
            }
            sleep(800);

            if (charlie.systemCount() != cSys0 || dana.systemCount() != dSys0
                    || bob.systemCount() != bSys0) {
                System.out.println("  (diagnostic) forged SYSTEM was broadcast");
                return false;
            }
            if (!room.server.isRunning()) {
                System.out.println("  (diagnostic) room stopped after forgery");
                return false;
            }
            if (room.server.getJoinedUserCount() != 4) {
                System.out.println("  (diagnostic) count changed to "
                    + room.server.getJoinedUserCount());
                return false;
            }
            if (!room.listener.leftSnapshot().isEmpty()) {
                System.out.println("  (diagnostic) unexpected leaves: " + room.listener.leftSnapshot());
                return false;
            }
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 9: Unexpected EOF
    // ------------------------------------------------------------------

    private static boolean testUnexpectedEof() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            int dSys0 = dana.systemCount();
            charlie.abruptClose();

            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Charlie not removed after EOF");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return dana.systemsOfType(SystemMessage.EventType.USER_LEFT).size() >= 1;
                }
            }, TIMEOUT_MS)) return false;
            boolean danaSawCharlie = false;
            for (SystemMessage s : dana.systemsSnapshot()) {
                if (s.getEventType() == SystemMessage.EventType.USER_LEFT
                        && s.getText().equals("Charlie left the chat.")) {
                    danaSawCharlie = true;
                }
            }
            if (!danaSawCharlie || dana.systemCount() <= dSys0) return false;

            // Exactly once: wait for the async leave callback to land,
            // then settle and assert no duplicate.
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    for (String u : room.listener.leftSnapshot()) {
                        if (u.equals("Charlie")) return true;
                    }
                    return false;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Charlie leave callback missing");
                return false;
            }
            sleep(600);
            int charlieLeaves = 0;
            for (String u : room.listener.leftSnapshot()) {
                if (u.equals("Charlie")) charlieLeaves++;
            }
            if (charlieLeaves != 1) {
                System.out.println("  (diagnostic) Charlie leave callbacks=" + charlieLeaves);
                return false;
            }
            if (!room.server.isRunning()) return false;

            // Dana still connected: Dana chats, Bob receives.
            int b0 = bob.chatCount();
            if (!dana.sendChat("d still here")) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return bob.chatCount() > b0; }
            }, TIMEOUT_MS)) return false;
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 10: Host closes room
    // ------------------------------------------------------------------

    private static boolean testHostClosesRoom() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        int port = -1;
        try {
            r = startRoom("Alice");
            final Room room = r;
            port = room.port;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            room.server.stop();

            if (!room.listener.awaitStopped()) {
                System.out.println("  (diagnostic) onRoomStopped not fired");
                return false;
            }
            // ROOM_CLOSED delivered before EOF where feasible.
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                final TestClient tc = c;
                if (!waitFor(new Condition() {
                    @Override public boolean check() {
                        return tc.systemsOfType(SystemMessage.EventType.ROOM_CLOSED).size() >= 1;
                    }
                }, TIMEOUT_MS)) {
                    System.out.println("  (diagnostic) " + tc.name + " missed ROOM_CLOSED");
                    return false;
                }
            }
            // Every client connection closes.
            if (!bob.awaitDisconnected(TIMEOUT_MS) || !charlie.awaitDisconnected(TIMEOUT_MS)
                    || !dana.awaitDisconnected(TIMEOUT_MS)) {
                System.out.println("  (diagnostic) client connections not closed");
                return false;
            }
            if (room.server.getJoinedUserCount() != 0) {
                System.out.println("  (diagnostic) count after stop="
                    + room.server.getJoinedUserCount());
                return false;
            }
            if (!room.server.getJoinedUsernames().isEmpty()) {
                System.out.println("  (diagnostic) snapshot not cleared: "
                    + room.server.getJoinedUsernames());
                return false;
            }
            if (room.server.isRunning()) {
                System.out.println("  (diagnostic) room still running after stop");
                return false;
            }

            // Stopped exactly once even with repeated stop().
            room.server.stop();
            sleep(300);
            if (room.listener.stoppedCount.get() != 1) {
                System.out.println("  (diagnostic) stopped count=" + room.listener.stoppedCount.get());
                return false;
            }

            // No new connection can join.
            boolean refused = false;
            Socket s = null;
            try {
                s = new Socket(LOCALHOST, port);
            } catch (java.io.IOException expected) {
                refused = true;
            } finally {
                closeQuietly(s);
            }
            if (!refused) {
                System.out.println("  (diagnostic) new connection accepted after stop");
                return false;
            }
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 11: Concurrent broadcasts
    // ------------------------------------------------------------------

    private static boolean testConcurrentBroadcasts() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 4; }
            }, TIMEOUT_MS)) return false;

            final int perSender = 5;
            final Set<String> expected = new HashSet<>();
            for (String sender : new String[]{"Bob", "Charlie", "Dana"}) {
                for (int i = 0; i < perSender; i++) {
                    expected.add(sender + "|" + "msg-" + i);
                }
            }

            Thread tb = senderThread(bob, perSender);
            Thread tc = senderThread(charlie, perSender);
            Thread td = senderThread(dana, perSender);
            tb.start();
            tc.start();
            td.start();
            tb.join(TIMEOUT_MS);
            tc.join(TIMEOUT_MS);
            td.join(TIMEOUT_MS);
            if (tb.isAlive() || tc.isAlive() || td.isAlive()) {
                System.out.println("  (diagnostic) sender thread deadlock");
                return false;
            }

            final int total = perSender * 3;
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return bob.chatCount() >= total && charlie.chatCount() >= total
                        && dana.chatCount() >= total;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) counts: bob=" + bob.chatCount()
                    + " charlie=" + charlie.chatCount() + " dana=" + dana.chatCount());
                return false;
            }
            sleep(800); // settle: exactly-once, no duplicates
            for (TestClient c : new TestClient[]{bob, charlie, dana}) {
                if (c.chatCount() != total) {
                    System.out.println("  (diagnostic) " + c.name + " count=" + c.chatCount()
                        + " expected=" + total);
                    return false;
                }
                Set<String> got = new HashSet<>();
                for (Message m : c.chatsSnapshot()) {
                    got.add(m.getSender() + "|" + m.getText());
                }
                if (!got.equals(expected)) {
                    System.out.println("  (diagnostic) " + c.name + " content mismatch/corruption");
                    return false;
                }
                if (c.decodeErrors.get() != 0) return false;
            }
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    private static Thread senderThread(final TestClient client, final int count) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < count; i++) {
                        client.sendChat("msg-" + i);
                        Thread.sleep(20);
                    }
                } catch (Exception e) {
                    // Counts verified by caller
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Test 12: Malformed pre-join client
    // ------------------------------------------------------------------

    private static boolean testMalformedHandshake() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient raw = new TestClient("raw");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie}) {
                c.connect(room.port);
                if (!c.sendJoin()) {
                    System.out.println("  (diagnostic) join send failed for " + c.name);
                    return false;
                }
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) initial joins never completed, count="
                    + room.server.getJoinedUserCount());
                return false;
            }
            // Listener callbacks lag registration: wait for both join callbacks
            // before baselining, otherwise in-flight callbacks look like
            // spurious join events for the malformed client.
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.listener.joinedSnapshot().size() == 2; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) join callbacks never completed");
                return false;
            }
            final int joinsBefore = room.listener.joinedSnapshot().size();
            final int leavesBefore = room.listener.leftSnapshot().size();

            raw.connect(room.port);
            raw.sendRaw("GARBAGE LINE ONE");
            sleep(200);
            raw.sendRaw("CHAT|bad");
            sleep(200);
            raw.sendRaw("HELLO WORLD");
            if (!raw.awaitDisconnected(TIMEOUT_MS)) {
                System.out.println("  (diagnostic) malformed client not closed");
                return false;
            }

            sleep(500);
            if (!room.server.isRunning()) {
                System.out.println("  (diagnostic) room stopped by malformed client");
                return false;
            }
            if (room.server.getJoinedUserCount() != 3) {
                System.out.println("  (diagnostic) joined count changed to "
                    + room.server.getJoinedUserCount());
                return false;
            }
            if (room.listener.joinedSnapshot().size() != joinsBefore) {
                System.out.println("  (diagnostic) unexpected join events: "
                    + room.listener.joinedSnapshot());
                return false;
            }
            if (room.listener.leftSnapshot().size() != leavesBefore) {
                System.out.println("  (diagnostic) leave broadcast for unjoined client");
                return false;
            }

            // Group still functional.
            int c0 = charlie.chatCount();
            if (!bob.sendChat("group still works")) {
                System.out.println("  (diagnostic) survivor send failed");
                return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return charlie.chatCount() > c0; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) survivor broadcast not delivered");
                return false;
            }
            return true;
        } finally {
            stopRoom(r, bob, charlie, raw);
        }
    }

    // ------------------------------------------------------------------
    // Test 13: Late client receives authoritative user list
    // ------------------------------------------------------------------

    private static boolean testLateClientUserList() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) return false;

            dana.connect(room.port);
            if (!dana.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return dana.userListCount() >= 1; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Dana never received USER_LIST");
                return false;
            }
            sleep(400);
            if (dana.userListCount() != 1) {
                System.out.println("  (diagnostic) Dana lists=" + dana.userListCount());
                return false;
            }
            List<String> names = dana.userListsSnapshot().get(0).getUsernames();
            if (!names.equals(java.util.Arrays.asList("Alice", "Bob", "Charlie", "Dana"))) {
                System.out.println("  (diagnostic) Dana list=" + names);
                return false;
            }
            if (!names.get(0).equals("Alice")) {
                System.out.println("  (diagnostic) host not first");
                return false;
            }
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 14: Snapshot is unicast to the new client only
    // ------------------------------------------------------------------

    private static boolean testUserListUnicast() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        TestClient dana = new TestClient("Dana");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) return false;
            // Existing clients each got exactly one list at their own join.
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return bob.userListCount() == 1 && charlie.userListCount() == 1;
                }
            }, TIMEOUT_MS)) return false;
            int bobBefore = bob.userListCount();
            int charlieBefore = charlie.userListCount();

            dana.connect(room.port);
            if (!dana.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return dana.userListCount() >= 1; }
            }, TIMEOUT_MS)) return false;
            sleep(600);
            if (bob.userListCount() != bobBefore || charlie.userListCount() != charlieBefore) {
                System.out.println("  (diagnostic) redundant snapshot: bob="
                    + bob.userListCount() + " charlie=" + charlie.userListCount());
                return false;
            }
            return true;
        } finally {
            stopRoom(r, bob, charlie, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 15: Snapshot excludes unjoined transport connections
    // ------------------------------------------------------------------

    private static boolean testUserListExcludesUnjoined() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient dana = new TestClient("Dana");
        Socket raw = null;
        try {
            r = startRoom("Alice");
            final Room room = r;
            bob.connect(room.port);
            if (!bob.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 2; }
            }, TIMEOUT_MS)) return false;

            // Raw transport that never sends JOIN.
            raw = new Socket(LOCALHOST, room.port);
            sleep(300);

            dana.connect(room.port);
            if (!dana.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return dana.userListCount() >= 1; }
            }, TIMEOUT_MS)) return false;
            List<String> names = dana.userListsSnapshot().get(0).getUsernames();
            if (!names.equals(java.util.Arrays.asList("Alice", "Bob", "Dana"))) {
                System.out.println("  (diagnostic) Dana list=" + names);
                return false;
            }
            return true;
        } finally {
            closeQuietly(raw);
            stopRoom(r, bob, dana);
        }
    }

    // ------------------------------------------------------------------
    // Test 16: Snapshot excludes rejected duplicates
    // ------------------------------------------------------------------

    private static boolean testUserListExcludesRejected() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient dup = new TestClient("alice");
        TestClient eve = new TestClient("Eve");
        try {
            r = startRoom("Alice");
            final Room room = r;
            bob.connect(room.port);
            if (!bob.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 2; }
            }, TIMEOUT_MS)) return false;

            dup.connect(room.port);
            if (!dup.sendJoin()) return false;
            if (!dup.awaitDisconnected(TIMEOUT_MS)) {
                System.out.println("  (diagnostic) duplicate not rejected");
                return false;
            }

            eve.connect(room.port);
            if (!eve.sendJoin()) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return eve.userListCount() >= 1; }
            }, TIMEOUT_MS)) return false;
            List<String> names = eve.userListsSnapshot().get(0).getUsernames();
            if (!names.equals(java.util.Arrays.asList("Alice", "Bob", "Eve"))) {
                System.out.println("  (diagnostic) Eve list=" + names);
                return false;
            }
            Set<String> lowered = new HashSet<>();
            for (String name : names) {
                if (!lowered.add(name.toLowerCase(java.util.Locale.ROOT))) {
                    System.out.println("  (diagnostic) duplicate in snapshot");
                    return false;
                }
            }
            return true;
        } finally {
            stopRoom(r, bob, dup, eve);
        }
    }

    // ------------------------------------------------------------------
    // Test 17: Client USER_LIST forgery rejected as violation
    // ------------------------------------------------------------------

    private static boolean testUserListForgeryRejected() throws Exception {
        Room r = null;
        TestClient bob = new TestClient("Bob");
        TestClient charlie = new TestClient("Charlie");
        try {
            r = startRoom("Alice");
            final Room room = r;
            for (TestClient c : new TestClient[]{bob, charlie}) {
                c.connect(room.port);
                if (!c.sendJoin()) return false;
            }
            if (!waitFor(new Condition() {
                @Override public boolean check() { return room.server.getJoinedUserCount() == 3; }
            }, TIMEOUT_MS)) return false;
            // Both clients must have received their own snapshots before
            // baselining; otherwise a late legitimate delivery looks like a
            // broadcast of the forged line.
            if (!waitFor(new Condition() {
                @Override public boolean check() {
                    return bob.userListCount() >= 1 && charlie.userListCount() >= 1;
                }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) own snapshots never arrived");
                return false;
            }
            int charlieListsBefore = charlie.userListCount();
            int charlieChatsBefore = charlie.chatCount();

            model.UserListMessage forged = new model.UserListMessage(
                java.util.Arrays.asList("Alice", "Bob", "Mallory"),
                LocalDateTime.now());
            if (!bob.sendRaw(MessageCodec.encodeUserList(forged))) return false;
            sleep(800);

            // Never broadcast: no new list or chat anywhere.
            if (charlie.userListCount() != charlieListsBefore
                    || charlie.chatCount() != charlieChatsBefore) {
                System.out.println("  (diagnostic) forged USER_LIST was broadcast");
                return false;
            }
            // One violation must not eject Bob or disturb authoritative state.
            if (!room.server.isRunning() || room.server.getJoinedUserCount() != 3) {
                System.out.println("  (diagnostic) room disturbed by single forgery");
                return false;
            }
            if (!bob.sendChat("still here")) return false;
            if (!waitFor(new Condition() {
                @Override public boolean check() { return charlie.chatCount() > charlieChatsBefore; }
            }, TIMEOUT_MS)) {
                System.out.println("  (diagnostic) Bob ejected after one violation");
                return false;
            }
            return true;
        } finally {
            stopRoom(r, bob, charlie);
        }
    }
}
