package network;

import model.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integration tests for Message protocol with MessageHandler.
 */
public class MessageProtocolIntegrationTest {
    
    private static final int TEST_TIMEOUT_MS = 5000;
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    
    public static void main(String[] args) {
        System.out.println("=== Message Protocol Integration Tests ===");
        
        testAliceToBobProtocol();
        testBobToAliceUnicodeProtocol();
        testExactlyOnceProtocolDelivery();
        testMalformedProtocolSeparation();
        testMaximumSizeProtocolMessage();
        
        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);
        
        if (testsFailed > 0) {
            System.exit(1);
        }
    }
    
    private static void testAliceToBobProtocol() {
        System.out.print("Test 1: Alice to Bob protocol... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSocketAccepted);
            
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Message> receivedMessage = new AtomicReference<>();
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    try {
                        Message message = MessageCodec.decode(line);
                        receivedMessage.set(message);
                        latch.countDown();
                    } catch (MessageFormatException e) {
                        // Should not happen
                    }
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            clientHandler.start();
            serverHandler.start();
            
            // Alice sends a message
            LocalDateTime fixedTimestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message aliceMessage = new Message("Alice", "Hello Bob", fixedTimestamp);
            String encoded = MessageCodec.encode(aliceMessage);
            clientHandler.sendMessage(encoded);
            
            boolean received = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Message not received");
                testsFailed++;
                return;
            }
            
            Message bobReceived = receivedMessage.get();
            if (!bobReceived.getSender().equals("Alice")) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            if (!bobReceived.getText().equals("Hello Bob")) {
                System.out.println("FAIL - Text mismatch");
                testsFailed++;
                return;
            }
            if (!bobReceived.getTimestamp().equals(fixedTimestamp)) {
                System.out.println("FAIL - Timestamp mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(clientHandler, serverHandler);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void testBobToAliceUnicodeProtocol() {
        System.out.print("Test 2: Bob to Alice Unicode protocol... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSocketAccepted);
            
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Message> receivedMessage = new AtomicReference<>();
            
            clientHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    try {
                        Message message = MessageCodec.decode(line);
                        receivedMessage.set(message);
                        latch.countDown();
                    } catch (MessageFormatException e) {
                        // Should not happen
                    }
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            clientHandler.start();
            serverHandler.start();
            
            // Bob sends a Unicode message
            LocalDateTime fixedTimestamp = LocalDateTime.of(2026, 10, 3, 14, 33, 10);
            String sender = "\u0905\u0932\u093F\u0938"; // अलिस (Alice in Hindi)
            String text = "\u0928\u092E\u0938\u094D\u0924\u0947 Bob \uD83D\uDC4B"; // नमस्ते Bob 👋
            Message bobMessage = new Message(sender, text, fixedTimestamp);
            String encoded = MessageCodec.encode(bobMessage);
            serverHandler.sendMessage(encoded);
            
            boolean received = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Message not received");
                testsFailed++;
                return;
            }
            
            Message aliceReceived = receivedMessage.get();
            if (!aliceReceived.getSender().equals(sender)) {
                System.out.println("FAIL - Sender mismatch");
                testsFailed++;
                return;
            }
            if (!aliceReceived.getText().equals(text)) {
                System.out.println("FAIL - Text mismatch");
                testsFailed++;
                return;
            }
            if (!aliceReceived.getTimestamp().equals(fixedTimestamp)) {
                System.out.println("FAIL - Timestamp mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(clientHandler, serverHandler);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void testExactlyOnceProtocolDelivery() {
        System.out.print("Test 3: Exactly-once protocol delivery... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSocketAccepted);
            
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger messageCount = new AtomicInteger(0);
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    messageCount.incrementAndGet();
                    latch.countDown();
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            clientHandler.start();
            serverHandler.start();
            
            // Send one encoded message
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message message = new Message("Alice", "Hello", timestamp);
            String encoded = MessageCodec.encode(message);
            clientHandler.sendMessage(encoded);
            
            boolean received = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Message not received");
                testsFailed++;
                return;
            }
            
            if (messageCount.get() != 1) {
                System.out.println("FAIL - Message delivered " + messageCount.get() + " times");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(clientHandler, serverHandler);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void testMalformedProtocolSeparation() {
        System.out.print("Test 4: Malformed protocol separation... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSocketAccepted);
            
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<String> receivedLine = new AtomicReference<>();
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    receivedLine.set(line);
                    latch.countDown();
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            clientHandler.start();
            serverHandler.start();
            
            // Send a valid network line that is not a valid MessageCodec line
            String malformedLine = "NOT_CHAT|bad";
            clientHandler.sendMessage(malformedLine);
            
            boolean received = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Line not received");
                testsFailed++;
                return;
            }
            
            // Verify the line was transported unchanged
            if (!receivedLine.get().equals(malformedLine)) {
                System.out.println("FAIL - Line was modified during transport");
                testsFailed++;
                return;
            }
            
            // Verify MessageCodec rejects it
            try {
                MessageCodec.decode(receivedLine.get());
                System.out.println("FAIL - MessageCodec accepted malformed line");
                testsFailed++;
                return;
            } catch (MessageFormatException e) {
                // Expected
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(clientHandler, serverHandler);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void testMaximumSizeProtocolMessage() {
        System.out.print("Test 5: Maximum-size protocol message... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        MessageHandler clientHandler = null;
        MessageHandler serverHandler = null;
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            clientHandler = new MessageHandler(clientSocket);
            serverHandler = new MessageHandler(serverSocketAccepted);
            
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Message> receivedMessage = new AtomicReference<>();
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String line) {
                    try {
                        Message message = MessageCodec.decode(line);
                        receivedMessage.set(message);
                        latch.countDown();
                    } catch (MessageFormatException e) {
                        // Should not happen
                    }
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            clientHandler.start();
            serverHandler.start();
            
            // Send a message with exactly 1000 characters
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Message.MAX_MESSAGE_LENGTH; i++) {
                sb.append("a");
            }
            LocalDateTime timestamp = LocalDateTime.of(2026, 10, 3, 14, 32, 10);
            Message maxMessage = new Message("Alice", sb.toString(), timestamp);
            String encoded = MessageCodec.encode(maxMessage);
            clientHandler.sendMessage(encoded);
            
            boolean received = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Message not received");
                testsFailed++;
                return;
            }
            
            Message decodedMessage = receivedMessage.get();
            if (decodedMessage.getText().length() != Message.MAX_MESSAGE_LENGTH) {
                System.out.println("FAIL - Text length mismatch");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(clientHandler, serverHandler);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void cleanupHandlers(MessageHandler handler1, MessageHandler handler2) {
        if (handler1 != null) {
            handler1.stop();
        }
        if (handler2 != null) {
            handler2.stop();
        }
    }
    
    private static void cleanup(ServerSocket serverSocket, Socket clientSocket, Socket serverSocketAccepted) {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {}
        
        try {
            if (serverSocketAccepted != null && !serverSocketAccepted.isClosed()) {
                serverSocketAccepted.close();
            }
        } catch (IOException e) {}
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {}
    }
}
