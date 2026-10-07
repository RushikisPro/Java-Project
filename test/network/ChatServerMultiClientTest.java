package network;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Step 9A: Multi-client server lifecycle tests.
 *
 * Verifies:
 * - ChatServer keeps ServerSocket open and accepts multiple clients.
 * - Each accepted Socket gets a unique connection ID.
 * - Connections are stored in a thread-safe collection.
 * - Removing one client keeps the server running.
 * - Server stops only on explicit stop().
 */
public class ChatServerMultiClientTest {

    private static final long TEST_TIMEOUT_MS = 10000;
    private static final String LOCALHOST = "127.0.0.1";

    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;

        try {
            if (testThreeClientsConnect()) {
                passed++;
                System.out.println("PASS: three clients connect");
            } else {
                failed++;
                System.out.println("FAIL: three clients connect");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: three clients connect - " + e.getMessage());
            e.printStackTrace(System.out);
        }

        try {
            if (testRemovingOneClientKeepsServerRunning()) {
                passed++;
                System.out.println("PASS: removing one client keeps server running");
            } else {
                failed++;
                System.out.println("FAIL: removing one client keeps server running");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: removing one client keeps server running - " + e.getMessage());
            e.printStackTrace(System.out);
        }

        try {
            if (testNewClientJoinsAfterAnotherLeaves()) {
                passed++;
                System.out.println("PASS: new client joins after another leaves");
            } else {
                failed++;
                System.out.println("FAIL: new client joins after another leaves");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: new client joins after another leaves - " + e.getMessage());
            e.printStackTrace(System.out);
        }

        try {
            if (testRepeatedRemoval()) {
                passed++;
                System.out.println("PASS: repeated client removal");
            } else {
                failed++;
                System.out.println("FAIL: repeated client removal");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: repeated client removal - " + e.getMessage());
            e.printStackTrace(System.out);
        }

        try {
            if (testServerStopClosesAllClients()) {
                passed++;
                System.out.println("PASS: server stop closes all clients");
            } else {
                failed++;
                System.out.println("FAIL: server stop closes all clients");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: server stop closes all clients - " + e.getMessage());
            e.printStackTrace(System.out);
        }

        try {
            if (testConcurrentClientLifecycle()) {
                passed++;
                System.out.println("PASS: concurrent client lifecycle");
            } else {
                failed++;
                System.out.println("FAIL: concurrent client lifecycle");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: concurrent client lifecycle - " + e.getMessage());
            e.printStackTrace(System.out);
        }

        System.out.println("\n=== ChatServerMultiClientTest Summary ===");
        System.out.println("PASS: " + passed);
        System.out.println("FAIL: " + failed);
        System.out.println("SKIP: " + skipped);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static class TestServerListener implements ChatServer.ServerListener {
        private final CountDownLatch startedLatch = new CountDownLatch(1);
        private final CountDownLatch stoppedLatch = new CountDownLatch(1);
        private final AtomicInteger connectedCount = new AtomicInteger(0);
        private final AtomicInteger disconnectedCount = new AtomicInteger(0);
        private final AtomicInteger stoppedCount = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private volatile int reportedPort = -1;
        private final List<ClientConnection> connectedConnections = new ArrayList<>();
        private final List<ClientConnection> disconnectedConnections = new ArrayList<>();
        private final Object listLock = new Object();

        @Override
        public void onServerStarted(String ipAddress, int port) {
            reportedPort = port;
            startedLatch.countDown();
        }

        @Override
        public void onClientConnected(ClientConnection connection) {
            connectedCount.incrementAndGet();
            synchronized (listLock) {
                connectedConnections.add(connection);
            }
        }

        @Override
        public void onClientDisconnected(ClientConnection connection) {
            disconnectedCount.incrementAndGet();
            synchronized (listLock) {
                disconnectedConnections.add(connection);
            }
        }

        @Override
        public void onServerError(Exception exception) {
            errorCount.incrementAndGet();
        }

        @Override
        public void onServerStopped() {
            stoppedCount.incrementAndGet();
            stoppedLatch.countDown();
        }

        boolean awaitStarted(long timeoutMillis) throws InterruptedException {
            return startedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        boolean awaitStopped(long timeoutMillis) throws InterruptedException {
            return stoppedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        int getConnectedCount() {
            return connectedCount.get();
        }

        int getDisconnectedCount() {
            return disconnectedCount.get();
        }

        int getStoppedCount() {
            return stoppedCount.get();
        }

        int getErrorCount() {
            return errorCount.get();
        }

        int getReportedPort() {
            return reportedPort;
        }

        List<ClientConnection> getConnectedConnections() {
            synchronized (listLock) {
                return new ArrayList<>(connectedConnections);
            }
        }

        List<ClientConnection> getDisconnectedConnections() {
            synchronized (listLock) {
                return new ArrayList<>(disconnectedConnections);
            }
        }
    }

    private static boolean waitForConnectedCallbacks(TestServerListener listener, int expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (listener.getConnectedCount() >= expected) {
                return true;
            }
            Thread.sleep(50);
        }
        return listener.getConnectedCount() >= expected;
    }

    private static boolean waitForServerCount(ChatServer server, int expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (server.getConnectedClientCount() == expected) {
                return true;
            }
            Thread.sleep(50);
        }
        return server.getConnectedClientCount() == expected;
    }

    private static boolean waitForDisconnectedCallbacks(TestServerListener listener, int expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (listener.getDisconnectedCount() >= expected) {
                return true;
            }
            Thread.sleep(50);
        }
        return listener.getDisconnectedCount() >= expected;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    // Test 1: Three clients connect
    private static boolean testThreeClientsConnect() throws Exception {
        ChatServer server = null;
        List<Socket> clients = new ArrayList<>();

        try {
            server = new ChatServer(0);
            TestServerListener listener = new TestServerListener();
            server.setServerListener(listener);

            server.start();

            if (!listener.awaitStarted(TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) server did not report started");
                return false;
            }

            int port = listener.getReportedPort();
            if (port <= 0) {
                System.out.println("  (diagnostic) invalid reported port: " + port);
                return false;
            }
            // Cross-check getPort() returns the actual bound port.
            if (server.getPort() != port) {
                System.out.println("  (diagnostic) getPort()=" + server.getPort()
                        + " != reported port=" + port);
                return false;
            }

            // Connect three clients.
            for (int i = 0; i < 3; i++) {
                Socket client = new Socket(LOCALHOST, port);
                clients.add(client);
            }

            if (!waitForConnectedCallbacks(listener, 3, TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) onClientConnected count="
                        + listener.getConnectedCount() + ", expected 3");
                return false;
            }
            if (!waitForServerCount(server, 3, TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) server count="
                        + server.getConnectedClientCount() + ", expected 3");
                return false;
            }

            if (server.getConnectedClientCount() != 3) {
                return false;
            }

            // All three connections must have unique IDs.
            List<ClientConnection> connections = listener.getConnectedConnections();
            if (connections.size() != 3) {
                System.out.println("  (diagnostic) listener connections=" + connections.size());
                return false;
            }
            Set<Long> ids = new HashSet<>();
            for (ClientConnection c : connections) {
                if (c == null || c.getSocket() == null) {
                    return false;
                }
                if (!ids.add(c.getConnectionId())) {
                    System.out.println("  (diagnostic) duplicate connection ID");
                    return false;
                }
            }
            if (ids.size() != 3) {
                return false;
            }

            return true;
        } finally {
            for (Socket client : clients) {
                closeQuietly(client);
            }
            if (server != null) {
                server.stop();
            }
            Thread.sleep(300);
        }
    }

    // Test 2: First client removal keeps server running
    private static boolean testRemovingOneClientKeepsServerRunning() throws Exception {
        ChatServer server = null;
        List<Socket> clients = new ArrayList<>();

        try {
            server = new ChatServer(0);
            TestServerListener listener = new TestServerListener();
            server.setServerListener(listener);

            server.start();

            if (!listener.awaitStarted(TEST_TIMEOUT_MS)) {
                return false;
            }
            int port = listener.getReportedPort();

            for (int i = 0; i < 3; i++) {
                clients.add(new Socket(LOCALHOST, port));
            }
            if (!waitForConnectedCallbacks(listener, 3, TEST_TIMEOUT_MS)) {
                return false;
            }
            if (!waitForServerCount(server, 3, TEST_TIMEOUT_MS)) {
                return false;
            }

            List<ClientConnection> before = listener.getConnectedConnections();
            long firstId = before.get(0).getConnectionId();

            server.removeClient(firstId);

            if (!waitForDisconnectedCallbacks(listener, 1, TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) onClientDisconnected not fired");
                return false;
            }
            if (!waitForServerCount(server, 2, TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) count after removal="
                        + server.getConnectedClientCount());
                return false;
            }

            // Server must remain running with ServerSocket still accepting.
            if (!server.isRunning()) {
                System.out.println("  (diagnostic) server not running after removal");
                return false;
            }

            // The other two client sockets must remain usable (open + connected).
            Socket second = clients.get(1);
            Socket third = clients.get(2);
            if (second.isClosed() || third.isClosed()) {
                System.out.println("  (diagnostic) remaining client socket closed");
                return false;
            }
            if (!second.isConnected() || !third.isConnected()) {
                return false;
            }
            // Server-side connections for the survivors must still be connected.
            for (ClientConnection c : server.getClientConnections()) {
                if (!c.isConnected() || c.getSocket().isClosed()) {
                    System.out.println("  (diagnostic) survivor server-side connection closed");
                    return false;
                }
            }

            // ServerSocket continues accepting: a fresh connection must succeed.
            Socket probe = new Socket(LOCALHOST, port);
            clients.add(probe);
            if (!waitForConnectedCallbacks(listener, 4, TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) server did not accept after removal");
                return false;
            }

            return true;
        } finally {
            for (Socket client : clients) {
                closeQuietly(client);
            }
            if (server != null) {
                server.stop();
            }
            Thread.sleep(300);
        }
    }

    // Test 3: Fourth client joins after removal
    private static boolean testNewClientJoinsAfterAnotherLeaves() throws Exception {
        ChatServer server = null;
        List<Socket> clients = new ArrayList<>();

        try {
            server = new ChatServer(0);
            TestServerListener listener = new TestServerListener();
            server.setServerListener(listener);

            server.start();

            if (!listener.awaitStarted(TEST_TIMEOUT_MS)) {
                return false;
            }
            int port = listener.getReportedPort();

            for (int i = 0; i < 3; i++) {
                clients.add(new Socket(LOCALHOST, port));
            }
            if (!waitForConnectedCallbacks(listener, 3, TEST_TIMEOUT_MS)) {
                return false;
            }
            if (!waitForServerCount(server, 3, TEST_TIMEOUT_MS)) {
                return false;
            }

            Set<Long> initialIds = new HashSet<>();
            for (ClientConnection c : listener.getConnectedConnections()) {
                initialIds.add(c.getConnectionId());
            }

            // Remove one of the initial clients.
            long removedId = listener.getConnectedConnections().get(0).getConnectionId();
            server.removeClient(removedId);
            if (!waitForServerCount(server, 2, TEST_TIMEOUT_MS)) {
                return false;
            }

            // Connect another client.
            Socket fourth = new Socket(LOCALHOST, port);
            clients.add(fourth);

            if (!waitForConnectedCallbacks(listener, 4, TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) fourth client not accepted");
                return false;
            }
            if (!waitForServerCount(server, 3, TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) count=" + server.getConnectedClientCount()
                        + ", expected 3");
                return false;
            }

            // New connection ID must be unique (never reused).
            List<ClientConnection> all = listener.getConnectedConnections();
            if (all.size() != 4) {
                return false;
            }
            ClientConnection newest = all.get(3);
            if (initialIds.contains(newest.getConnectionId())) {
                System.out.println("  (diagnostic) new connection ID reused");
                return false;
            }
            Set<Long> unique = new HashSet<>();
            for (ClientConnection c : all) {
                if (!unique.add(c.getConnectionId())) {
                    return false;
                }
            }

            return true;
        } finally {
            for (Socket client : clients) {
                closeQuietly(client);
            }
            if (server != null) {
                server.stop();
            }
            Thread.sleep(300);
        }
    }

    // Test 4: Repeated removal of the same ID
    private static boolean testRepeatedRemoval() throws Exception {
        ChatServer server = null;
        List<Socket> clients = new ArrayList<>();

        try {
            server = new ChatServer(0);
            TestServerListener listener = new TestServerListener();
            server.setServerListener(listener);

            server.start();

            if (!listener.awaitStarted(TEST_TIMEOUT_MS)) {
                return false;
            }
            int port = listener.getReportedPort();

            Socket client = new Socket(LOCALHOST, port);
            clients.add(client);

            if (!waitForConnectedCallbacks(listener, 1, TEST_TIMEOUT_MS)) {
                return false;
            }
            if (!waitForServerCount(server, 1, TEST_TIMEOUT_MS)) {
                return false;
            }

            long connectionId = listener.getConnectedConnections().get(0).getConnectionId();

            // Remove the same ID multiple times: must not throw, must notify once.
            server.removeClient(connectionId);
            server.removeClient(connectionId);
            server.removeClient(connectionId);

            Thread.sleep(300);

            if (listener.getDisconnectedCount() != 1) {
                System.out.println("  (diagnostic) onClientDisconnected count="
                        + listener.getDisconnectedCount() + ", expected 1");
                return false;
            }
            if (server.getConnectedClientCount() != 0) {
                return false;
            }
            // Unknown IDs must also be safe.
            server.removeClient(-999999L);
            if (listener.getDisconnectedCount() != 1) {
                return false;
            }

            return true;
        } finally {
            for (Socket client : clients) {
                closeQuietly(client);
            }
            if (server != null) {
                server.stop();
            }
            Thread.sleep(300);
        }
    }

    // Test 5: Server stop closes everything
    private static boolean testServerStopClosesAllClients() throws Exception {
        ChatServer server = null;
        List<Socket> clients = new ArrayList<>();
        List<ClientConnection> serverSide = new ArrayList<>();

        try {
            server = new ChatServer(0);
            TestServerListener listener = new TestServerListener();
            server.setServerListener(listener);

            server.start();

            if (!listener.awaitStarted(TEST_TIMEOUT_MS)) {
                return false;
            }
            int port = listener.getReportedPort();

            for (int i = 0; i < 3; i++) {
                clients.add(new Socket(LOCALHOST, port));
            }
            if (!waitForConnectedCallbacks(listener, 3, TEST_TIMEOUT_MS)) {
                return false;
            }
            if (!waitForServerCount(server, 3, TEST_TIMEOUT_MS)) {
                return false;
            }

            serverSide.addAll(server.getClientConnections());

            server.stop();

            if (!listener.awaitStopped(TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) onServerStopped not fired");
                return false;
            }
            Thread.sleep(300);

            if (server.isRunning()) {
                System.out.println("  (diagnostic) server still running after stop");
                return false;
            }
            if (server.getConnectedClientCount() != 0) {
                return false;
            }
            Collection<ClientConnection> remaining = server.getClientConnections();
            if (!remaining.isEmpty()) {
                return false;
            }
            // All prior server-side connections must be closed.
            for (ClientConnection c : serverSide) {
                if (c.isConnected()) {
                    System.out.println("  (diagnostic) server-side connection still marked connected");
                    return false;
                }
                if (!c.getSocket().isClosed()) {
                    System.out.println("  (diagnostic) server-side socket not closed");
                    return false;
                }
            }
            // onServerStopped must occur exactly once even with repeated stop().
            server.stop();
            Thread.sleep(200);
            if (listener.getStoppedCount() != 1) {
                System.out.println("  (diagnostic) onServerStopped count="
                        + listener.getStoppedCount() + ", expected 1");
                return false;
            }
            if (listener.getErrorCount() != 0) {
                System.out.println("  (diagnostic) unexpected onServerError on shutdown");
                return false;
            }

            // A new connection must be refused.
            boolean refused = false;
            try {
                Socket s = new Socket(LOCALHOST, port);
                closeQuietly(s);
            } catch (IOException expected) {
                refused = true;
            }
            if (!refused) {
                System.out.println("  (diagnostic) new connection accepted after stop");
                return false;
            }

            return true;
        } finally {
            for (Socket client : clients) {
                closeQuietly(client);
            }
            if (server != null) {
                server.stop();
            }
            Thread.sleep(300);
        }
    }

    // Test 6: Concurrent connect and remove with finite timeouts
    private static boolean testConcurrentClientLifecycle() throws Exception {
        ChatServer server = null;
        List<Socket> clients = new ArrayList<>();
        final Object clientsLock = new Object();

        try {
            server = new ChatServer(0);
            final ChatServer finalServer = server;
            TestServerListener listener = new TestServerListener();
            final TestServerListener finalListener = listener;
            server.setServerListener(listener);

            server.start();

            if (!listener.awaitStarted(TEST_TIMEOUT_MS)) {
                return false;
            }
            final int port = listener.getReportedPort();

            // Phase 1: establish 4 baseline clients.
            for (int i = 0; i < 4; i++) {
                Socket s = new Socket(LOCALHOST, port);
                synchronized (clientsLock) {
                    clients.add(s);
                }
            }
            if (!waitForConnectedCallbacks(listener, 4, TEST_TIMEOUT_MS)) {
                return false;
            }
            if (!waitForServerCount(server, 4, TEST_TIMEOUT_MS)) {
                return false;
            }

            // Capture two pre-existing IDs for concurrent removal.
            List<ClientConnection> baseline = listener.getConnectedConnections();
            final long removeId1 = baseline.get(0).getConnectionId();
            final long removeId2 = baseline.get(1).getConnectionId();

            // Thread A: connect 2 more clients.
            Thread connectThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int i = 0; i < 2; i++) {
                            Socket s = new Socket(LOCALHOST, port);
                            synchronized (clientsLock) {
                                clients.add(s);
                            }
                            Thread.sleep(50);
                        }
                    } catch (Exception e) {
                        // Recorded via counts below.
                    }
                }
            });

            // Thread B: remove the two pre-existing clients + exercise read APIs.
            final AtomicInteger readFailures = new AtomicInteger(0);
            Thread removeThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(50);
                        finalServer.removeClient(removeId1);
                        // Concurrent reads must never throw.
                        try {
                            finalServer.getConnectedClientCount();
                            finalServer.getClientConnections();
                        } catch (Exception e) {
                            readFailures.incrementAndGet();
                        }
                        Thread.sleep(50);
                        finalServer.removeClient(removeId2);
                        try {
                            finalServer.getConnectedClientCount();
                            finalServer.getClientConnections();
                        } catch (Exception e) {
                            readFailures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Ignore; verified below.
                    }
                }
            });

            connectThread.start();
            removeThread.start();

            connectThread.join(TEST_TIMEOUT_MS);
            removeThread.join(TEST_TIMEOUT_MS);

            // No deadlock: both threads must finish within the finite timeout.
            if (connectThread.isAlive() || removeThread.isAlive()) {
                System.out.println("  (diagnostic) worker thread deadlock/timeout");
                return false;
            }
            if (readFailures.get() != 0) {
                return false;
            }

            // Expected: 4 baseline - 2 removed + 2 new = 4.
            if (!waitForServerCount(finalServer, 4, TEST_TIMEOUT_MS)) {
                System.out.println("  (diagnostic) final count="
                        + finalServer.getConnectedClientCount() + ", expected 4");
                return false;
            }
            if (!waitForConnectedCallbacks(finalListener, 6, TEST_TIMEOUT_MS)) {
                return false;
            }
            if (!waitForDisconnectedCallbacks(finalListener, 2, TEST_TIMEOUT_MS)) {
                return false;
            }

            int connected = finalServer.getConnectedClientCount();
            int totalConnected = finalListener.getConnectedCount();
            int totalDisconnected = finalListener.getDisconnectedCount();
            if (connected != (totalConnected - totalDisconnected)) {
                System.out.println("  (diagnostic) count mismatch: live=" + connected
                        + " total=" + totalConnected + " removed=" + totalDisconnected);
                return false;
            }

            return true;
        } finally {
            synchronized (clientsLock) {
                for (Socket client : clients) {
                    closeQuietly(client);
                }
            }
            if (server != null) {
                server.stop();
            }
            Thread.sleep(300);
        }
    }
}
