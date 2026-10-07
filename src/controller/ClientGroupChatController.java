package controller;

import model.JoinMessage;
import model.Message;
import model.SystemMessage;
import model.SystemMessage.EventType;
import model.UserListMessage;
import network.GroupChatClientSession;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side group-chat Swing controller.
 *
 * <p>Binds a {@link ChatView} to one {@link GroupChatClientSession}. Outgoing
 * chat is sent without any local echo; every visible message comes from a
 * server broadcast. Online-user state starts from the authoritative USER_LIST
 * snapshot (host first) and is updated incrementally by trusted join/leave
 * events observed during this session.
 */
public class ClientGroupChatController implements GroupChatClientSession.GroupChatClientSessionListener {

    /**
     * Lifecycle listener for return-to-start behavior (owned by StartFrame later).
     */
    public interface ClientGroupChatLifecycleListener {
        /**
         * Called exactly once when the client room ends.
         * @param reason Human-readable reason
         * @param initiatedLocally true for a confirmed local Leave Room
         */
        void onClientRoomEnded(String reason, boolean initiatedLocally);
    }

    private static final String JOIN_SUFFIX = " joined the chat.";
    private static final String LEFT_SUFFIX = " left the chat.";

    private final ChatView chatView;
    private final GroupChatClientSession session;
    private final String username;
    private final ClientGroupChatLifecycleListener lifecycleListener;
    private final DisconnectConfirmation leaveConfirmation;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean terminalHandled = new AtomicBoolean(false);
    private final AtomicBoolean lifecycleNotified = new AtomicBoolean(false);
    private final AtomicBoolean joined = new AtomicBoolean(false);
    private final AtomicBoolean roomClosedDisplayed = new AtomicBoolean(false);
    private final AtomicBoolean rejectionDisplayed = new AtomicBoolean(false);
    private final AtomicInteger invalidUserLists = new AtomicInteger(0);
    private static final int MAX_INVALID_USER_LISTS = 3;

    private final Object usersLock = new Object();
    private final Map<String, String> onlineUsersByKey = new LinkedHashMap<>();

    private volatile String hostUsername;

    private ActionListener sendAction;
    private ActionListener leaveAction;
    private DocumentListener characterCountListener;

    /**
     * Primary constructor.
     * @param chatView The view (must not be null)
     * @param hostAddress The server host (must not be null or blank)
     * @param port The server port (1-65535)
     * @param username The local username (JoinMessage validation)
     * @param lifecycleListener The lifecycle listener (must not be null)
     * @param leaveConfirmation The leave confirmation (must not be null)
     * @throws IllegalArgumentException if any validation fails
     */
    public ClientGroupChatController(
            ChatView chatView,
            String hostAddress,
            int port,
            String username,
            ClientGroupChatLifecycleListener lifecycleListener,
            DisconnectConfirmation leaveConfirmation) {
        if (chatView == null) {
            throw new IllegalArgumentException("ChatView cannot be null");
        }
        if (hostAddress == null || hostAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Host address cannot be null or blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        try {
            new JoinMessage(username, LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid username: " + e.getMessage(), e);
        }
        if (lifecycleListener == null) {
            throw new IllegalArgumentException("Lifecycle listener cannot be null");
        }
        if (leaveConfirmation == null) {
            throw new IllegalArgumentException("Leave confirmation cannot be null");
        }

        this.chatView = chatView;
        this.username = username.trim();
        this.lifecycleListener = lifecycleListener;
        this.leaveConfirmation = leaveConfirmation;
        this.hostUsername = null;

        this.session = new GroupChatClientSession(
                hostAddress.trim(), port, this.username);
        this.session.setListener(this);

        initializeController();
    }

    /**
     * Production convenience constructor using Swing confirmation.
     */
    public ClientGroupChatController(
            ChatView chatView,
            String hostAddress,
            int port,
            String username,
            ClientGroupChatLifecycleListener lifecycleListener) {
        this(chatView, hostAddress, port, username, lifecycleListener,
                new SwingDisconnectConfirmation());
    }

    private void initializeController() {
        chatView.setLeaveButtonText("Leave Room");
        chatView.setRoomStatus("Connecting...");
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

        leaveAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLeave();
            }
        };
        chatView.getDisconnectButton().addActionListener(leaveAction);

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

        session.connect();
    }

    // ------------------------------------------------------------------
    // Session callbacks
    // ------------------------------------------------------------------

    @Override
    public void onConnecting(String hostAddress, int port) {
        if (closed.get()) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Connecting...");
                chatView.setRoomControlsEnabled(false);
            }
        });
    }

    @Override
    public void onJoined(String joinedUsername) {
        if (closed.get()) {
            return;
        }
        // Only our own confirmation enables the room (session guarantees once).
        joined.set(true);
        addOnlineUser(username);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Connected");
                chatView.setRoomControlsEnabled(true);
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
                chatView.appendGroupMessage(message, username);
            }
        });
    }

    @Override
    public void onSystemMessage(final SystemMessage message) {
        if (closed.get() || message == null) {
            return;
        }
        if (message.getEventType() == EventType.ROOM_CLOSED) {
            displayRoomClosedOnce(message.getText());
            return;
        }
        if (message.getEventType() == EventType.USERNAME_REJECTED) {
            displayRejectionOnce(message.getText());
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.appendTrustedSystemMessage(message);
            }
        });
        if (message.getEventType() == EventType.USER_JOINED) {
            String newcomer = extractJoinedUsername(message.getText());
            if (newcomer != null) {
                addOnlineUser(newcomer);
            }
        } else if (message.getEventType() == EventType.USER_LEFT) {
            String departed = extractLeftUsername(message.getText());
            if (departed != null) {
                removeOnlineUser(departed);
            }
        }
    }

    @Override
    public void onConnectionError(Exception exception) {
        if (closed.get()) {
            return;
        }
        if (!terminalHandled.compareAndSet(false, true)) {
            return;
        }
        joined.set(false);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Connection error");
                chatView.setRoomControlsEnabled(false);
                chatView.appendSystemMessage("Unable to connect to the chat room.");
            }
        });
        notifyLifecycle("Unable to connect to the chat room.", false);
    }

    @Override
    public void onUsernameRejected(String reason) {
        if (closed.get()) {
            return;
        }
        if (!terminalHandled.compareAndSet(false, true)) {
            return;
        }
        joined.set(false);
        final String text = (reason == null || reason.trim().isEmpty())
                ? "Username is already in use." : reason;
        displayRejectionOnce(text);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Username rejected");
                chatView.setRoomControlsEnabled(false);
            }
        });
        notifyLifecycle("Username is already in use.", false);
    }

    @Override
    public void onRoomClosed(String reason) {
        if (closed.get()) {
            return;
        }
        if (!terminalHandled.compareAndSet(false, true)) {
            return;
        }
        joined.set(false);
        final String text = (reason == null || reason.trim().isEmpty())
                ? "The Host closed the chat room." : reason;
        displayRoomClosedOnce(text);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Room closed");
                chatView.setRoomControlsEnabled(false);
            }
        });
        notifyLifecycle("The Host closed the chat room.", false);
    }

    @Override
    public void onDisconnectedUnexpectedly() {
        if (closed.get()) {
            return;
        }
        if (!terminalHandled.compareAndSet(false, true)) {
            return;
        }
        joined.set(false);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Disconnected");
                chatView.setRoomControlsEnabled(false);
                chatView.appendSystemMessage("Disconnected from the chat room unexpectedly.");
            }
        });
        notifyLifecycle("Disconnected from the chat room unexpectedly.", false);
    }

    /**
     * Apply an authoritative membership snapshot. The server guarantees the
     * host is first; that entry determines the Host label and never changes
     * afterwards. Snapshots replace partial event-built state atomically;
     * later USER_JOINED/USER_LEFT events apply incrementally on top.
     * Callback order matches network-line order (single receiver thread) and
     * user state mutates synchronously here — only the immutable snapshot
     * copy crosses to the EDT for rendering.
     */
    @Override
    public void onUserList(UserListMessage message) {
        if (closed.get() || terminalHandled.get()) {
            return;
        }
        if (message == null) {
            recordInvalidUserList();
            return;
        }
        List<String> names;
        try {
            names = new ArrayList<>(message.getUsernames());
        } catch (Exception e) {
            recordInvalidUserList();
            return;
        }
        if (names.isEmpty()) {
            recordInvalidUserList();
            return;
        }
        // Local user must be present; never invent it.
        boolean localPresent = false;
        for (String name : names) {
            if (username.equals(name)) {
                localPresent = true;
                break;
            }
        }
        if (!localPresent) {
            recordInvalidUserList();
            return;
        }
        String snapshotHost = names.get(0);
        boolean hostChanged;
        synchronized (usersLock) {
            if (hostUsername != null && !hostUsername.equals(snapshotHost)) {
                // Defensive: host identity must not change mid-room.
                hostChanged = true;
            } else {
                hostChanged = false;
                onlineUsersByKey.clear();
                for (String name : names) {
                    String key = name.toLowerCase(Locale.ROOT);
                    if (!onlineUsersByKey.containsKey(key)) {
                        onlineUsersByKey.put(key, name);
                    }
                }
                if (hostUsername == null) {
                    hostUsername = snapshotHost;
                }
            }
        }
        if (hostChanged) {
            recordInvalidUserList();
            return;
        }
        invalidUserLists.set(0);
        refreshOnlineUsers();
    }

    private void recordInvalidUserList() {
        // Previous valid snapshot is kept as-is.
        int bad = invalidUserLists.incrementAndGet();
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.appendSystemMessage("Received an invalid online-user list.");
            }
        });
        if (bad >= MAX_INVALID_USER_LISTS) {
            if (!terminalHandled.compareAndSet(false, true)) {
                return;
            }
            joined.set(false);
            runOnEdt(new Runnable() {
                @Override
                public void run() {
                    if (closed.get()) {
                        return;
                    }
                    chatView.setRoomStatus("Connection error");
                    chatView.setRoomControlsEnabled(false);
                }
            });
            try {
                session.disconnect();
            } catch (Exception e) {
                // Ignore shutdown errors.
            }
            notifyLifecycle("Received invalid room state.", false);
        }
    }

    @Override
    public void onSessionStopped() {        if (closed.get()) {
            return;
        }
        if (terminalHandled.get()) {
            return;
        }
        if (!terminalHandled.compareAndSet(false, true)) {
            return;
        }
        joined.set(false);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomControlsEnabled(false);
                chatView.appendSystemMessage("The chat session ended.");
            }
        });
        notifyLifecycle("The chat session ended.", false);
    }

    // ------------------------------------------------------------------
    // Send / leave / character count
    // ------------------------------------------------------------------

    private void handleSend() {
        if (closed.get() || terminalHandled.get() || !joined.get()) {
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
        boolean sent = session.sendChat(trimmed);
        if (sent) {
            // No local echo: the server-broadcast copy renders the message.
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
            if (!session.isJoined() && !session.isTerminal()) {
                // Transitional; keep text, wait for the terminal callback.
                return;
            }
            if (session.isTerminal() || terminalHandled.get()) {
                runOnEdt(new Runnable() {
                    @Override
                    public void run() {
                        if (closed.get()) {
                            return;
                        }
                        chatView.setRoomControlsEnabled(false);
                    }
                });
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

    private void handleLeave() {
        if (closed.get() || terminalHandled.get()) {
            return;
        }
        boolean confirmed;
        try {
            confirmed = leaveConfirmation.confirm(chatView.getParentComponent());
        } catch (Exception e) {
            return;
        }
        if (!confirmed) {
            return;
        }
        if (!terminalHandled.compareAndSet(false, true)) {
            return;
        }
        joined.set(false);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setRoomStatus("Disconnected");
                chatView.setRoomControlsEnabled(false);
                chatView.appendSystemMessage("You left the chat.");
            }
        });
        try {
            session.disconnect();
        } catch (Exception e) {
            // Ignore disconnect errors; lifecycle still ends locally.
        }
        notifyLifecycle("You left the chat.", true);
    }

    private void updateCharacterCount() {
        if (closed.get()) {
            return;
        }
        int length = chatView.getMessageField().getText().length();
        chatView.updateCharacterCount(length);
    }

    // ------------------------------------------------------------------
    // Online users (temporary event-built view)
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
        final String host = hostUsername;
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.setOnlineUsers(snapshot, username, host);
            }
        });
    }

    /**
     * Extract a username from trusted "<name> joined the chat." text.
     * @return The name, or null when the suffix does not match exactly
     */
    private static String extractJoinedUsername(String text) {
        if (text == null || !text.endsWith(JOIN_SUFFIX)) {
            return null;
        }
        String name = text.substring(0, text.length() - JOIN_SUFFIX.length()).trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * Extract a username from trusted "<name> left the chat." text.
     * @return The name, or null when the suffix does not match exactly
     */
    private static String extractLeftUsername(String text) {
        if (text == null || !text.endsWith(LEFT_SUFFIX)) {
            return null;
        }
        String name = text.substring(0, text.length() - LEFT_SUFFIX.length()).trim();
        return name.isEmpty() ? null : name;
    }

    // ------------------------------------------------------------------
    // Terminal display guards and lifecycle
    // ------------------------------------------------------------------

    private void displayRoomClosedOnce(final String text) {
        if (!roomClosedDisplayed.compareAndSet(false, true)) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.appendTrustedSystemMessage(
                        new SystemMessage(EventType.ROOM_CLOSED, text, LocalDateTime.now()));
            }
        });
    }

    private void displayRejectionOnce(final String text) {
        if (!rejectionDisplayed.compareAndSet(false, true)) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                if (closed.get()) {
                    return;
                }
                chatView.appendTrustedSystemMessage(
                        new SystemMessage(EventType.USERNAME_REJECTED, text, LocalDateTime.now()));
            }
        });
    }

    private void notifyLifecycle(String reason, boolean initiatedLocally) {
        if (!lifecycleNotified.compareAndSet(false, true)) {
            return;
        }
        try {
            lifecycleListener.onClientRoomEnded(reason, initiatedLocally);
        } catch (Exception e) {
            // Never let lifecycle listener errors break the controller.
        }
    }

    /**
     * Graceful shutdown for full application exit: sends DISCONNECT when
     * joined (no confirmation dialog) and notifies the lifecycle listener.
     * StartFrame invalidates its session generation before invoking this, so
     * the notification is ignored during shutdown. Idempotent.
     */
    public void leaveRoomForApplicationExit() {
        if (!terminalHandled.compareAndSet(false, true)) {
            try {
                session.disconnect();
            } catch (Exception e) {
                // Ignore shutdown errors.
            }
            return;
        }
        joined.set(false);
        try {
            session.disconnect();
        } catch (Exception e) {
            // Ignore shutdown errors; lifecycle still ends locally.
        }
        notifyLifecycle("You left the chat.", true);
    }

    // ------------------------------------------------------------------
    // Close
    // ------------------------------------------------------------------

    /**
     * Close the controller: idempotent, removes only owned listeners,
     * disconnects the session, never notifies the lifecycle listener.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        terminalHandled.set(true);
        joined.set(false);
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
        if (leaveAction != null) {
            chatView.getDisconnectButton().removeActionListener(leaveAction);
        }
        if (characterCountListener != null) {
            chatView.getMessageField().getDocument().removeDocumentListener(characterCountListener);
        }
        try {
            session.disconnect();
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
     * @return true once the local JOIN was confirmed
     */
    public boolean isJoined() {
        return joined.get();
    }

    private void runOnEdt(Runnable action) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(action);
        }
    }
}
