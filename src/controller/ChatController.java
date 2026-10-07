package controller;

import model.DisconnectMessage;
import model.Message;
import network.MessageCodec;
import network.MessageFormatException;
import network.MessageHandler;
import network.MessageCodec.ProtocolType;
import ui.ChatView;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChatController class to manage the chat application logic.
 * This class handles communication between ChatFrame, MessageHandler, and networking.
 */
public class ChatController {
    
    /**
     * Session listener interface for notifying when a chat session ends.
     */
    public interface SessionListener {
        /**
         * Called when the chat session has ended.
         * @param reason The reason for the session ending
         * @param initiatedLocally true if the disconnect was initiated locally
         */
        void onSessionEnded(String reason, boolean initiatedLocally);
    }
    
    private ChatView chatView;
    private MessageHandler messageHandler;
    private String userName;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean terminalEventHandled = new AtomicBoolean(false);
    private final AtomicBoolean sessionEndNotified = new AtomicBoolean(false);
    private ActionListener sendAction;
    private ActionListener disconnectAction;
    private DocumentListener characterCountListener;
    private final SessionListener sessionListener;
    private final DisconnectConfirmation disconnectConfirmation;
    private static final int MALFORMED_MESSAGE_WARNING_LIMIT = 3;
    private final AtomicInteger malformedMessageCount = new AtomicInteger(0);
    
    /**
     * Primary constructor for ChatController.
     * @param chatView The chat view to control
     * @param socket The connected socket
     * @param userName The local user's name
     * @param role The user's role ("Host" or "Client")
     * @param sessionListener The listener for session end events
     * @param disconnectConfirmation The confirmation dialog for disconnect
     */
    public ChatController(
            ChatView chatView,
            Socket socket,
            String userName,
            String role,
            SessionListener sessionListener,
            DisconnectConfirmation disconnectConfirmation) {
        if (chatView == null) {
            throw new IllegalArgumentException("ChatView cannot be null");
        }
        if (socket == null) {
            throw new IllegalArgumentException("Socket cannot be null");
        }
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("User name cannot be null or empty");
        }
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role cannot be null or empty");
        }
        if (sessionListener == null) {
            throw new IllegalArgumentException("SessionListener cannot be null");
        }
        if (disconnectConfirmation == null) {
            throw new IllegalArgumentException("DisconnectConfirmation cannot be null");
        }

        this.chatView = chatView;
        this.userName = userName.trim();
        this.sessionListener = sessionListener;
        this.disconnectConfirmation = disconnectConfirmation;

        initializeController(socket);
    }

    /**
     * Convenience constructor for ChatController with Swing disconnect confirmation.
     * @param chatView The chat view to control
     * @param socket The connected socket
     * @param userName The local user's name
     * @param role The user's role ("Host" or "Client")
     * @param sessionListener The listener for session end events
     */
    public ChatController(
            ChatView chatView,
            Socket socket,
            String userName,
            String role,
            SessionListener sessionListener) {
        this(chatView, socket, userName, role, sessionListener, new SwingDisconnectConfirmation());
    }

    /**
     * Initialize the controller with the given socket.
     * @param socket The connected socket
     */
    private void initializeController(Socket socket) {
        
        // Create message handler
        messageHandler = new MessageHandler(socket);
        messageHandler.setMessageListener(new MessageHandler.MessageListener() {
            @Override
            public void onMessageReceived(String line) {
                handleReceivedLine(line);
            }

            @Override
            public void onDisconnected() {
                handleDisconnection();
            }

            @Override
            public void onMessageError(Exception exception) {
                handleMessageError(exception);
            }
        });

        // Register UI actions
        registerActions();

        // Start message handler
        boolean started = messageHandler.start();

        // Enable messaging only if startup succeeded
        if (started) {
            enableMessaging();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    chatView.appendSystemMessage("Unable to initialize the chat connection.");
                }
            });
        }
    }
    
    /**
     * Register action listeners for UI components.
     */
    private void registerActions() {
        sendAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleSend();
            }
        };

        chatView.getSendButton().addActionListener(sendAction);
        chatView.getMessageField().addActionListener(sendAction);

        disconnectAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleDisconnect();
            }
        };

        chatView.getDisconnectButton().addActionListener(disconnectAction);

        // Add document listener to update character count
        characterCountListener = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateCharacterCount();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateCharacterCount();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateCharacterCount();
            }
        };
        chatView.getMessageField().getDocument().addDocumentListener(characterCountListener);
    }
    
    /**
     * Update the character count label.
     */
    private void updateCharacterCount() {
        int length = chatView.getMessageField().getText().length();
        chatView.updateCharacterCount(length);
    }
    
    /**
     * Enable messaging controls.
     */
    private void enableMessaging() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                chatView.setMessagingEnabled(true);
                chatView.setDisconnectEnabled(true);
                chatView.setMaximumMessageLength(Message.MAX_MESSAGE_LENGTH);
                updateCharacterCount();
            }
        });
    }
    
    /**
     * Disable messaging controls.
     */
    private void disableMessaging() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                chatView.setMessagingEnabled(false);
                chatView.setDisconnectEnabled(false);
            }
        });
    }
    
    /**
     * Handle sending a message.
     * Note: sendMessage() performs socket output synchronously on the Event Dispatch Thread.
     * This is acceptable only for small one-line LAN messages. For larger payloads or
     * production use, sending should be moved to a background thread.
     */
    private void handleSend() {
        if (closed.get()) {
            return;
        }

        String text = chatView.getMessageField().getText();
        if (text == null) {
            return;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            // Clear the field if it only contains whitespace
            chatView.getMessageField().setText("");
            return;
        }

        // Validate maximum length
        if (trimmed.length() > Message.MAX_MESSAGE_LENGTH) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    chatView.appendSystemMessage("Message is too long. Maximum length is " + Message.MAX_MESSAGE_LENGTH + " characters.");
                }
            });
            return;
        }

        // Create Message object
        Message message;
        try {
            message = new Message(userName, trimmed, LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    chatView.appendSystemMessage("Invalid message: " + e.getMessage());
                }
            });
            return;
        }

        // Encode message using MessageCodec
        String encodedLine;
        try {
            encodedLine = MessageCodec.encode(message);
        } catch (IllegalArgumentException e) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    chatView.appendSystemMessage("Unable to encode message.");
                }
            });
            return;
        }

        // Send the encoded line through message handler
        boolean success = messageHandler.sendMessage(encodedLine);

        if (success) {
            // Append locally as "You"
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    chatView.appendLocalMessage(message);
                    chatView.getMessageField().setText("");
                    chatView.getMessageField().requestFocusInWindow();
                    updateCharacterCount();
                }
            });
        } else {
            // Send failed - check if handler is still running
            if (!messageHandler.isRunning()) {
                // Connection is no longer available - claim terminal handling
                if (!terminalEventHandled.compareAndSet(false, true)) {
                    // Already handled - preserve unsent field contents
                    return;
                }

                disableMessaging();

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        chatView.setConnectionStatus("Connection error");
                        chatView.appendSystemMessage("Unable to send message because the connection is unavailable.");
                    }
                });
            } else {
                // Handler still running - transient failure, preserve input
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        chatView.appendSystemMessage("Unable to send message.");
                    }
                });
            }
        }
    }
    
    /**
     * Handle the Disconnect button click.
     */
    private void handleDisconnect() {
        requestLocalDisconnect();
    }

    /**
     * Request a local disconnect with confirmation.
     */
    private void requestLocalDisconnect() {
        if (closed.get() || terminalEventHandled.get()) {
            return;
        }

        // Show confirmation dialog
        boolean confirmed = disconnectConfirmation.confirm(chatView.getParentComponent());

        if (!confirmed) {
            // User chose No - do nothing
            return;
        }

        // User chose Yes - proceed with disconnect
        performLocalDisconnect();
    }
    
    /**
     * Perform a local disconnect and send the disconnect message to the peer.
     * This method must only be called after confirmation.
     */
    private void performLocalDisconnect() {
        // Atomically claim terminal handling
        if (!terminalEventHandled.compareAndSet(false, true)) {
            return; // Already handled
        }

        // Create and encode disconnect message
        DisconnectMessage disconnectMessage = new DisconnectMessage(userName, LocalDateTime.now());
        String encodedLine;
        try {
            encodedLine = MessageCodec.encodeDisconnect(disconnectMessage);
        } catch (IllegalArgumentException e) {
            // Should not happen with valid DisconnectMessage
            encodedLine = null;
        }

        // Attempt to send the disconnect message before any cleanup
        boolean sendSucceeded = false;
        if (encodedLine != null && messageHandler != null) {
            sendSucceeded = messageHandler.sendMessage(encodedLine);
        }

        // Disable controls
        disableMessaging();

        // Update UI
        final String systemMessage = sendSucceeded ? "You left the chat." : "[System] You left the chat.";
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                chatView.setConnectionStatus("Disconnected");
                chatView.appendSystemMessage(systemMessage);
            }
        });

        // Stop message handler
        if (messageHandler != null) {
            messageHandler.stop();
        }

        // Notify session listener
        notifySessionEnded("You left the chat.", true);
    }
    
    /**
     * Handle a remote disconnect message.
     * @param sender The sender who disconnected
     */
    private void handleRemoteDisconnect(String sender) {
        // Atomically claim terminal handling
        if (!terminalEventHandled.compareAndSet(false, true)) {
            return; // Already handled
        }

        // Disable controls
        disableMessaging();

        // Update UI
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                chatView.setConnectionStatus("Disconnected");
                chatView.appendSystemMessage(sender + " left the chat.");
            }
        });

        // Stop message handler
        if (messageHandler != null) {
            messageHandler.stop();
        }

        // Notify session listener
        notifySessionEnded(sender + " left the chat.", false);
    }
    
    /**
     * Notify the session listener that the session has ended.
     * This method is idempotent.
     * @param reason The reason for the session ending
     * @param initiatedLocally true if the disconnect was initiated locally
     */
    private void notifySessionEnded(final String reason, final boolean initiatedLocally) {
        if (!sessionEndNotified.compareAndSet(false, true)) {
            return; // Already notified
        }
        
        if (sessionListener != null) {
            sessionListener.onSessionEnded(reason, initiatedLocally);
        }
    }
    
    /**
     * Handle a received line from the network.
     * @param line The received line
     */
    private void handleReceivedLine(final String line) {
        if (closed.get()) {
            return;
        }
        
        // Detect protocol type
        ProtocolType type = MessageCodec.detectType(line);
        
        if (type == ProtocolType.CHAT) {
            // Decode as chat message
            Message message;
            try {
                message = MessageCodec.decode(line);
            } catch (MessageFormatException e) {
                // Protocol error - not a valid message
                handleMalformedMessage();
                return;
            }

            // Valid message - append as remote message
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    chatView.appendRemoteMessage(message);
                }
            });
        } else if (type == ProtocolType.DISCONNECT) {
            // Decode as disconnect message
            DisconnectMessage disconnectMessage;
            try {
                disconnectMessage = MessageCodec.decodeDisconnect(line);
            } catch (MessageFormatException e) {
                // Malformed disconnect - treat as invalid protocol
                handleMalformedMessage();
                return;
            }

            // Valid disconnect - handle remote disconnect
            handleRemoteDisconnect(disconnectMessage.getSender());
        } else {
            // Unknown protocol - treat as malformed
            handleMalformedMessage();
        }
    }

    /**
     * Handle a malformed message.
     */
    private void handleMalformedMessage() {
        int warningNumber = malformedMessageCount.incrementAndGet();

        if (warningNumber <= MALFORMED_MESSAGE_WARNING_LIMIT) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    chatView.appendSystemMessage("Received an invalid message.");
                }
            });
        }
        // After the limit, silently ignore additional malformed lines
    }
    
    /**
     * Handle disconnection.
     * Uses atomic compare-and-set to ensure only one terminal message is appended.
     */
    private void handleDisconnection() {
        if (closed.get()) {
            return;
        }
        
        // Atomically claim terminal handling
        if (!terminalEventHandled.compareAndSet(false, true)) {
            return; // Already handled
        }
        
        disableMessaging();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                chatView.setConnectionStatus("Disconnected");
                chatView.appendSystemMessage("The other user disconnected unexpectedly.");
            }
        });

        // Notify session listener
        notifySessionEnded("The other user disconnected unexpectedly.", false);
    }

    /**
     * Handle a message error.
     * Uses atomic compare-and-set to ensure only one terminal message is appended.
     * @param exception The exception
     */
    private void handleMessageError(Exception exception) {
        if (closed.get()) {
            return;
        }

        // Atomically claim terminal handling
        if (!terminalEventHandled.compareAndSet(false, true)) {
            return; // Already handled
        }

        disableMessaging();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                chatView.setConnectionStatus("Connection error");
                chatView.appendSystemMessage("Connection error occurred.");
            }
        });

        // Notify session listener
        notifySessionEnded("Connection error.", false);
    }
    
    /**
     * Close the controller and cleanup resources.
     * Safe to call multiple times.
     */
    public void close() {
        // Atomically mark closed
        if (!closed.compareAndSet(false, true)) {
            return; // Already closed
        }
        
        // Mark terminal event handled to prevent duplicate messages
        terminalEventHandled.set(true);
        
        // Disable messaging
        disableMessaging();
        
        // Remove only our action listeners
        if (sendAction != null) {
            chatView.getSendButton().removeActionListener(sendAction);
            chatView.getMessageField().removeActionListener(sendAction);
        }

        if (disconnectAction != null) {
            chatView.getDisconnectButton().removeActionListener(disconnectAction);
        }

        // Remove our document listener
        if (characterCountListener != null) {
            chatView.getMessageField().getDocument().removeDocumentListener(characterCountListener);
        }
        
        // Stop message handler
        if (messageHandler != null) {
            messageHandler.stop();
        }
    }
    
    /**
     * Check if the controller is closed.
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }
}
