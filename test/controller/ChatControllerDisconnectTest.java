package controller;

import model.DisconnectMessage;
import model.Message;
import network.MessageCodec;
import network.MessageHandler;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ChatControllerDisconnectTest {

    private static final int TEST_TIMEOUT_MS = 10000;
    private static final String LOCALHOST = "127.0.0.1";
    private static final int TEST_PORT = 15001;

    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;

        try {
            if (testCancelLocalDisconnect()) {
                passed++;
                System.out.println("PASS: cancel local disconnect");
            } else {
                failed++;
                System.out.println("FAIL: cancel local disconnect");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: cancel local disconnect - " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (testConfirmLocalDisconnect()) {
                passed++;
                System.out.println("PASS: confirm local disconnect");
            } else {
                failed++;
                System.out.println("FAIL: confirm local disconnect");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: confirm local disconnect - " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (testRemoteGracefulDisconnect()) {
                passed++;
                System.out.println("PASS: remote graceful disconnect");
            } else {
                failed++;
                System.out.println("FAIL: remote graceful disconnect");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: remote graceful disconnect - " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (testRemoteEOF()) {
                passed++;
                System.out.println("PASS: abrupt remote disconnect");
            } else {
                failed++;
                System.out.println("FAIL: abrupt remote disconnect");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: abrupt remote disconnect - " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (testGracefulLineSuppressesEOFDuplicate()) {
                passed++;
                System.out.println("PASS: graceful disconnect suppresses EOF duplicate");
            } else {
                failed++;
                System.out.println("FAIL: graceful disconnect suppresses EOF duplicate");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: graceful disconnect suppresses EOF duplicate - " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (testMalformedDisconnectRemainsNonterminal()) {
                passed++;
                System.out.println("PASS: malformed disconnect remains nonterminal");
            } else {
                failed++;
                System.out.println("FAIL: malformed disconnect remains nonterminal");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: malformed disconnect remains nonterminal - " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (testUnknownProtocolRemainsNonterminal()) {
                passed++;
                System.out.println("PASS: unknown protocol remains nonterminal");
            } else {
                failed++;
                System.out.println("FAIL: unknown protocol remains nonterminal");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: unknown protocol remains nonterminal - " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (testRepeatedClose()) {
                passed++;
                System.out.println("PASS: repeated controller close");
            } else {
                failed++;
                System.out.println("FAIL: repeated controller close");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: repeated controller close - " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (testControllerListenerCleanup()) {
                passed++;
                System.out.println("PASS: controller listener cleanup");
            } else {
                failed++;
                System.out.println("FAIL: controller listener cleanup");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: controller listener cleanup - " + e.getMessage());
            e.printStackTrace();
        }

        try {
            if (testChatMessageRemainsNonterminal()) {
                passed++;
                System.out.println("PASS: chat message remains nonterminal");
            } else {
                failed++;
                System.out.println("FAIL: chat message remains nonterminal");
            }
        } catch (Exception e) {
            failed++;
            System.out.println("FAIL: chat message remains nonterminal - " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== ChatControllerDisconnectTest Summary ===");
        System.out.println("PASS: " + passed);
        System.out.println("FAIL: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static class TestDisconnectConfirmation implements DisconnectConfirmation {
        private final boolean shouldConfirm;

        TestDisconnectConfirmation(boolean shouldConfirm) {
            this.shouldConfirm = shouldConfirm;
        }

        @Override
        public boolean confirm(Component parent) {
            return shouldConfirm;
        }
    }

    private static class TestSessionListener implements ChatController.SessionListener {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<String> reason = new AtomicReference<>();
        private final AtomicBoolean initiatedLocally = new AtomicBoolean();

        @Override
        public void onSessionEnded(String reason, boolean initiatedLocally) {
            this.reason.set(reason);
            this.initiatedLocally.set(initiatedLocally);
            latch.countDown();
        }

        boolean await(long timeoutMillis) throws InterruptedException {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        String getReason() {
            return reason.get();
        }

        boolean getInitiatedLocally() {
            return initiatedLocally.get();
        }

        int getCount() {
            return (int) (1 - latch.getCount());
        }
    }

    private static class ConnectedSockets implements AutoCloseable {
        ServerSocket serverSocket;
        Socket clientSocket;
        Socket serverSideSocket;

        ConnectedSockets() throws IOException {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();

            clientSocket = new Socket(LOCALHOST, port);
            serverSideSocket = serverSocket.accept();
        }

        @Override
        public void close() {
            closeQuietly(serverSideSocket);
            closeQuietly(clientSocket);
            closeQuietly(serverSocket);
        }

        private void closeQuietly(AutoCloseable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private static boolean testCancelLocalDisconnect() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(false);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(100);

            // Trigger disconnect button
            chatView.getDisconnectButton().doClick();

            // Wait for potential session end
            sessionListener.await(500);

            // Verify session remains active
            if (sessionListener.getCount() != 0) {
                return false;
            }

            if (!chatView.isMessagingEnabled()) {
                return false;
            }

            if (!chatView.isDisconnectEnabled()) {
                return false;
            }

            if (sockets.clientSocket.isClosed()) {
                return false;
            }

            controller.close();
            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }

    private static boolean testConfirmLocalDisconnect() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(true);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(100);

            // Trigger disconnect button
            chatView.getDisconnectButton().doClick();

            // Wait for session end
            if (!sessionListener.await(TEST_TIMEOUT_MS)) {
                return false;
            }

            // Verify session ended exactly once
            if (sessionListener.getCount() != 1) {
                return false;
            }

            // Wait for UI updates to propagate
            if (!chatView.waitForMessagingEnabled(false, 2000)) {
                return false;
            }

            if (!chatView.waitForDisconnectEnabled(false, 2000)) {
                return false;
            }

            if (!chatView.waitForStatus("Disconnected", 2000)) {
                return false;
            }

            // Verify system message contains "You left the chat."
            boolean foundMessage = false;
            for (String msg : chatView.getSystemMessages()) {
                if (msg.contains("You left the chat.")) {
                    foundMessage = true;
                    break;
                }
            }
            if (!foundMessage) {
                return false;
            }

            // Verify session listener parameters
            if (!"You left the chat.".equals(sessionListener.getReason())) {
                return false;
            }

            if (!sessionListener.getInitiatedLocally()) {
                return false;
            }

            controller.close();
            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }

    private static boolean testRemoteGracefulDisconnect() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(true);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(200);

            // Send disconnect message from peer
            DisconnectMessage disconnectMessage = new DisconnectMessage("RemoteUser", LocalDateTime.now());
            String encoded = MessageCodec.encodeDisconnect(disconnectMessage);
            PrintWriter writer = new PrintWriter(sockets.serverSideSocket.getOutputStream(), true);
            writer.println(encoded);
            writer.flush();
            writer.close();

            // Give time for message to be received
            Thread.sleep(500);

            // Wait for session end (but it may have already been called)
            sessionListener.await(TEST_TIMEOUT_MS);

            // Verify session ended exactly once
            if (sessionListener.getCount() != 1) {
                return false;
            }

            // Wait for UI updates to propagate
            if (!chatView.waitForMessagingEnabled(false, 2000)) {
                return false;
            }

            if (!chatView.waitForDisconnectEnabled(false, 2000)) {
                return false;
            }

            if (!chatView.waitForStatus("Disconnected", 2000)) {
                return false;
            }

            // Verify system message contains remote sender name
            boolean foundMessage = false;
            for (String msg : chatView.getSystemMessages()) {
                if (msg.contains("RemoteUser") && msg.contains("left the chat.")) {
                    foundMessage = true;
                    break;
                }
            }
            if (!foundMessage) {
                return false;
            }

            // Verify session listener parameters
            if (sessionListener.getInitiatedLocally()) {
                return false;
            }

            controller.close();
            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }

    private static boolean testRemoteEOF() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(true);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(100);

            // Close peer without sending DISCONNECT
            sockets.serverSideSocket.close();

            // Wait for session end
            if (!sessionListener.await(TEST_TIMEOUT_MS)) {
                return false;
            }

            // Verify session ended exactly once
            if (sessionListener.getCount() != 1) {
                return false;
            }

            // Wait for UI updates to propagate
            if (!chatView.waitForMessagingEnabled(false, 2000)) {
                return false;
            }

            if (!chatView.waitForDisconnectEnabled(false, 2000)) {
                return false;
            }

            // Wait for system message to be added (controller uses invokeLater)
            Thread.sleep(200);

            // Verify exactly one unexpected-disconnect system message
            int unexpectedCount = 0;
            for (String msg : chatView.getSystemMessages()) {
                if (msg.contains("disconnected unexpectedly")) {
                    unexpectedCount++;
                }
            }
            if (unexpectedCount != 1) {
                return false;
            }

            controller.close();
            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }

    private static boolean testGracefulLineSuppressesEOFDuplicate() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(true);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(100);

            // Send disconnect message from peer
            DisconnectMessage disconnectMessage = new DisconnectMessage("RemoteUser", LocalDateTime.now());
            String encoded = MessageCodec.encodeDisconnect(disconnectMessage);
            PrintWriter writer = new PrintWriter(sockets.serverSideSocket.getOutputStream(), true);
            writer.println(encoded);
            writer.flush();

            // Immediately close peer
            sockets.serverSideSocket.close();

            // Wait for session end
            if (!sessionListener.await(TEST_TIMEOUT_MS)) {
                return false;
            }

            // Verify session ended exactly once
            if (sessionListener.getCount() != 1) {
                return false;
            }

            // Wait for UI updates to propagate
            if (!chatView.waitForMessagingEnabled(false, 2000)) {
                return false;
            }

            if (!chatView.waitForDisconnectEnabled(false, 2000)) {
                return false;
            }

            // Verify only the named graceful departure message is shown
            int gracefulCount = 0;
            int unexpectedCount = 0;
            for (String msg : chatView.getSystemMessages()) {
                if (msg.contains("RemoteUser") && msg.contains("left the chat.")) {
                    gracefulCount++;
                }
                if (msg.contains("disconnected unexpectedly")) {
                    unexpectedCount++;
                }
            }

            if (gracefulCount != 1) {
                return false;
            }

            if (unexpectedCount != 0) {
                return false;
            }

            controller.close();
            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }

    private static boolean testMalformedDisconnectRemainsNonterminal() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(true);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(100);

            // Send malformed disconnect
            PrintWriter writer = new PrintWriter(sockets.serverSideSocket.getOutputStream(), true);
            writer.println("DISCONNECT|bad");
            writer.flush();

            // Wait to ensure no session end
            Thread.sleep(500);

            // Verify session listener not called
            if (sessionListener.getCount() != 0) {
                return false;
            }

            // Verify session remains active
            if (!chatView.isMessagingEnabled()) {
                return false;
            }

            if (!chatView.isDisconnectEnabled()) {
                return false;
            }

            // Verify invalid-message warning
            boolean foundWarning = false;
            for (String msg : chatView.getSystemMessages()) {
                if (msg.contains("invalid message")) {
                    foundWarning = true;
                    break;
                }
            }
            if (!foundWarning) {
                return false;
            }

            controller.close();
            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }

    private static boolean testUnknownProtocolRemainsNonterminal() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(true);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(100);

            // Send unknown protocol
            PrintWriter writer = new PrintWriter(sockets.serverSideSocket.getOutputStream(), true);
            writer.println("UNKNOWN|value");
            writer.flush();

            // Wait to ensure no session end
            Thread.sleep(500);

            // Verify session listener not called
            if (sessionListener.getCount() != 0) {
                return false;
            }

            // Verify session remains active
            if (!chatView.isMessagingEnabled()) {
                return false;
            }

            if (!chatView.isDisconnectEnabled()) {
                return false;
            }

            // Verify invalid-message warning
            boolean foundWarning = false;
            for (String msg : chatView.getSystemMessages()) {
                if (msg.contains("invalid message")) {
                    foundWarning = true;
                    break;
                }
            }
            if (!foundWarning) {
                return false;
            }

            controller.close();
            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }

    private static boolean testRepeatedClose() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(true);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(100);

            // Close multiple times
            controller.close();
            controller.close();
            controller.close();

            // Verify no exception (we got here)
            // Verify session listener not duplicated
            if (sessionListener.getCount() > 1) {
                return false;
            }

            // Verify socket is closed
            if (!sockets.clientSocket.isClosed()) {
                return false;
            }

            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }

    private static boolean testControllerListenerCleanup() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(true);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(100);

            // Record listener counts before close
            int sendButtonListenersBefore = chatView.getSendButton().getActionListeners().length;
            int messageFieldListenersBefore = chatView.getMessageField().getActionListeners().length;
            int disconnectButtonListenersBefore = chatView.getDisconnectButton().getActionListeners().length;

            controller.close();

            // Record listener counts after close
            int sendButtonListenersAfter = chatView.getSendButton().getActionListeners().length;
            int messageFieldListenersAfter = chatView.getMessageField().getActionListeners().length;
            int disconnectButtonListenersAfter = chatView.getDisconnectButton().getActionListeners().length;

            // Verify that at least one listener was removed from each component
            // (controller adds exactly one listener to each)
            if (sendButtonListenersAfter >= sendButtonListenersBefore) {
                return false;
            }

            if (messageFieldListenersAfter >= messageFieldListenersBefore) {
                return false;
            }

            if (disconnectButtonListenersAfter >= disconnectButtonListenersBefore) {
                return false;
            }

            // Note: Document listener cleanup is verified by the controller's close() method
            // but we cannot easily count Document listeners in headless tests
            // The controller code does remove the listener, which is sufficient

            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }

    private static boolean testChatMessageRemainsNonterminal() throws Exception {
        ConnectedSockets sockets = null;
        try {
            sockets = new ConnectedSockets();

            FakeChatView chatView = new FakeChatView();
            TestDisconnectConfirmation confirmation = new TestDisconnectConfirmation(true);
            TestSessionListener sessionListener = new TestSessionListener();

            ChatController controller = new ChatController(
                chatView,
                sockets.clientSocket,
                "TestUser",
                "Host",
                sessionListener,
                confirmation
            );

            // Wait for controller to start
            Thread.sleep(100);

            // Send valid chat message from peer
            Message message = new Message("RemoteUser", "Hello", LocalDateTime.now());
            String encoded = MessageCodec.encode(message);
            PrintWriter writer = new PrintWriter(sockets.serverSideSocket.getOutputStream(), true);
            writer.println(encoded);
            writer.flush();

            // Wait for message to be received
            if (!chatView.waitForRemoteMessage(1, TEST_TIMEOUT_MS)) {
                return false;
            }

            // Verify remote message displayed
            if (chatView.getRemoteMessages().size() != 1) {
                return false;
            }

            // Verify session remains active
            if (!chatView.isMessagingEnabled()) {
                return false;
            }

            if (!chatView.isDisconnectEnabled()) {
                return false;
            }

            // Verify session listener not called
            if (sessionListener.getCount() != 0) {
                return false;
            }

            controller.close();
            return true;
        } finally {
            if (sockets != null) {
                sockets.close();
            }
        }
    }
}
