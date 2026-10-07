package network;

import model.DisconnectMessage;
import model.JoinMessage;
import model.Message;
import model.SystemMessage;
import model.SystemMessage.EventType;
import model.UserListMessage;
import network.MessageCodec.ProtocolType;

import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GroupChatClientSession is the reusable client-side group-room networking layer.
 *
 * <p>It connects through {@link ChatClient}, sends JOIN after the TCP connection
 * is established, and then relays only server-broadcast records to its listener:
 * outgoing chat is never echoed locally; the sender displays the server-broadcast
 * copy like every other participant. Only trusted server SYSTEM lines are decoded
 * and forwarded; this class exposes no way to send arbitrary SYSTEM records
 * (public sends are limited to {@link #sendChat(String)} and {@link #disconnect()}).
 *
 * <p>Single-use object with an explicit state machine. All listener callbacks
 * occur outside internal locks, and socket/handler shutdown never happens while
 * the state lock is held.
 */
public class GroupChatClientSession {

    /**
     * Listener for client session events. Never invoked while internal locks are held.
     */
    public interface GroupChatClientSessionListener {
        void onConnecting(String hostAddress, int port);
        void onJoined(String username);
        void onChatMessage(Message message);
        void onSystemMessage(SystemMessage message);
        void onUserList(UserListMessage message);
        void onUsernameRejected(String reason);
        void onRoomClosed(String reason);
        void onDisconnectedUnexpectedly();
        void onConnectionError(Exception exception);
        void onSessionStopped();
    }

    private enum State {
        NEW,
        CONNECTING,
        JOINING,
        JOINED,
        DISCONNECTING,
        DISCONNECTED,
        REJECTED,
        ROOM_CLOSED,
        FAILED
    }

    private static final int MAX_SERVER_PROTOCOL_FAILURES = 3;

    private final String hostAddress;
    private final int port;
    private final String username;

    private final Object stateLock = new Object();
    private State state = State.NEW;
    private GroupChatClientSessionListener listener;

    private volatile ChatClient chatClient;
    private volatile MessageHandler handler;
    private volatile Socket socket;

    private final AtomicBoolean joinedNotified = new AtomicBoolean(false);
    private final AtomicBoolean terminalNotified = new AtomicBoolean(false);
    private final AtomicBoolean stoppedNotified = new AtomicBoolean(false);
    private final AtomicInteger serverFailures = new AtomicInteger(0);

    /**
     * Create a session with an explicit port.
     * @param hostAddress The server host (must not be null or blank)
     * @param port The server port (1-65535)
     * @param username The desired username (same rules as JoinMessage)
     * @throws IllegalArgumentException if any validation fails
     */
    public GroupChatClientSession(String hostAddress, int port, String username) {
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
        this.hostAddress = hostAddress.trim();
        this.port = port;
        this.username = username.trim();
    }

    /**
     * Create a session using the default chat port.
     * @param hostAddress The server host (must not be null or blank)
     * @param username The desired username (same rules as JoinMessage)
     * @throws IllegalArgumentException if any validation fails
     */
    public GroupChatClientSession(String hostAddress, String username) {
        this(hostAddress, ChatClient.DEFAULT_PORT, username);
    }

    /**
     * Assign the session listener. Must be non-null and may only be assigned
     * once, before {@link #connect()}.
     * @param listener The listener
     * @throws IllegalArgumentException if listener is null
     * @throws IllegalStateException if a listener is already assigned or the session started
     */
    public void setListener(GroupChatClientSessionListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        synchronized (stateLock) {
            if (this.listener != null) {
                throw new IllegalStateException("Listener replacement is not allowed");
            }
            if (state != State.NEW) {
                throw new IllegalStateException("Listener may only be assigned before connect()");
            }
            this.listener = listener;
        }
    }

    /**
     * Start the session: TCP connect via ChatClient, then JOIN handshake.
     * Valid only from NEW; single-use (duplicate calls return false and
     * create no additional ChatClient or thread).
     * @return true if the connection attempt was started
     * @throws IllegalStateException if no listener was assigned
     */
    public boolean connect() {
        final GroupChatClientSessionListener current;
        synchronized (stateLock) {
            if (state != State.NEW) {
                return false;
            }
            if (listener == null) {
                throw new IllegalStateException("Listener must be set before connect()");
            }
            state = State.CONNECTING;
            current = listener;
        }
        final ChatClient client = new ChatClient(hostAddress, port);
        client.setClientListener(new ChatClient.ClientListener() {
            @Override
            public void onConnecting(String host, int port) {
                safeCallback(new Runnable() {
                    @Override
                    public void run() {
                        current.onConnecting(host, port);
                    }
                });
            }

            @Override
            public void onConnected(Socket connectedSocket) {
                handleConnected(connectedSocket);
            }

            @Override
            public void onConnectionError(Exception exception) {
                handleConnectError(exception);
            }

            @Override
            public void onDisconnected() {
                handleTransportLoss();
            }
        });
        chatClient = client;
        client.connect();
        return true;
    }

    /**
     * Send a chat message. Succeeds only in JOINED with valid content.
     * Never echoes locally; the caller waits for the server-broadcast copy.
     * @param text The message text
     * @return true only if the line was accepted by the transport
     */
    public boolean sendChat(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            return false;
        }
        synchronized (stateLock) {
            if (state != State.JOINED) {
                return false;
            }
        }
        Message message;
        try {
            message = new Message(username, trimmed, LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String line;
        try {
            line = MessageCodec.encode(message);
        } catch (IllegalArgumentException e) {
            return false;
        }
        MessageHandler current = handler;
        if (current == null) {
            return false;
        }
        return current.sendMessage(line);
    }

    /**
     * Graceful, idempotent disconnect. Sends DISCONNECT only when JOINED;
     * cancels quietly from CONNECTING/JOINING without sending DISCONNECT.
     * Never emits unexpected-disconnect or connection-error callbacks.
     */
    public void disconnect() {
        boolean sendGoodbye = false;
        boolean notifyNow = false;
        MessageHandler handlerToStop;
        ChatClient clientToStop;
        synchronized (stateLock) {
            if (isTerminalState(state) || state == State.DISCONNECTING) {
                return;
            }
            if (state == State.JOINED) {
                state = State.DISCONNECTING;
                sendGoodbye = true;
            } else if (state == State.CONNECTING || state == State.JOINING || state == State.NEW) {
                state = State.DISCONNECTED;
                notifyNow = true;
            } else {
                return;
            }
            handlerToStop = handler;
            clientToStop = chatClient;
        }
        if (sendGoodbye && handlerToStop != null) {
            try {
                DisconnectMessage goodbye =
                        new DisconnectMessage(username, LocalDateTime.now());
                handlerToStop.sendMessage(MessageCodec.encodeDisconnect(goodbye));
            } catch (Exception e) {
                // Best effort only
            }
        }
        shutdownTransport(handlerToStop, clientToStop);
        if (sendGoodbye) {
            synchronized (stateLock) {
                if (state == State.DISCONNECTING) {
                    state = State.DISCONNECTED;
                }
            }
        }
        if (sendGoodbye || notifyNow) {
            notifySessionStopped();
        }
    }

    /**
     * @return true while TCP connecting or awaiting join confirmation
     */
    public boolean isConnecting() {
        synchronized (stateLock) {
            return state == State.CONNECTING || state == State.JOINING;
        }
    }

    /**
     * @return true once the server confirmed our JOIN
     */
    public boolean isJoined() {
        synchronized (stateLock) {
            return state == State.JOINED;
        }
    }

    /**
     * @return true once the session reached a terminal outcome
     */
    public boolean isTerminal() {
        synchronized (stateLock) {
            return isTerminalState(state);
        }
    }

    /**
     * @return The validated, trimmed username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The trimmed host address
     */
    public String getHostAddress() {
        return hostAddress;
    }

    /**
     * @return The port
     */
    public int getPort() {
        return port;
    }

    // ------------------------------------------------------------------
    // Internal flow
    // ------------------------------------------------------------------

    private void handleConnected(Socket connectedSocket) {
        synchronized (stateLock) {
            if (state != State.CONNECTING) {
                // Stale callback after local cancellation: drop the socket.
                closeSocketQuietly(connectedSocket);
                return;
            }
        }
        socket = connectedSocket;
        MessageHandler created;
        try {
            created = new MessageHandler(connectedSocket);
        } catch (IllegalArgumentException e) {
            failSession(e);
            return;
        }
        final MessageHandler newHandler = created;
        handler = newHandler;
        newHandler.setMessageListener(new MessageHandler.MessageListener() {
            @Override
            public void onMessageReceived(String line) {
                handleServerLine(line);
            }

            @Override
            public void onDisconnected() {
                handleTransportLoss();
            }

            @Override
            public void onMessageError(Exception exception) {
                // Informational only: malformed counting and EOF drive termination.
            }
        });
        if (!newHandler.start()) {
            failSession(new IOException("MessageHandler failed to start"));
            return;
        }
        synchronized (stateLock) {
            if (state != State.CONNECTING) {
                // Lost a race with local disconnect; tear down quietly.
                closeSocketQuietly(connectedSocket);
                return;
            }
            state = State.JOINING;
        }
        String joinLine;
        try {
            joinLine = MessageCodec.encodeJoin(new JoinMessage(username, LocalDateTime.now()));
        } catch (IllegalArgumentException e) {
            failSession(e);
            return;
        }
        if (!newHandler.sendMessage(joinLine)) {
            failSession(new IOException("JOIN line was not accepted by the transport"));
        }
    }

    private void handleConnectError(Exception exception) {
        if (!claimRemoteTerminal(State.FAILED)) {
            return;
        }
        final Exception cause = (exception != null) ? exception : new IOException("Connection failed");
        if (terminalNotified.compareAndSet(false, true)) {
            final GroupChatClientSessionListener current = currentListener();
            if (current != null) {
                safeCallback(new Runnable() {
                    @Override
                    public void run() {
                        current.onConnectionError(cause);
                    }
                });
            }
        }
        shutdownTransport();
        notifySessionStopped();
    }

    private void failSession(Exception cause) {
        if (!claimRemoteTerminal(State.FAILED)) {
            return;
        }
        final Exception error = (cause != null) ? cause : new IOException("Session failed");
        if (terminalNotified.compareAndSet(false, true)) {
            final GroupChatClientSessionListener current = currentListener();
            if (current != null) {
                safeCallback(new Runnable() {
                    @Override
                    public void run() {
                        current.onConnectionError(error);
                    }
                });
            }
        }
        shutdownTransport();
        notifySessionStopped();
    }

    private void handleTransportLoss() {
        if (!claimRemoteTerminal(State.DISCONNECTED)) {
            return;
        }
        if (terminalNotified.compareAndSet(false, true)) {
            final GroupChatClientSessionListener current = currentListener();
            if (current != null) {
                safeCallback(new Runnable() {
                    @Override
                    public void run() {
                        current.onDisconnectedUnexpectedly();
                    }
                });
            }
        }
        shutdownTransport();
        notifySessionStopped();
    }

    private void handleServerLine(String line) {
        synchronized (stateLock) {
            if (isTerminalState(state) || state == State.DISCONNECTING) {
                return;
            }
        }
        ProtocolType type = MessageCodec.detectType(line);
        if (type == ProtocolType.CHAT) {
            Message message;
            try {
                message = MessageCodec.decode(line);
            } catch (MessageFormatException e) {
                recordServerFailure();
                return;
            }
            boolean deliver;
            synchronized (stateLock) {
                deliver = (state == State.JOINING || state == State.JOINED);
            }
            if (!deliver) {
                return;
            }
            final Message inbound = message;
            final GroupChatClientSessionListener current = currentListener();
            if (current != null) {
                safeCallback(new Runnable() {
                    @Override
                    public void run() {
                        current.onChatMessage(inbound);
                    }
                });
            }
            return;
        }
        if (type == ProtocolType.SYSTEM) {
            SystemMessage system;
            try {
                system = MessageCodec.decodeSystem(line);
            } catch (MessageFormatException e) {
                recordServerFailure();
                return;
            }
            handleSystemMessage(system);
            return;
        }
        if (type == ProtocolType.USER_LIST) {
            UserListMessage userList;
            try {
                userList = MessageCodec.decodeUserList(line);
            } catch (MessageFormatException e) {
                recordServerFailure();
                return;
            }
            // Snapshots never confirm JOIN and never render as chat/system.
            boolean deliver;
            synchronized (stateLock) {
                deliver = (state == State.JOINING || state == State.JOINED);
            }
            if (!deliver) {
                return;
            }
            final UserListMessage snapshot = userList;
            final GroupChatClientSessionListener current = currentListener();
            if (current != null) {
                safeCallback(new Runnable() {
                    @Override
                    public void run() {
                        current.onUserList(snapshot);
                    }
                });
            }
            return;
        }
        // The server must never send JOIN, DISCONNECT or untyped lines.
        boolean countIt;
        synchronized (stateLock) {
            countIt = (state == State.JOINING || state == State.JOINED);
        }
        if (countIt) {
            recordServerFailure();
        }
    }

    private void handleSystemMessage(SystemMessage system) {
        EventType eventType = system.getEventType();
        if (eventType == EventType.USER_JOINED) {
            boolean confirm = false;
            synchronized (stateLock) {
                if (state == State.JOINING && isOwnJoinText(system.getText())) {
                    state = State.JOINED;
                    confirm = true;
                }
            }
            if (confirm && joinedNotified.compareAndSet(false, true)) {
                final GroupChatClientSessionListener current = currentListener();
                if (current != null) {
                    safeCallback(new Runnable() {
                        @Override
                        public void run() {
                            current.onJoined(username);
                        }
                    });
                }
            }
            forwardSystem(system);
            return;
        }
        if (eventType == EventType.USER_LEFT) {
            // Another user leaving never terminates the local session.
            forwardSystem(system);
            return;
        }
        if (eventType == EventType.USERNAME_REJECTED) {
            boolean rejectedHere = false;
            synchronized (stateLock) {
                rejectedHere = (state == State.JOINING);
            }
            if (rejectedHere) {
                if (claimRemoteTerminal(State.REJECTED)) {
                    final String reason = system.getText();
                    if (terminalNotified.compareAndSet(false, true)) {
                        final GroupChatClientSessionListener current = currentListener();
                        if (current != null) {
                            safeCallback(new Runnable() {
                                @Override
                                public void run() {
                                    current.onUsernameRejected(reason);
                                }
                            });
                        }
                    }
                    shutdownTransport();
                    notifySessionStopped();
                }
                return;
            }
            // Logically invalid transition (e.g. after JOINED): forward when safe
            // and count it as a server protocol failure.
            forwardSystem(system);
            recordServerFailure();
            return;
        }
        if (eventType == EventType.ROOM_CLOSED) {
            synchronized (stateLock) {
                if (isTerminalState(state) || state == State.DISCONNECTING) {
                    return;
                }
            }
            if (claimRemoteTerminal(State.ROOM_CLOSED)) {
                final String reason = system.getText();
                if (terminalNotified.compareAndSet(false, true)) {
                    final GroupChatClientSessionListener current = currentListener();
                    if (current != null) {
                        safeCallback(new Runnable() {
                            @Override
                            public void run() {
                                current.onRoomClosed(reason);
                            }
                        });
                    }
                }
                shutdownTransport();
                notifySessionStopped();
            }
            return;
        }
    }

    /**
     * The server preserves the registered display name in join text
     * ("&lt;username&gt; joined the chat."), so confirmation matches
     * case-sensitively against our validated username.
     */
    private boolean isOwnJoinText(String text) {
        return text != null && text.equals(username + " joined the chat.");
    }

    private void forwardSystem(final SystemMessage system) {
        synchronized (stateLock) {
            // Observable only while the session can still observe the room.
            if (isTerminalState(state) || state == State.DISCONNECTING) {
                return;
            }
        }
        final GroupChatClientSessionListener current = currentListener();
        if (current != null) {
            safeCallback(new Runnable() {
                @Override
                public void run() {
                    current.onSystemMessage(system);
                }
            });
        }
    }

    private void recordServerFailure() {
        int failures = serverFailures.incrementAndGet();
        if (failures < MAX_SERVER_PROTOCOL_FAILURES) {
            return;
        }
        if (!claimRemoteTerminal(State.FAILED)) {
            return;
        }
        if (terminalNotified.compareAndSet(false, true)) {
            final GroupChatClientSessionListener current = currentListener();
            if (current != null) {
                safeCallback(new Runnable() {
                    @Override
                    public void run() {
                        current.onConnectionError(new MessageFormatException(
                                "Server protocol violation limit exceeded"));
                    }
                });
            }
        }
        shutdownTransport();
        notifySessionStopped();
    }

    /**
     * Claim a remote-driven terminal state from an active state.
     * Local-disconnect states and already-terminal states always lose.
     */
    private boolean claimRemoteTerminal(State terminal) {
        synchronized (stateLock) {
            if (state == State.CONNECTING || state == State.JOINING || state == State.JOINED) {
                state = terminal;
                return true;
            }
            return false;
        }
    }

    private static boolean isTerminalState(State state) {
        return state == State.DISCONNECTED || state == State.REJECTED
                || state == State.ROOM_CLOSED || state == State.FAILED;
    }

    private GroupChatClientSessionListener currentListener() {
        synchronized (stateLock) {
            return listener;
        }
    }

    private void shutdownTransport() {
        shutdownTransport(handler, chatClient);
    }

    private void shutdownTransport(MessageHandler handlerToStop, ChatClient clientToStop) {
        if (handlerToStop != null) {
            try {
                handlerToStop.stop();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        }
        if (clientToStop != null) {
            try {
                clientToStop.disconnect();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        }
        closeSocketQuietly(socket);
    }

    private static void closeSocketQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }

    private void notifySessionStopped() {
        if (!stoppedNotified.compareAndSet(false, true)) {
            return;
        }
        final GroupChatClientSessionListener current = currentListener();
        if (current != null) {
            safeCallback(new Runnable() {
                @Override
                public void run() {
                    current.onSessionStopped();
                }
            });
        }
    }

    private static void safeCallback(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            // Never let listener exceptions break transport threads
        }
    }
}
