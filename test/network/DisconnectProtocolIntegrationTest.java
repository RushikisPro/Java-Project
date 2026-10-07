package network;

import model.DisconnectMessage;
import model.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for disconnect protocol.
 */
public class DisconnectProtocolIntegrationTest {
    
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    
    public static void main(String[] args) {
        System.out.println("=== Disconnect Protocol Integration Tests ===");
        
        testGracefulClientDisconnect();
        testGracefulHostDisconnect();
        testExactlyOnceDisconnectDelivery();
        testTransportRemainsRaw();
        testRemoteEofAfterGracefulLine();
        
        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);
        
        if (testsFailed > 0) {
            System.exit(1);
        }
    }
    
    private static void testGracefulClientDisconnect() {
        System.out.print("Test 1: Graceful client disconnect... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSideSocket = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            // Start ephemeral server
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            // Connect client
            clientSocket = new Socket("localhost", port);
            serverSideSocket = serverSocket.accept();
            
            // Create handlers
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSideSocket);
            
            final AtomicInteger serverReceived = new AtomicInteger(0);
            final DisconnectMessage[] serverReceivedMessage = new DisconnectMessage[1];
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    if (MessageCodec.detectType(line) == MessageCodec.ProtocolType.DISCONNECT) {
                        try {
                            serverReceivedMessage[0] = MessageCodec.decodeDisconnect(line);
                            serverReceived.incrementAndGet();
                        } catch (MessageFormatException e) {
                            // Ignore
                        }
                    }
                }
                
                @Override
                public void onDisconnected() {
                    // Ignore
                }
                
                @Override
                public void onMessageError(Exception exception) {
                    // Ignore
                }
            });
            
            // Start handlers
            clientHandler.start();
            serverHandler.start();
            
            // Send disconnect from client
            DisconnectMessage disconnectMessage = new DisconnectMessage("Alice", LocalDateTime.of(2026, 10, 3, 14, 32, 10));
            String encoded = MessageCodec.encodeDisconnect(disconnectMessage);
            clientHandler.sendMessage(encoded);
            
            // Wait for delivery
            Thread.sleep(100);
            
            // Verify
            if (serverReceived.get() != 1) {
                System.out.println("FAIL - Expected 1 disconnect, got " + serverReceived.get());
                testsFailed++;
                return;
            }
            if (serverReceivedMessage[0] == null) {
                System.out.println("FAIL - No disconnect message received");
                testsFailed++;
                return;
            }
            if (!serverReceivedMessage[0].getSender().equals("Alice")) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            closeQuietly(clientHandler);
            closeQuietly(serverHandler);
            closeQuietly(clientSocket);
            closeQuietly(serverSideSocket);
            closeQuietly(serverSocket);
        }
    }
    
    private static void testGracefulHostDisconnect() {
        System.out.print("Test 2: Graceful host disconnect... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSideSocket = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            // Start ephemeral server
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            // Connect client
            clientSocket = new Socket("localhost", port);
            serverSideSocket = serverSocket.accept();
            
            // Create handlers
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSideSocket);
            
            final AtomicInteger clientReceived = new AtomicInteger(0);
            final DisconnectMessage[] clientReceivedMessage = new DisconnectMessage[1];
            
            clientHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    if (MessageCodec.detectType(line) == MessageCodec.ProtocolType.DISCONNECT) {
                        try {
                            clientReceivedMessage[0] = MessageCodec.decodeDisconnect(line);
                            clientReceived.incrementAndGet();
                        } catch (MessageFormatException e) {
                            // Ignore
                        }
                    }
                }
                
                @Override
                public void onDisconnected() {
                    // Ignore
                }
                
                @Override
                public void onMessageError(Exception exception) {
                    // Ignore
                }
            });
            
            // Start handlers
            clientHandler.start();
            serverHandler.start();
            
            // Send disconnect from server
            DisconnectMessage disconnectMessage = new DisconnectMessage("Bob", LocalDateTime.of(2026, 10, 3, 14, 33, 10));
            String encoded = MessageCodec.encodeDisconnect(disconnectMessage);
            serverHandler.sendMessage(encoded);
            
            // Wait for delivery
            Thread.sleep(100);
            
            // Verify
            if (clientReceived.get() != 1) {
                System.out.println("FAIL - Expected 1 disconnect, got " + clientReceived.get());
                testsFailed++;
                return;
            }
            if (clientReceivedMessage[0] == null) {
                System.out.println("FAIL - No disconnect message received");
                testsFailed++;
                return;
            }
            if (!clientReceivedMessage[0].getSender().equals("Bob")) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            closeQuietly(clientHandler);
            closeQuietly(serverHandler);
            closeQuietly(clientSocket);
            closeQuietly(serverSideSocket);
            closeQuietly(serverSocket);
        }
    }
    
    private static void testExactlyOnceDisconnectDelivery() {
        System.out.print("Test 3: Exactly-once disconnect delivery... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSideSocket = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            // Start ephemeral server
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            // Connect client
            clientSocket = new Socket("localhost", port);
            serverSideSocket = serverSocket.accept();
            
            // Create handlers
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSideSocket);
            
            final AtomicInteger serverReceived = new AtomicInteger(0);
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    if (MessageCodec.detectType(line) == MessageCodec.ProtocolType.DISCONNECT) {
                        serverReceived.incrementAndGet();
                    }
                }
                
                @Override
                public void onDisconnected() {
                    // Ignore
                }
                
                @Override
                public void onMessageError(Exception exception) {
                    // Ignore
                }
            });
            
            // Start handlers
            clientHandler.start();
            serverHandler.start();
            
            // Send one disconnect
            DisconnectMessage disconnectMessage = new DisconnectMessage("Alice", LocalDateTime.now());
            String encoded = MessageCodec.encodeDisconnect(disconnectMessage);
            clientHandler.sendMessage(encoded);
            
            // Wait for delivery
            Thread.sleep(100);
            
            // Verify exactly one
            if (serverReceived.get() != 1) {
                System.out.println("FAIL - Expected 1 disconnect, got " + serverReceived.get());
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            closeQuietly(clientHandler);
            closeQuietly(serverHandler);
            closeQuietly(clientSocket);
            closeQuietly(serverSideSocket);
            closeQuietly(serverSocket);
        }
    }
    
    private static void testTransportRemainsRaw() {
        System.out.print("Test 4: Transport remains raw... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSideSocket = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            // Start ephemeral server
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            // Connect client
            clientSocket = new Socket("localhost", port);
            serverSideSocket = serverSocket.accept();
            
            // Create handlers
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSideSocket);
            
            final String[] serverReceivedLine = new String[1];
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    serverReceivedLine[0] = line;
                }
                
                @Override
                public void onDisconnected() {
                    // Ignore
                }
                
                @Override
                public void onMessageError(Exception exception) {
                    // Ignore
                }
            });
            
            // Start handlers
            clientHandler.start();
            serverHandler.start();
            
            // Send disconnect
            DisconnectMessage disconnectMessage = new DisconnectMessage("Alice", LocalDateTime.now());
            String encoded = MessageCodec.encodeDisconnect(disconnectMessage);
            clientHandler.sendMessage(encoded);
            
            // Wait for delivery
            Thread.sleep(100);
            
            // Verify raw line matches
            if (serverReceivedLine[0] == null) {
                System.out.println("FAIL - No line received");
                testsFailed++;
                return;
            }
            if (!serverReceivedLine[0].equals(encoded)) {
                System.out.println("FAIL - Line was modified during transport");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            closeQuietly(clientHandler);
            closeQuietly(serverHandler);
            closeQuietly(clientSocket);
            closeQuietly(serverSideSocket);
            closeQuietly(serverSocket);
        }
    }
    
    private static void testRemoteEofAfterGracefulLine() {
        System.out.print("Test 5: Remote EOF after graceful line... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSideSocket = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            // Start ephemeral server
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            // Connect client
            clientSocket = new Socket("localhost", port);
            serverSideSocket = serverSocket.accept();
            
            // Create handlers
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSideSocket);
            
            final AtomicInteger disconnectCount = new AtomicInteger(0);
            final AtomicInteger disconnectCallbackCount = new AtomicInteger(0);
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    if (MessageCodec.detectType(line) == MessageCodec.ProtocolType.DISCONNECT) {
                        disconnectCount.incrementAndGet();
                    }
                }
                
                @Override
                public void onDisconnected() {
                    disconnectCallbackCount.incrementAndGet();
                }
                
                @Override
                public void onMessageError(Exception exception) {
                    // Ignore
                }
            });
            
            // Start handlers
            clientHandler.start();
            serverHandler.start();
            
            // Send disconnect
            DisconnectMessage disconnectMessage = new DisconnectMessage("Alice", LocalDateTime.now());
            String encoded = MessageCodec.encodeDisconnect(disconnectMessage);
            clientHandler.sendMessage(encoded);
            
            // Wait for delivery
            Thread.sleep(100);
            
            // Stop sender
            clientHandler.stop();
            
            // Wait for EOF to propagate
            Thread.sleep(100);
            
            // Verify
            if (disconnectCount.get() != 1) {
                System.out.println("FAIL - Expected 1 disconnect line, got " + disconnectCount.get());
                testsFailed++;
                return;
            }
            if (disconnectCallbackCount.get() > 1) {
                System.out.println("FAIL - More than one disconnect callback: " + disconnectCallbackCount.get());
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            closeQuietly(clientHandler);
            closeQuietly(serverHandler);
            closeQuietly(clientSocket);
            closeQuietly(serverSideSocket);
            closeQuietly(serverSocket);
        }
    }
    
    private static void closeQuietly(MessageHandler handler) {
        if (handler != null) {
            handler.stop();
        }
    }
    
    private static void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    private static void closeQuietly(ServerSocket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
