package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle tests for ChatServer without Swing.
 * Tests server start/stop behavior, client acceptance, and race conditions.
 */
public class ChatServerLifecycleTest {
    
    private static final int TEST_TIMEOUT_MS = 5000;
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    
    public static void main(String[] args) {
        System.out.println("=== ChatServer Lifecycle Tests ===");
        
        testServerStarts();
        testClientAccepted();
        testStopWhileAccepting();
        testImmediateStopRace();
        testRepeatedStop();
        testSingleClientBehavior();
        
        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);
        
        if (testsFailed > 0) {
            System.exit(1);
        }
    }
    
    private static void testServerStarts() {
        System.out.print("Test 1: Server starts... ");
        
        ChatServer server = null;
        
        try {
            server = new ChatServer(0);
            
            final CountDownLatch startedLatch = new CountDownLatch(1);
            final AtomicInteger reportedPort = new AtomicInteger(0);
            final AtomicInteger errorCount = new AtomicInteger(0);
            
            server.setServerListener(new ChatServer.ServerListener() {
                @Override
                public void onServerStarted(String ipAddress, int port) {
                    reportedPort.set(port);
                    startedLatch.countDown();
                }
                
                @Override
                public void onClientConnected(ClientConnection connection) {}
                
                @Override
                public void onClientDisconnected(ClientConnection connection) {}
                
                @Override
                public void onServerError(Exception exception) {
                    errorCount.incrementAndGet();
                }
                
                @Override
                public void onServerStopped() {}
            });
            
            server.start();
            
            boolean started = startedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!started) {
                System.out.println("FAIL - Server did not start");
                testsFailed++;
                return;
            }
            
            if (reportedPort.get() <= 0) {
                System.out.println("FAIL - Reported port is not greater than zero: " + reportedPort.get());
                testsFailed++;
                return;
            }
            
            if (!server.isRunning()) {
                System.out.println("FAIL - isRunning() returned false while server was running");
                testsFailed++;
                return;
            }
            
            if (errorCount.get() > 0) {
                System.out.println("FAIL - Error callback occurred during start");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
    
    private static void testClientAccepted() {
        System.out.print("Test 2: Client accepted... ");
        
        ChatServer server = null;
        Socket clientSocket = null;
        
        try {
            server = new ChatServer(0);
            
            final CountDownLatch startedLatch = new CountDownLatch(1);
            final CountDownLatch connectedLatch = new CountDownLatch(1);
            final AtomicInteger reportedPort = new AtomicInteger(0);
            final AtomicReference<Socket> acceptedSocket = new AtomicReference<>();
            final AtomicInteger errorCount = new AtomicInteger(0);
            
            server.setServerListener(new ChatServer.ServerListener() {
                @Override
                public void onServerStarted(String ipAddress, int port) {
                    reportedPort.set(port);
                    startedLatch.countDown();
                }
                
                @Override
                public void onClientConnected(ClientConnection connection) {
                    acceptedSocket.set(connection.getSocket());
                    connectedLatch.countDown();
                }
                
                @Override
                public void onClientDisconnected(ClientConnection connection) {}
                
                @Override
                public void onServerError(Exception exception) {
                    errorCount.incrementAndGet();
                }
                
                @Override
                public void onServerStopped() {}
            });
            
            server.start();
            
            boolean started = startedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!started) {
                System.out.println("FAIL - Server did not start");
                testsFailed++;
                return;
            }
            
            // Connect a client
            int port = reportedPort.get();
            clientSocket = new Socket("localhost", port);
            
            boolean connected = connectedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!connected) {
                System.out.println("FAIL - Client was not accepted");
                testsFailed++;
                return;
            }
            
            Socket socket = acceptedSocket.get();
            if (socket == null) {
                System.out.println("FAIL - Accepted socket is null");
                testsFailed++;
                return;
            }
            
            if (!socket.isConnected()) {
                System.out.println("FAIL - Accepted socket is not connected");
                testsFailed++;
                return;
            }
            
            if (errorCount.get() > 0) {
                System.out.println("FAIL - Error callback occurred");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (clientSocket != null && !clientSocket.isClosed()) {
                try {
                    clientSocket.close();
                } catch (IOException e) {}
            }
            if (server != null) {
                server.stop();
            }
        }
    }
    
    private static void testStopWhileAccepting() {
        System.out.print("Test 3: Stop while accepting... ");
        
        ChatServer server = null;
        
        try {
            server = new ChatServer(0);
            
            final CountDownLatch startedLatch = new CountDownLatch(1);
            final AtomicInteger reportedPort = new AtomicInteger(0);
            final AtomicInteger errorCount = new AtomicInteger(0);
            
            server.setServerListener(new ChatServer.ServerListener() {
                @Override
                public void onServerStarted(String ipAddress, int port) {
                    reportedPort.set(port);
                    startedLatch.countDown();
                }
                
                @Override
                public void onClientConnected(ClientConnection connection) {}
                
                @Override
                public void onClientDisconnected(ClientConnection connection) {}
                
                @Override
                public void onServerError(Exception exception) {
                    errorCount.incrementAndGet();
                }
                
                @Override
                public void onServerStopped() {}
            });
            
            server.start();
            
            boolean started = startedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!started) {
                System.out.println("FAIL - Server did not start");
                testsFailed++;
                return;
            }
            
            // Stop without connecting a client
            server.stop();
            
            // Wait a bit to ensure cleanup
            Thread.sleep(500);
            
            if (errorCount.get() > 0) {
                System.out.println("FAIL - Error callback occurred during stop");
                testsFailed++;
                return;
            }
            
            if (server.isRunning()) {
                System.out.println("FAIL - isRunning() returned true after stop");
                testsFailed++;
                return;
            }
            
            // Verify the port no longer accepts connections
            int port = reportedPort.get();
            Socket testSocket = null;
            try {
                testSocket = new Socket("localhost", port);
                System.out.println("FAIL - Port still accepts connections after stop");
                testsFailed++;
                return;
            } catch (IOException e) {
                // Expected - connection should be refused
            } finally {
                if (testSocket != null) {
                    try {
                        testSocket.close();
                    } catch (IOException e) {}
                }
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
    
    private static void testImmediateStopRace() {
        System.out.print("Test 4: Immediate stop race... ");
        
        final int iterations = 50;
        int failures = 0;
        
        for (int i = 0; i < iterations; i++) {
            ChatServer server = null;
            
            try {
                server = new ChatServer(0);
                
                final AtomicInteger errorCount = new AtomicInteger(0);
                
                server.setServerListener(new ChatServer.ServerListener() {
                    @Override
                    public void onServerStarted(String ipAddress, int port) {}
                    
                    @Override
                    public void onClientConnected(ClientConnection connection) {}

                    @Override
                    public void onClientDisconnected(ClientConnection connection) {}
                    
                    @Override
                    public void onServerError(Exception exception) {
                        errorCount.incrementAndGet();
                    }

                    @Override
                    public void onServerStopped() {}
                });
                
                server.start();
                server.stop();
                
                // Wait for the server to reach stopped state
                long startTime = System.currentTimeMillis();
                while (server.isRunning() && (System.currentTimeMillis() - startTime) < TEST_TIMEOUT_MS) {
                    Thread.sleep(10);
                }
                
                if (errorCount.get() > 0) {
                    failures++;
                }
                
                if (server.isRunning()) {
                    failures++;
                }
                
            } catch (Exception e) {
                failures++;
            } finally {
                if (server != null) {
                    server.stop();
                }
            }
        }
        
        if (failures > 0) {
            System.out.println("FAIL - " + failures + " out of " + iterations + " iterations failed");
            testsFailed++;
        } else {
            System.out.println("PASS");
            testsPassed++;
        }
    }
    
    private static void testRepeatedStop() {
        System.out.print("Test 5: Repeated stop... ");
        
        ChatServer server = null;
        
        try {
            server = new ChatServer(0);
            
            final AtomicInteger errorCount = new AtomicInteger(0);
            
            server.setServerListener(new ChatServer.ServerListener() {
                @Override
                public void onServerStarted(String ipAddress, int port) {}
                
                @Override
                public void onClientConnected(ClientConnection connection) {}
                
                @Override
                public void onClientDisconnected(ClientConnection connection) {}
                
                @Override
                public void onServerError(Exception exception) {
                    errorCount.incrementAndGet();
                }
                
                @Override
                public void onServerStopped() {}
            });
            
            server.start();
            
            // Call stop multiple times
            server.stop();
            server.stop();
            server.stop();
            
            // Wait a bit
            Thread.sleep(500);
            
            if (errorCount.get() > 0) {
                System.out.println("FAIL - Error callback occurred during repeated stop");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
    
    private static void testSingleClientBehavior() {
        System.out.print("Test 6: Multi-client behavior... ");
        
        ChatServer server = null;
        Socket firstClient = null;
        Socket secondClient = null;
        
        try {
            server = new ChatServer(0);
            
            final CountDownLatch startedLatch = new CountDownLatch(1);
            final CountDownLatch connectedLatch = new CountDownLatch(1);
            final AtomicInteger reportedPort = new AtomicInteger(0);
            final AtomicReference<Socket> acceptedSocket = new AtomicReference<>();
            
            server.setServerListener(new ChatServer.ServerListener() {
                @Override
                public void onServerStarted(String ipAddress, int port) {
                    reportedPort.set(port);
                    startedLatch.countDown();
                }
                
                @Override
                public void onClientConnected(ClientConnection connection) {
                    acceptedSocket.set(connection.getSocket());
                    connectedLatch.countDown();
                }
                
                @Override
                public void onClientDisconnected(ClientConnection connection) {}
                
                @Override
                public void onServerError(Exception exception) {}
                
                @Override
                public void onServerStopped() {}
            });
            
            server.start();
            
            boolean started = startedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!started) {
                System.out.println("FAIL - Server did not start");
                testsFailed++;
                return;
            }
            
            // Connect first client
            int port = reportedPort.get();
            firstClient = new Socket("localhost", port);
            
            boolean connected = connectedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!connected) {
                System.out.println("FAIL - First client was not accepted");
                testsFailed++;
                return;
            }
            
            Socket socket = acceptedSocket.get();
            if (socket == null || !socket.isConnected()) {
                System.out.println("FAIL - First accepted socket is not connected");
                testsFailed++;
                return;
            }
            
            // Wait a bit to ensure server remains listening
            Thread.sleep(500);
            
            // Attempt second connection - should be accepted (multi-client architecture)
            secondClient = null;
            try {
                secondClient = new Socket("localhost", port);
            } catch (IOException e) {
                System.out.println("FAIL - Second connection was refused (should be accepted in multi-client mode)");
                testsFailed++;
                return;
            }
            
            // Verify first socket is still usable
            if (socket.isClosed()) {
                System.out.println("FAIL - First accepted socket was closed prematurely");
                testsFailed++;
                return;
            }
            
            // Verify second socket is connected
            if (!secondClient.isConnected()) {
                System.out.println("FAIL - Second socket is not connected");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            if (firstClient != null && !firstClient.isClosed()) {
                try {
                    firstClient.close();
                } catch (IOException e) {}
            }
            if (secondClient != null && !secondClient.isClosed()) {
                try {
                    secondClient.close();
                } catch (IOException e) {}
            }
            if (server != null) {
                server.stop();
            }
        }
    }
}
