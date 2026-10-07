package controller;

import model.JoinMessage;
import model.Message;
import model.SystemMessage;
import model.SystemMessage.EventType;
import network.GroupChatServer;
import ui.ChatView;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Host-side group-chat Swing controller.
 *
 * <p>Binds a {@link ChatView} to one owned {@link GroupChatServer}. Host
 * messages are broadcast through the server and rendered only via the
 * {@code onChatMessage} callback, so the Host view shows the same single
 * copy as every Client (no local echo). The Host renders trusted join/leave
 * events from server callbacks directly, since it receives no network
 * records through a socket and never generates a second broadcast.
 *
 * <p>On room closure the final online-user list is retained until navigation
 * disposes the view, letting the Host briefly see the final room state.
 */
public class HostGroupChatController implements GroupChatServer.GroupChatServerListener {

    /**
     * Lifecycle listener for return-to-start behavior (owned by StartFrame later).
     */
    public interface HostGroupChatLifecycleListener {
        /**
         * Called exactly once when the hosted room ends.
         * @param reason Human-readable reason
         * @param initiatedLocally true for a confirmed local Close Room
         */
        void onHostRoomEnded(String reason, boolean initiatedLocally);
    }

    private final ChatView chatView;
    private final GroupChatServer groupChatServer;
    private final String hostUsername;
    private final HostGroupChatLifecycleListener lifecycleListener;
    private final RoomCloseConfirmation closeRoomConfirmation;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean roomStarted = new AtomicBoolean(false);
    private final AtomicBoolean terminalHandled = new AtomicBoolean(false);
    private final AtomicBoolean lifecycleNotified = new AtomicBoolean(false);

    private final Object usersLock = new Object();
    private final Map<String, String> onlineUsersByKey = new LinkedHashMap<>();

    private ActionListener sendAction;
    private ActionListener closeRoomAction;
    private DocumentListener characterCountListener;

    /**
     * Primary constructor.
     * @param chatView The view (must not be null)
     * @param port The room port (0-65535; 0 selects an ephemeral port for tests)
     * @param hostUsername The host username (JoinMessage validation)
     * @param lifecycleListener The lifecycle listener (must not be null)
     * @param closeRoomConfirmation The close confirmation (must not be null)
     * @throws IllegalArgumentException if any validation fails
     */
    public HostGroupChatController(
            ChatView chatView,
            int port,
            String hostUsername,
            HostGroupChatLifecycleListener lifecycleListener,
            RoomCloseConfirmation closeRoomConfirmation) {
        if (chatView == null) {
            throw new IllegalArgumentException("ChatView cannot be null");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        if (hostUsername == null) {
            throw new IllegalArgumentException("Host username cannot be null");
        }
        try {
            new JoinMessage(hostUsername, LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid host username: " + e.getMessage(), e);
        }
        if (lifecycleListener == null) {
            throw new IllegalArgumentException("Lifecycle listener cannot be null");
        }
        if (closeRoomConfirmation == null) {
            throw new IllegalArgumentException("Close-room confirmation cannot be null");
        }

        this.chatView = chatView;
        this.hostUsername = hostUsername.trim();
        this.lifecycleListener = lifecycleListener;
        this.closeRoomConfirmation = closeRoomConfirmation;

        this.groupChatServer = new GroupChatServer(port, this.hostUsername);
        this.groupChatServer.setListener(this);

        initializeController();
    }

    /**
     * Production convenience constructor using Swing confirmation.
     */
    public HostGroupChatController(
            ChatView chatView,
            int port,
            String hostUsername,
            HostGroupChatLifecycleListener lifecycleListener) {
        this(chatView, port, hostUsername, lifecycleListener,
                new SwingRoomCloseConfirmation());
    }

    private void initializeController() {
        chatView.setLeaveButtonText("Close Room");
        chatView.setRoomStatus("Starting room...");
        chatView.setRoomControlsEnabled(false);
        chatView.setMaximumMessageLength(Message.MAX_MESSAGE_LENGTH);

        sendAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleSend();
            }
        };
        chatView.getSendButton().addActionListener(sendAction);
        chatView.getMessageField().addActionListener(sendAction);

        closeRoomAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleCloseRoom();
            }
        };
        chatView.getDisconnectButton().addActionListener(closeRoomAction);

        characterCountListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCharacterCount();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCharacterCount();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCharacterCount();
            }
        };
        chatView.getMessageField().getDocument().addDocumentListener(characterCountListener);

        addOnlineUser(hostUsername);
        groupChatServer.start();
    }

    // ------------------------------------------------------------------
    // Server callbacks
    // ------------------------------------------------------------------

    @Override
    public void onRoomStarted(String ipAddress, int port) {
        if (closed.get()) {
            return;
        }
        roomStarted.set(true);
        final String status = "Waiting for users at " + ipAddress + ":" + port;
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus(status);
                chatView.setRoomControlsEnabled(true);
            }
        });
    }

    @Override
    public void onUserJoined(final String username) {
        if (closed.get() || username == null) {
            return;
        }
        addOnlineUser(username);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.appendTrustedSystemMessage(new SystemMessage(
                        EventType.USER_JOINED, username + " joined the chat.",
                        LocalDateTime.now()));
            }
        });
    }

    @Override
    public void onUserLeft(final String username) {
        if (closed.get() || username == null) {
            return;
        }
        removeOnlineUser(username);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.appendTrustedSystemMessage(new SystemMessage(
                        EventType.USER_LEFT, username + " left the chat.",
                        LocalDateTime.now()));
            }
        });
    }

    @Override
    public void onChatMessage(final Message message) {
        if (closed.get() || message == null) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.appendGroupMessage(message, hostUsername);
            }
        });
    }

    @Override
    public void onRoomError(Exception exception) {
        if (closed.get()) {
            return;
        }
        if (!terminalHandled.compareAndSet(false, true)) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Connection error");
                chatView.setRoomControlsEnabled(false);
                chatView.appendSystemMessage("Unable to continue hosting the chat room.");
            }
        });
        try {
            groupChatServer.stop();
        } catch (Exception e) {
            // Ignore shutdown errors.
        }
        notifyLifecycle("Unable to continue hosting the chat room.", false);
    }

    @Override
    public void onRoomStopped() {
        if (closed.get()) {
            return;
        }
        if (!terminalHandled.compareAndSet(false, true)) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Room closed");
                chatView.setRoomControlsEnabled(false);
                chatView.appendSystemMessage("The chat room stopped.");
            }
        });
        notifyLifecycle("The chat room stopped.", false);
    }

    // ------------------------------------------------------------------
    // Send / close-room / character count
    // ------------------------------------------------------------------

    private void handleSend() {
        if (closed.get() || terminalHandled.get() || !roomStarted.get()) {
            return;
        }
        String raw = chatView.getMessageField().getText();
        if (raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            chatView.getMessageField().setText("");
            return;
        }
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            return;
        }
        if (trimmed.length() > Message.MAX_MESSAGE_LENGTH) {
            runOnEdt(new Runnable() {
                @Override
                public void run() {
                    if (closed.get()) {
                        return;
                    }
                    chatView.appendSystemMessage("Message is too long. Maximum length is "
                            + Message.MAX_MESSAGE_LENGTH + " characters.");
                }
            });
            return;
        }
        Message message;
        try {
            message = new Message(hostUsername, trimmed, LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            return;
        }
        boolean sent = groupChatServer.broadcastHostMessage(message);
        if (sent) {
            // No local echo: display arrives through onChatMessage.
            runOnEdt(new Runnable() {
                @Override
                public void run() {
                    if (closed.get()) {
                        return;
                    }
                    chatView.getMessageField().setText("");
                    chatView.updateCharacterCount(0);
                    chatView.getMessageField().requestFocusInWindow();
                }
            });
        } else {
            if (!groupChatServer.isRunning()) {
                if (!terminalHandled.compareAndSet(false, true)) {
                    return;
                }
                runOnEdt(new Runnable() {
                    @Override
                    public void run() {
                        if (closed.get()) {
                            return;
                        }
                        chatView.setRoomStatus("Connection error");
                        chatView.setRoomControlsEnabled(false);
                        chatView.appendSystemMessage(
                                "Unable to send because the room is unavailable.");
                    }
                });
                notifyLifecycle("Unable to send because the room is unavailable.", false);
                return;
            }
            runOnEdt(new Runnable() {
                @Override
                public void run() {
                    if (closed.get()) {
                        return;
                    }
                    chatView.appendSystemMessage("Unable to send message.");
                }
            });
        }
    }

    private void handleCloseRoom() {
        if (closed.get() || terminalHandled.get()) {
            return;
        }
        boolean confirmed;
        try {
            confirmed = closeRoomConfirmation.confirm(chatView.getParentComponent());
        } catch (Exception e) {
            return;
        }
        if (!confirmed) {
            return;
        }
        if (!terminalHandled.compareAndSet(false, true)) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Room closed");
                chatView.setRoomControlsEnabled(false);
                chatView.appendSystemMessage("You closed the chat room.");
            }
        });
        try {
            groupChatServer.stop();
        } catch (Exception e) {
            // Ignore shutdown errors; lifecycle still ends locally.
        }
        notifyLifecycle("You closed the chat room.", true);
    }

    private void updateCharacterCount() {
        if (closed.get()) {
            return;
        }
        int length = chatView.getMessageField().getText().length();
        chatView.updateCharacterCount(length);
    }

    // ------------------------------------------------------------------
    // Online users (authoritative server callbacks)
    // ------------------------------------------------------------------

    private void addOnlineUser(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String trimmed = name.trim();
        synchronized (usersLock) {
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (!onlineUsersByKey.containsKey(key)) {
                onlineUsersByKey.put(key, trimmed);
            }
        }
        refreshOnlineUsers();
    }

    private void removeOnlineUser(String name) {
        if (name == null) {
            return;
        }
        synchronized (usersLock) {
            onlineUsersByKey.remove(name.trim().toLowerCase(Locale.ROOT));
        }
        refreshOnlineUsers();
    }

    private void refreshOnlineUsers() {
        final Collection<String> snapshot;
        synchronized (usersLock) {
            snapshot = new ArrayList<>(onlineUsersByKey.values());
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setOnlineUsers(snapshot, hostUsername, hostUsername);
            }
        });
    }

    // ------------------------------------------------------------------
    // Lifecycle and close
    // ------------------------------------------------------------------

    private void notifyLifecycle(String reason, boolean initiatedLocally) {
        if (!lifecycleNotified.compareAndSet(false, true)) {
            return;
        }
        try {
            lifecycleListener.onHostRoomEnded(reason, initiatedLocally);
        } catch (Exception e) {
            // Never let lifecycle listener errors break the controller.
        }
    }

    /**
     * Graceful shutdown for full application exit: broadcasts ROOM_CLOSED to
     * Clients (no confirmation dialog) and notifies the lifecycle listener.
     * StartFrame invalidates its session generation before invoking this, so
     * the notification is ignored during shutdown. Idempotent.
     */
    public void closeRoomForApplicationExit() {
        if (!terminalHandled.compareAndSet(false, true)) {
            try {
                groupChatServer.stop();
            } catch (Exception e) {
                // Ignore shutdown errors.
            }
            return;
        }
        try {
            groupChatServer.stop();
        } catch (Exception e) {
            // Ignore shutdown errors; lifecycle still ends locally.
        }
        notifyLifecycle("You closed the chat room.", true);
    }

    /**
     * Close the controller: idempotent, removes only owned listeners, stops
     * the room, never notifies the lifecycle listener. The final online-user
     * list is retained for navigation to display.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        terminalHandled.set(true);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                chatView.setRoomControlsEnabled(false);
            }
        });
        if (sendAction != null) {
            chatView.getSendButton().removeActionListener(sendAction);
            chatView.getMessageField().removeActionListener(sendAction);
        }
        if (closeRoomAction != null) {
            chatView.getDisconnectButton().removeActionListener(closeRoomAction);
        }
        if (characterCountListener != null) {
            chatView.getMessageField().getDocument().removeDocumentListener(characterCountListener);
        }
        try {
            groupChatServer.stop();
        } catch (Exception e) {
            // Ignore shutdown errors.
        }
    }

    /**
     * @return true after close()
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * @return true once the room reported started
     */
    public boolean isRoomStarted() {
        return roomStarted.get();
    }

    /**
     * @return The actual bound room port
     */
    public int getPort() {
        return groupChatServer.getPort();
    }

    /**
     * Get a defensive snapshot of the tracked online users.
     * @return A copy of the display-cased usernames
     */
    public Collection<String> getOnlineUserSnapshot() {
        synchronized (usersLock) {
            return new ArrayList<>(onlineUsersByKey.values());
        }
    }

    /**
     * Get the tracked online users as a list, for tests.
     * @return A defensive copy in display order
     */
    public List<String> getOnlineUserList() {
        synchronized (usersLock) {
            return new ArrayList<>(onlineUsersByKey.values());
        }
    }

    private void runOnEdt(Runnable action) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(action);
        }
    }
}
