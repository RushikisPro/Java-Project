package network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integration tests for MessageHandler without Swing.
 * Tests basic two-way messaging, error handling, and lifecycle behavior.
 */
public class MessageHandlerIntegrationTest {
    
    private static final int TEST_TIMEOUT_MS = 5000;
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int testsSkipped = 0;
    
    public static void main(String[] args) {
        System.out.println("=== MessageHandler Integration Tests ===");
        
        testNullListenerRejection();
        testListenerReplacementBeforeStart();
        testListenerAssignmentAfterStart();
        testClientToServerMessage();
        testServerToClientMessage();
        testInvalidMessageRejection();
        testRemoteDisconnect();
        testRepeatedStop();
        testDuplicateStart();
        testUTF8Message();
        testRapidClose();
        testCallbackLockSafety();
        
        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Skipped: " + testsSkipped);
        
        if (testsFailed > 0) {
            System.exit(1);
        }
    }
    
    private static void testNullListenerRejection() {
        System.out.print("Test 1: Null listener rejection... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            MessageHandler handler = new MessageHandler(clientSocket);
            
            try {
                handler.setMessageListener(null);
                System.out.println("FAIL - Null listener was accepted");
                testsFailed++;
                return;
            } catch (IllegalArgumentException e) {
                // Expected
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(null, null);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void testListenerReplacementBeforeStart() {
        System.out.print("Test 2: Listener replacement before start... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            MessageHandler handler = new MessageHandler(clientSocket);
            
            final AtomicInteger firstListenerCount = new AtomicInteger(0);
            final AtomicInteger secondListenerCount = new AtomicInteger(0);
            
            handler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    firstListenerCount.incrementAndGet();
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            // Replace listener before start - should be allowed
            handler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    secondListenerCount.incrementAndGet();
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            handler.start();
            
            // Create another handler to send a message
            MessageHandler sender = new MessageHandler(serverSocketAccepted);
            sender.start();
            sender.sendMessage("test");
            
            Thread.sleep(500);
            
            if (firstListenerCount.get() > 0) {
                System.out.println("FAIL - First listener still received messages");
                testsFailed++;
                return;
            }
            
            if (secondListenerCount.get() != 1) {
                System.out.println("FAIL - Second listener did not receive message");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(null, null);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void testListenerAssignmentAfterStart() {
        System.out.print("Test 3: Listener assignment after start... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            MessageHandler handler = new MessageHandler(clientSocket);
            
            handler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {}
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            handler.start();
            
            try {
                handler.setMessageListener(new MessageHandler.MessageListener() {
                    @Override
                    public void onMessageReceived(String message) {}
                    
                    @Override
                    public void onDisconnected() {}
                    
                    @Override
                    public void onMessageError(Exception exception) {}
                });
                System.out.println("FAIL - Listener replacement after start was accepted");
                testsFailed++;
                return;
            } catch (IllegalStateException e) {
                // Expected
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(null, null);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void testClientToServerMessage() {
        System.out.print("Test 4: Client-to-server message... ");
        
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
            final AtomicReference<String> receivedMessage = new AtomicReference<>();
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    receivedMessage.set(message);
                    latch.countDown();
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            boolean started = clientHandler.start();
            if (!started) {
                System.out.println("FAIL - Client handler failed to start");
                testsFailed++;
                return;
            }
            
            started = serverHandler.start();
            if (!started) {
                System.out.println("FAIL - Server handler failed to start");
                testsFailed++;
                return;
            }
            
            String testMessage = "Alice: Hello Bob";
            boolean sent = clientHandler.sendMessage(testMessage);
            if (!sent) {
                System.out.println("FAIL - Failed to send message");
                testsFailed++;
                return;
            }
            
            boolean received = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Message not received");
                testsFailed++;
                return;
            }
            
            if (!testMessage.equals(receivedMessage.get())) {
                System.out.println("FAIL - Message mismatch: expected '" + testMessage + "', got '" + receivedMessage.get() + "'");
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
    
    private static void testServerToClientMessage() {
        System.out.print("Test 5: Server-to-client message... ");
        
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
            final AtomicReference<String> receivedMessage = new AtomicReference<>();
            
            clientHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    receivedMessage.set(message);
                    latch.countDown();
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            clientHandler.start();
            serverHandler.start();
            
            String testMessage = "Bob: Hi Alice";
            boolean sent = serverHandler.sendMessage(testMessage);
            if (!sent) {
                System.out.println("FAIL - Failed to send message");
                testsFailed++;
                return;
            }
            
            boolean received = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Message not received");
                testsFailed++;
                return;
            }
            
            if (!testMessage.equals(receivedMessage.get())) {
                System.out.println("FAIL - Message mismatch: expected '" + testMessage + "', got '" + receivedMessage.get() + "'");
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
    
    private static void testInvalidMessageRejection() {
        System.out.print("Test 6: Invalid message rejection... ");
        
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
            
            final AtomicInteger messageCount = new AtomicInteger(0);
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    messageCount.incrementAndGet();
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            clientHandler.start();
            serverHandler.start();
            
            // Test invalid messages
            String[] invalidMessages = {null, "", "   ", "line1\nline2", "line1\rline2"};
            
            for (String msg : invalidMessages) {
                boolean sent = clientHandler.sendMessage(msg);
                if (sent) {
                    System.out.println("FAIL - Invalid message was accepted: " + msg);
                    testsFailed++;
                    return;
                }
            }
            
            // Wait a bit to ensure no messages arrived
            Thread.sleep(500);
            
            if (messageCount.get() > 0) {
                System.out.println("FAIL - Invalid message triggered receive callback");
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
    
    private static void testRemoteDisconnect() {
        System.out.print("Test 7: Remote disconnect... ");
        
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
            
            final CountDownLatch clientDisconnectLatch = new CountDownLatch(1);
            final AtomicInteger clientDisconnectCount = new AtomicInteger(0);
            final AtomicInteger serverDisconnectCount = new AtomicInteger(0);
            final AtomicInteger clientErrorCount = new AtomicInteger(0);
            final AtomicInteger serverErrorCount = new AtomicInteger(0);
            
            clientHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {}
                
                @Override
                public void onDisconnected() {
                    clientDisconnectCount.incrementAndGet();
                    clientDisconnectLatch.countDown();
                }
                
                @Override
                public void onMessageError(Exception exception) {
                    clientErrorCount.incrementAndGet();
                }
            });
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {}
                
                @Override
                public void onDisconnected() {
                    serverDisconnectCount.incrementAndGet();
                }
                
                @Override
                public void onMessageError(Exception exception) {
                    serverErrorCount.incrementAndGet();
                }
            });
            
            clientHandler.start();
            serverHandler.start();
            
            // Stop server handler to simulate remote disconnect
            serverHandler.stop();
            
            boolean received = clientDisconnectLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Disconnection callback not received");
                testsFailed++;
                return;
            }
            
            if (clientDisconnectCount.get() != 1) {
                System.out.println("FAIL - Client disconnect callback called " + clientDisconnectCount.get() + " times");
                testsFailed++;
                return;
            }
            
            // The handler that called stop() should receive zero disconnection callbacks
            if (serverDisconnectCount.get() != 0) {
                System.out.println("FAIL - Server (which called stop()) received disconnect callback");
                testsFailed++;
                return;
            }
            
            // The handler that called stop() should receive zero error callbacks
            if (serverErrorCount.get() != 0) {
                System.out.println("FAIL - Server (which called stop()) received error callback");
                testsFailed++;
                return;
            }
            
            // Client should receive exactly one onDisconnected callback
            if (clientDisconnectCount.get() != 1) {
                System.out.println("FAIL - Client disconnect callback count: " + clientDisconnectCount.get());
                testsFailed++;
                return;
            }
            
            // Wait a bit more to ensure no duplicate callbacks
            Thread.sleep(500);
            
            if (clientDisconnectCount.get() != 1) {
                System.out.println("FAIL - Duplicate disconnect callback");
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
    
    private static void testRepeatedStop() {
        System.out.print("Test 8: Repeated stop... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        MessageHandler clientHandler = null;
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            clientHandler = new MessageHandler(clientSocket);
            
            final AtomicInteger errorCount = new AtomicInteger(0);
            final AtomicInteger disconnectCount = new AtomicInteger(0);
            
            clientHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {}
                
                @Override
                public void onDisconnected() {
                    disconnectCount.incrementAndGet();
                }
                
                @Override
                public void onMessageError(Exception exception) {
                    errorCount.incrementAndGet();
                }
            });
            
            clientHandler.start();
            
            // Call stop multiple times
            clientHandler.stop();
            clientHandler.stop();
            clientHandler.stop();
            
            // Wait a bit
            Thread.sleep(500);
            
            if (errorCount.get() > 0) {
                System.out.println("FAIL - Repeated stop caused errors");
                testsFailed++;
                return;
            }
            
            if (disconnectCount.get() > 0) {
                System.out.println("FAIL - Repeated stop caused disconnect callback");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(clientHandler, null);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void testDuplicateStart() {
        System.out.print("Test 9: Duplicate start... ");
        
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
                public void onMessageReceived(String message) {
                    messageCount.incrementAndGet();
                    latch.countDown();
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            boolean firstStart = clientHandler.start();
            if (!firstStart) {
                System.out.println("FAIL - First start returned false");
                testsFailed++;
                return;
            }
            
            boolean secondStart = clientHandler.start();
            if (!secondStart) {
                System.out.println("FAIL - Second start returned false");
                testsFailed++;
                return;
            }
            
            // Verify only one receiver thread was started
            if (clientHandler.getReceiverThreadStartCountForTest() != 1) {
                System.out.println("FAIL - Multiple receiver threads started: " + clientHandler.getReceiverThreadStartCountForTest());
                testsFailed++;
                return;
            }
            
            // Start server handler and verify message delivery still works
            serverHandler.start();
            
            String testMessage = "Test message after duplicate start";
            boolean sent = clientHandler.sendMessage(testMessage);
            if (!sent) {
                System.out.println("FAIL - Failed to send test message");
                testsFailed++;
                return;
            }
            
            boolean received = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Test message not received");
                testsFailed++;
                return;
            }
            
            if (messageCount.get() != 1) {
                System.out.println("FAIL - Message delivered " + messageCount.get() + " times");
                testsFailed++;
                return;
            }
            
            // Verify stop() completes normally
            clientHandler.stop();
            serverHandler.stop();
            
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
    
    private static void testUTF8Message() {
        System.out.print("Test 10: UTF-8 message... ");
        
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
            final AtomicReference<String> receivedMessage = new AtomicReference<>();
            
            serverHandler.setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    receivedMessage.set(message);
                    latch.countDown();
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            clientHandler.start();
            serverHandler.start();
            
            String testMessage = "Alice: नमस्ते Bob";
            boolean sent = clientHandler.sendMessage(testMessage);
            if (!sent) {
                System.out.println("FAIL - Failed to send UTF-8 message");
                testsFailed++;
                return;
            }
            
            boolean received = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - UTF-8 message not received");
                testsFailed++;
                return;
            }
            
            if (!testMessage.equals(receivedMessage.get())) {
                System.out.println("FAIL - UTF-8 message mismatch");
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
    
    private static void testRapidClose() {
        System.out.print("Test 11: Rapid close... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        final MessageHandler[] clientHandlerHolder = new MessageHandler[1];
        final MessageHandler[] serverHandlerHolder = new MessageHandler[1];
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            clientHandlerHolder[0] = new MessageHandler(clientSocket);
            serverHandlerHolder[0] = new MessageHandler(serverSocketAccepted);
            
            clientHandlerHolder[0].start();
            serverHandlerHolder[0].start();
            
            // Stop both handlers nearly simultaneously
            Thread t1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    clientHandlerHolder[0].stop();
                }
            });
            
            Thread t2 = new Thread(new Runnable() {
                @Override
                public void run() {
                    serverHandlerHolder[0].stop();
                }
            });
            
            t1.start();
            t2.start();
            
            t1.join(TEST_TIMEOUT_MS);
            t2.join(TEST_TIMEOUT_MS);
            
            boolean completed = !t1.isAlive() && !t2.isAlive();
            
            if (!completed) {
                System.out.println("FAIL - Deadlock or timeout during rapid close");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(clientHandlerHolder[0], serverHandlerHolder[0]);
            cleanup(serverSocket, clientSocket, serverSocketAccepted);
        }
    }
    
    private static void testCallbackLockSafety() {
        System.out.print("Test 12: Callback lock safety... ");
        
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        Socket serverSocketAccepted = null;
        final MessageHandler[] clientHandlerHolder = new MessageHandler[1];
        final MessageHandler[] serverHandlerHolder = new MessageHandler[1];
        
        try {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            
            clientSocket = new Socket("localhost", port);
            serverSocketAccepted = serverSocket.accept();
            
            clientHandlerHolder[0] = new MessageHandler(clientSocket);
            serverHandlerHolder[0] = new MessageHandler(serverSocketAccepted);
            
            final CountDownLatch messageLatch = new CountDownLatch(1);
            final AtomicInteger callbackErrors = new AtomicInteger(0);
            
            serverHandlerHolder[0].setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    // In callback, invoke safe state methods
                    try {
                        boolean running = serverHandlerHolder[0].isRunning();
                        Socket s = serverHandlerHolder[0].getSocket();
                        
                        // These should complete without deadlock
                        messageLatch.countDown();
                    } catch (Exception e) {
                        callbackErrors.incrementAndGet();
                    }
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            // Test stop() from within a callback on the same handler
            final CountDownLatch stopLatch = new CountDownLatch(1);
            final AtomicInteger stopErrors = new AtomicInteger(0);
            
            clientHandlerHolder[0].setMessageListener(new MessageHandler.MessageListener() {
                @Override
                public void onMessageReceived(String message) {
                    try {
                        // Invoke stop() from within callback
                        clientHandlerHolder[0].stop();
                        stopLatch.countDown();
                    } catch (Exception e) {
                        stopErrors.incrementAndGet();
                    }
                }
                
                @Override
                public void onDisconnected() {}
                
                @Override
                public void onMessageError(Exception exception) {}
            });
            
            clientHandlerHolder[0].start();
            serverHandlerHolder[0].start();
            
            // Send a message to trigger the callback
            clientHandlerHolder[0].sendMessage("Test message");
            
            boolean received = messageLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!received) {
                System.out.println("FAIL - Message callback not received");
                testsFailed++;
                return;
            }
            
            if (callbackErrors.get() > 0) {
                System.out.println("FAIL - Callback methods threw exceptions");
                testsFailed++;
                return;
            }
            
            // Re-send to trigger stop() from callback
            serverHandlerHolder[0].sendMessage("Trigger stop");
            
            boolean stopped = stopLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!stopped) {
                System.out.println("FAIL - Stop from callback did not complete");
                testsFailed++;
                return;
            }
            
            if (stopErrors.get() > 0) {
                System.out.println("FAIL - Stop from callback threw exception");
                testsFailed++;
                return;
            }
            
            System.out.println("PASS");
            testsPassed++;
            
        } catch (Exception e) {
            System.out.println("FAIL - Exception: " + e.getMessage());
            testsFailed++;
        } finally {
            cleanupHandlers(clientHandlerHolder[0], serverHandlerHolder[0]);
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
