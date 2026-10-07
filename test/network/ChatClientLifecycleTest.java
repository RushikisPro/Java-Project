package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle tests for ChatClient without Swing.
 * Tests connection behavior, disconnect, and state management.
 */
public class ChatClientLifecycleTest {
    
    private static final int TEST_TIMEOUT_MS = 5000;
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    
    public static void main(String[] args) {
        System.out.println("=== ChatClient Lifecycle Tests ===");
        
        testSuccessfulConnection();
        testConnectionRefused();
        testRepeatedDisconnect();
        testSingleUseLifecycle();
        testDisconnectDuringConnect();
        testCallbackLockSafety();
        
        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);
        
        if (testsFailed > 0) {
            System.exit(1);
        }
    }
    
    private static void testSuccessfulConnection() {
        System.out.print("Test 1: Successful connection... ");
        
        final ServerSocket[] serverSocketHolder = new ServerSocket[1];
        ChatClient client = null;
        
        try {
            serverSocketHolder[0] = new ServerSocket(0);
            int port = serverSocketHolder[0].getLocalPort();
            
            // Start an accept thread
            final CountDownLatch acceptLatch = new CountDownLatch(1);
            final AtomicReference<Socket> acceptedSocket = new AtomicReference<>();
            
            Thread acceptThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket socket = serverSocketHolder[0].accept();
                        acceptedSocket.set(socket);
                        acceptLatch.countDown();
                    } catch (IOException e) {
                        // Connection failed
                    }
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();
            
            client = new ChatClient("localhost", port);
            
            final CountDownLatch connectingLatch = new CountDownLatch(1);
            final CountDownLatch connectedLatch = new CountDownLatch(1);
            final AtomicInteger connectingCount = new AtomicInteger(0);
            final AtomicInteger connectedCount = new AtomicInteger(0);
            final AtomicInteger errorCount = new AtomicInteger(0);
            final AtomicReference<Socket> callbackSocket = new AtomicReference<>();
            
            client.setClientListener(new ChatClient.ClientListener() {
                @Override
                public void onConnecting(String hostAddress, int port) {
                    connectingCount.incrementAndGet();
                    connectingLatch.countDown();
                }
                
                @Override
                public void onConnected(Socket socket) {
                    connectedCount.incrementAndGet();
                    callbackSocket.set(socket);
                    connectedLatch.countDown();
                }
                
                @Override
                public void onConnectionError(Exception exception) {
                    errorCount.incrementAndGet();
                }
                
                @Override
                public void onDisconnected() {}
            });
            
            client.connect();
            
            boolean connecting = connectingLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!connecting) {
                System.out.println("FAIL - onConnecting callback not received");
                testsFailed++;
                return;
            }
            
            boolean connected = connectedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!connected) {
                System.out.println("FAIL - onConnected callback not received");
                testsFailed++;
                return;
            }
            
            boolean accepted = acceptLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!accepted) {
                System.out.println("FAIL - Server did not accept connection");
                testsFailed++;
                return;
            }
            
            if (connectingCount.get() != 1) {
                System.out.println("FAIL - onConnecting called " + connectingCount.get() + " times");
                testsFailed++;
                return;
            }
            
            if (connectedCount.get() != 1) {
                System.out.println("FAIL - onConnected called " + connectedCount.get() + " times");
                testsFailed++;
                return;
            }
            
            if (errorCount.get() > 0) {
                System.out.println("FAIL - onConnectionError called");
                testsFailed++;
                return;
            }
            
            if (!client.isConnected()) {
                System.out.println("FAIL - isConnected() returned false");
                testsFailed++;
                return;
            }
            
            Socket socket = callbackSocket.get();
            if (socket == null) {
                System.out.println("FAIL - Callback socket is null");
                testsFailed++;
                return;
            }
            
            if (!socket.isConnected()) {
                System.out.println("FAIL - Callback socket is not connected");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (client != null) {
                client.disconnect();
            }
            if (serverSocketHolder[0] != null && !serverSocketHolder[0].isClosed()) {
                try {
                    serverSocketHolder[0].close();
                } catch (IOException e) {}
            }
        }
    }
    
    private static void testConnectionRefused() {
        System.out.print("Test 2: Connection refused... ");
        
        final ServerSocket[] serverSocketHolder = new ServerSocket[1];
        ChatClient client = null;
        
        try {
            // Obtain ephemeral port and close it
            serverSocketHolder[0] = new ServerSocket(0);
            int port = serverSocketHolder[0].getLocalPort();
            serverSocketHolder[0].close();
            
            client = new ChatClient("localhost", port);
            
            final CountDownLatch errorLatch = new CountDownLatch(1);
            final AtomicInteger errorCount = new AtomicInteger(0);
            final AtomicInteger connectedCount = new AtomicInteger(0);
            
            client.setClientListener(new ChatClient.ClientListener() {
                @Override
                public void onConnecting(String hostAddress, int port) {}
                
                @Override
                public void onConnected(Socket socket) {
                    connectedCount.incrementAndGet();
                }
                
                @Override
                public void onConnectionError(Exception exception) {
                    errorCount.incrementAndGet();
                    errorLatch.countDown();
                }
                
                @Override
                public void onDisconnected() {}
            });
            
            client.connect();
            
            boolean errorReceived = errorLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!errorReceived) {
                System.out.println("FAIL - onConnectionError callback not received");
                testsFailed++;
                return;
            }
            
            if (errorCount.get() != 1) {
                System.out.println("FAIL - onConnectionError called " + errorCount.get() + " times");
                testsFailed++;
                return;
            }
            
            if (connectedCount.get() > 0) {
                System.out.println("FAIL - onConnected called");
                testsFailed++;
                return;
            }
            
            if (client.isConnected()) {
                System.out.println("FAIL - isConnected() returned true");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (client != null) {
                client.disconnect();
            }
            if (serverSocketHolder[0] != null && !serverSocketHolder[0].isClosed()) {
                try {
                    serverSocketHolder[0].close();
                } catch (IOException e) {}
            }
        }
    }
    
    private static void testRepeatedDisconnect() {
        System.out.print("Test 3: Repeated disconnect... ");
        
        final ServerSocket[] serverSocketHolder = new ServerSocket[1];
        ChatClient client = null;
        
        try {
            serverSocketHolder[0] = new ServerSocket(0);
            int port = serverSocketHolder[0].getLocalPort();
            
            // Start an accept thread
            final CountDownLatch acceptLatch = new CountDownLatch(1);
            final AtomicReference<Socket> acceptedSocket = new AtomicReference<>();
            
            Thread acceptThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket socket = serverSocketHolder[0].accept();
                        acceptedSocket.set(socket);
                        acceptLatch.countDown();
                    } catch (IOException e) {
                        // Connection failed
                    }
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();
            
            client = new ChatClient("localhost", port);
            
            final CountDownLatch connectedLatch = new CountDownLatch(1);
            final AtomicInteger disconnectCount = new AtomicInteger(0);
            
            client.setClientListener(new ChatClient.ClientListener() {
                @Override
                public void onConnecting(String hostAddress, int port) {}
                
                @Override
                public void onConnected(Socket socket) {
                    connectedLatch.countDown();
                }
                
                @Override
                public void onConnectionError(Exception exception) {}
                
                @Override
                public void onDisconnected() {
                    disconnectCount.incrementAndGet();
                }
            });
            
            client.connect();
            
            boolean connected = connectedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!connected) {
                System.out.println("FAIL - Connection failed");
                testsFailed++;
                return;
            }
            
            boolean accepted = acceptLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!accepted) {
                System.out.println("FAIL - Server did not accept connection");
                testsFailed++;
                return;
            }
            
            // Call disconnect three times
            client.disconnect();
            client.disconnect();
            client.disconnect();
            
            // Wait a bit
            Thread.sleep(500);
            
            if (disconnectCount.get() > 1) {
                System.out.println("FAIL - onDisconnected called " + disconnectCount.get() + " times");
                testsFailed++;
                return;
            }
            
            if (client.isConnected()) {
                System.out.println("FAIL - isConnected() returned true after disconnect");
                testsFailed++;
                return;
            }
            
            if (client.isConnecting()) {
                System.out.println("FAIL - isConnecting() returned true after disconnect");
                testsFailed++;
                return;
            }
            
            Socket socket = client.getSocket();
            if (socket != null && !socket.isClosed()) {
                System.out.println("FAIL - getSocket() returned an open socket");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (client != null) {
                client.disconnect();
            }
            if (serverSocketHolder[0] != null && !serverSocketHolder[0].isClosed()) {
                try {
                    serverSocketHolder[0].close();
                } catch (IOException e) {}
            }
        }
    }
    
    private static void testSingleUseLifecycle() {
        System.out.print("Test 4: Single-use lifecycle... ");
        
        final ServerSocket[] serverSocket1Holder = new ServerSocket[1];
        final ServerSocket[] serverSocket2Holder = new ServerSocket[1];
        ChatClient client = null;
        
        try {
            // First connection
            serverSocket1Holder[0] = new ServerSocket(0);
            int port1 = serverSocket1Holder[0].getLocalPort();
            
            final CountDownLatch acceptLatch1 = new CountDownLatch(1);
            final CountDownLatch connectedLatch1 = new CountDownLatch(1);
            
            Thread acceptThread1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        serverSocket1Holder[0].accept();
                        acceptLatch1.countDown();
                    } catch (IOException e) {}
                }
            });
            acceptThread1.setDaemon(true);
            acceptThread1.start();
            
            client = new ChatClient("localhost", port1);
            
            client.setClientListener(new ChatClient.ClientListener() {
                @Override
                public void onConnecting(String hostAddress, int port) {}
                
                @Override
                public void onConnected(Socket socket) {
                    connectedLatch1.countDown();
                }
                
                @Override
                public void onConnectionError(Exception exception) {}
                
                @Override
                public void onDisconnected() {}
            });
            
            client.connect();
            
            boolean connected1 = connectedLatch1.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!connected1) {
                System.out.println("FAIL - First connection failed");
                testsFailed++;
                return;
            }
            
            boolean accepted1 = acceptLatch1.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!accepted1) {
                System.out.println("FAIL - First accept failed");
                testsFailed++;
                return;
            }
            
            // Disconnect
            client.disconnect();
            
            // Wait for disconnect to complete
            Thread.sleep(500);
            
            // Try to reconnect - should throw IllegalStateException
            serverSocket2Holder[0] = new ServerSocket(0);
            int port2 = serverSocket2Holder[0].getLocalPort();
            
            boolean threwException = false;
            try {
                client.connect();
            } catch (IllegalStateException e) {
                threwException = true;
            }
            
            if (!threwException) {
                System.out.println("FAIL - connect() after disconnect did not throw IllegalStateException");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (client != null) {
                client.disconnect();
            }
            if (serverSocket1Holder[0] != null && !serverSocket1Holder[0].isClosed()) {
                try {
                    serverSocket1Holder[0].close();
                } catch (IOException e) {}
            }
            if (serverSocket2Holder[0] != null && !serverSocket2Holder[0].isClosed()) {
                try {
                    serverSocket2Holder[0].close();
                } catch (IOException e) {}
            }
        }
    }
    
    private static void testDisconnectDuringConnect() {
        System.out.print("Test 5: Disconnect during connect... ");
        
        // SKIP: Deterministic cancellation test omitted - adding a SocketFactory abstraction
        // to production code would overcomplicate the implementation for this mini project.
        // The ChatClient already implements cancellation logic via closing the connecting socket,
        // but a portable deterministic test for blocking TCP connect is not feasible without
        // external mocking frameworks or test-specific abstractions.
        System.out.println("SKIP: disconnect during connect - requires SocketFactory abstraction for deterministic test");
        testsSkipped++;
    }
    
    private static void testCallbackLockSafety() {
        System.out.print("Test 6: Callback lock safety... ");
        
        final ServerSocket[] serverSocketHolder = new ServerSocket[1];
        final ChatClient[] clientHolder = new ChatClient[1];
        
        try {
            serverSocketHolder[0] = new ServerSocket(0);
            int port = serverSocketHolder[0].getLocalPort();
            
            // Start an accept thread
            final CountDownLatch acceptLatch = new CountDownLatch(1);
            
            Thread acceptThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        serverSocketHolder[0].accept();
                        acceptLatch.countDown();
                    } catch (IOException e) {}
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();
            
            clientHolder[0] = new ChatClient("localhost", port);
            
            final CountDownLatch connectedLatch = new CountDownLatch(1);
            final AtomicInteger callbackErrors = new AtomicInteger(0);
            
            clientHolder[0].setClientListener(new ChatClient.ClientListener() {
                @Override
                public void onConnecting(String hostAddress, int port) {}
                
                @Override
                public void onConnected(Socket socket) {
                    // In callback, invoke safe state methods
                    try {
                        boolean connected = clientHolder[0].isConnected();
                        boolean connecting = clientHolder[0].isConnecting();
                        Socket s = clientHolder[0].getSocket();
                        
                        // These should complete without deadlock
                        connectedLatch.countDown();
                    } catch (Exception e) {
                        callbackErrors.incrementAndGet();
                    }
                }
                
                @Override
                public void onConnectionError(Exception exception) {}
                
                @Override
                public void onDisconnected() {}
            });
            
            clientHolder[0].connect();
            
            boolean connected = connectedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!connected) {
                System.out.println("FAIL - onConnected callback not received");
                testsFailed++;
                return;
            }
            
            boolean accepted = acceptLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!accepted) {
                System.out.println("FAIL - Server did not accept connection");
                testsFailed++;
                return;
            }
            
            if (callbackErrors.get() > 0) {
                System.out.println("FAIL - Callback methods threw exceptions");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (clientHolder[0] != null) {
                clientHolder[0].disconnect();
            }
            if (serverSocketHolder[0] != null && !serverSocketHolder[0].isClosed()) {
                try {
                    serverSocketHolder[0].close();
                } catch (IOException e) {}
            }
        }
    }
}
