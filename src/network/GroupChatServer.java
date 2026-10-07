package network;

import model.DisconnectMessage;
import model.JoinMessage;
import model.Message;
import model.SystemMessage;
import model.SystemMessage.EventType;
import model.UserListMessage;
import network.MessageCodec.ProtocolType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GroupChatServer coordinates the Step 9B room protocol on top of ChatServer.
 *
 * <p>Responsibilities:
 * - Own a ChatServer for listening, accepting and tracking sockets.
 * - Attach one MessageHandler per accepted ClientConnection.
 * - Enforce the JOIN-first handshake (3-strike limit for pre-join garbage).
 * - Register unique case-insensitive usernames (host name reserved).
 * - Broadcast valid CHAT lines to every joined client (including the sender).
 * - Broadcast join/leave/room-closed SYSTEM events originated only here.
 * - Reject duplicate usernames, sender spoofing and client SYSTEM forgery.
 * - Remove exactly one client per leave/EOF without stopping the room.
 *
 * <p>ChatServer remains responsible only for listening, accepting, tracking
 * connection identities, removing sockets and stopping the listen service.
 */
public class GroupChatServer {

    /**
     * Listener for room-level events. Never invoked while internal locks are held.
     */
    public interface GroupChatServerListener {
        void onRoomStarted(String ipAddress, int port);
        void onUserJoined(String username);
        void onUserLeft(String username);
        void onChatMessage(Message message);
        void onRoomError(Exception exception);
        void onRoomStopped();
    }

    private static final int MAX_HANDSHAKE_FAILURES = 3;
    private static final int MAX_PROTOCOL_VIOLATIONS = 3;

    /**
     * Per-connection handshake/violation counters.
     * Each connection is driven by its own receiver thread, but stop() and
     * broadcasts may touch state concurrently, so all access is synchronized.
     */
    private static final class PeerState {
        int handshakeFailures;
        int violations;
    }

    private final ChatServer chatServer;
    private final String hostUsername;
    private final String hostKey;
    /** True while the host reservation is active (set at start, cleared at stop). */
    private volatile boolean hostReserved;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Object listenerLock = new Object();
    private GroupChatServerListener listener;

    /** Normalized (lower-case ROOT) username -&gt; joined connection. Only joined entries. */
    private final ConcurrentHashMap<String, ClientConnection> usersByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PeerState> peerStates = new ConcurrentHashMap<>();
    /** Connection IDs whose removal funnel was already claimed (exactly-once leave). */
    private final ConcurrentHashMap<Long, Boolean> removalClaimed = new ConcurrentHashMap<>();

    /**
     * Create a room without a reserved host participant.
     * @param port The port to listen on (0 for ephemeral)
     */
    public GroupChatServer(int port) {
        this(port, null);
    }

    /**
     * Create a room owned by a host participant.
     * The host requires no loopback socket but occupies its username
     * case-insensitively and is included in joined snapshots.
     * @param port The port to listen on (0 for ephemeral)
     * @param hostUsername The host username, or null for no host participant
     * @throws IllegalArgumentException if hostUsername is non-null but invalid
     */
    public GroupChatServer(int port, String hostUsername) {
        if (hostUsername != null) {
            if (hostUsername.trim().isEmpty()) {
                throw new IllegalArgumentException("Host username cannot be blank");
            }
            if (hostUsername.contains("\n") || hostUsername.contains("\r")) {
                throw new IllegalArgumentException("Host username cannot contain newline or carriage return");
            }
            if (hostUsername.trim().length() > Message.MAX_SENDER_LENGTH) {
                throw new IllegalArgumentException("Host username exceeds maximum length");
            }
            this.hostUsername = hostUsername.trim();
            this.hostKey = normalize(this.hostUsername);
        } else {
            this.hostUsername = null;
            this.hostKey = null;
        }
        this.chatServer = new ChatServer(port);
    }

    /**
     * Set the room listener.
     * @param listener The listener to set
     */
    public void setListener(GroupChatServerListener listener) {
        synchronized (listenerLock) {
            this.listener = listener;
        }
    }

    private GroupChatServerListener currentListener() {
        synchronized (listenerLock) {
            return listener;
        }
    }

    /**
     * Start the room: starts the underlying ChatServer and begins accepting.
     * Idempotent while running; a stopped room cannot be restarted.
     */
    public void start() {
        if (running.get()) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return; // already started before (stopped rooms cannot restart)
        }
        if (hostUsername != null) {
            hostReserved = true;
        }
        running.set(true);
        chatServer.setServerListener(new ChatServer.ServerListener() {
            @Override
            public void onServerStarted(String ipAddress, int port) {
                GroupChatServerListener l = currentListener();
                if (l != null) {
                    safeCallback(new Runnable() {
                        @Override
                        public void run() {
                            l.onRoomStarted(ipAddress, port);
                        }
                    });
                }
            }

            @Override
            public void onClientConnected(ClientConnection connection) {
                handleAccepted(connection);
            }

            @Override
            public void onClientDisconnected(ClientConnection connection) {
                // ChatServer fires this from removeClient(); funnel claims exactly once.
                removeAndAnnounce(connection);
            }

            @Override
            public void onServerError(Exception exception) {
                GroupChatServerListener l = currentListener();
                if (l != null) {
                    final Exception e = exception;
                    safeCallback(new Runnable() {
                        @Override
                        public void run() {
                            l.onRoomError(e);
                        }
                    });
                }
            }

            @Override
            public void onServerStopped() {
                // Room-level stop notification is driven by GroupChatServer.stop() only.
            }
        });
        chatServer.start();
    }

    /**
     * Stop the room. Broadcasts ROOM_CLOSED, stops all handlers, stops
     * ChatServer, clears indexes and notifies onRoomStopped exactly once.
     * Idempotent; never reports intentional shutdown as onRoomError.
     */
    public void stop() {
        stopInternal(true);
    }

    /**
     * Test-only abrupt transport kill: closes every client transport and stops
     * the listening service WITHOUT broadcasting ROOM_CLOSED, so client
     * sessions observe an unexpected EOF. Package-private by design.
     */
    void abruptStopForTest() {
        stopInternal(false);
    }

    private void stopInternal(boolean graceful) {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        // 1. Best-effort ROOM_CLOSED broadcast to every joined client (skipped
        //    for abrupt test teardown so peers observe an unexpected EOF).
        if (graceful) {
            try {
                SystemMessage closed = new SystemMessage(
                        EventType.ROOM_CLOSED, "The Host closed the chat room.", LocalDateTime.now());
                broadcastLine(MessageCodec.encodeSystem(closed));
            } catch (Exception e) {
                // Best effort only
            }
        }
        // 2. Stop every handler (via connection close; snapshot first).
        List<ClientConnection> snapshot;
        try {
            snapshot = new ArrayList<>(chatServer.getClientConnections());
        } catch (Exception e) {
            snapshot = new ArrayList<>();
        }
        for (ClientConnection c : snapshot) {
            try {
                c.close();
            } catch (Exception e) {
                // Ignore per-connection close errors
            }
        }
        // 3. Stop the listening service.
        try {
            chatServer.stop();
        } catch (Exception e) {
            // Ignore shutdown errors
        }
        // 4. Clear username, connection and host-reservation indexes.
        usersByKey.clear();
        hostReserved = false;
        peerStates.clear();
        removalClaimed.clear();
        // 5. Notify exactly once, outside any lock.
        GroupChatServerListener l = currentListener();
        if (l != null) {
            safeCallback(new Runnable() {
                @Override
                public void run() {
                    l.onRoomStopped();
                }
            });
        }
    }

    /**
     * Check whether the room is running.
     * @return true while started and not stopped
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get the port the room listens on (actual bound port once started).
     * @return The port
     */
    public int getPort() {
        return chatServer.getPort();
    }

    /**
     * Get the number of joined users, including the host participant while
     * the room is active. After {@link #stop()} the room is gone and this
     * returns 0.
     * @return The joined user count
     */
    public int getJoinedUserCount() {
        int count = usersByKey.size();
        if (hostReserved) {
            count += 1;
        }
        return count;
    }

    /**
     * Get a snapshot of joined usernames, including the host while the room
     * is active. After {@link #stop()} the snapshot is empty.
     * @return An unmodifiable snapshot copy
     */
    public Collection<String> getJoinedUsernames() {
        List<String> names = new ArrayList<>();
        if (hostReserved && hostUsername != null) {
            names.add(hostUsername);
        }
        for (ClientConnection c : usersByKey.values()) {
            String u = c.getUsername();
            if (u != null) {
                names.add(u);
            }
        }
        return Collections.unmodifiableList(names);
    }

    /**
     * Broadcast a host message to all joined network clients.
     * The host needs no socket; the message is fanned out like a client CHAT.
     * @param message The host message; sender must equal the host username
     * @return true when broadcast processing succeeded
     */
    public boolean broadcastHostMessage(Message message) {
        if (message == null || hostUsername == null || !hostReserved) {
            return false;
        }
        if (!message.getSender().equals(hostUsername)) {
            return false;
        }
        if (!running.get()) {
            return false;
        }
        String line;
        try {
            line = MessageCodec.encode(message);
        } catch (IllegalArgumentException e) {
            return false;
        }
        broadcastLine(line);
        final GroupChatServerListener l = currentListener();
        if (l != null) {
            final Message m = message;
            safeCallback(new Runnable() {
                @Override
                public void run() {
                    l.onChatMessage(m);
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Accept path
    // ------------------------------------------------------------------

    private void handleAccepted(ClientConnection connection) {
        if (!running.get()) {
            safeRemoveFromChatServer(connection);
            return;
        }
        PeerState state = new PeerState();
        PeerState prev = peerStates.putIfAbsent(connection.getConnectionId(), state);
        final PeerState peer = (prev != null) ? prev : state;

        MessageHandler handler;
        try {
            handler = new MessageHandler(connection.getSocket());
        } catch (IllegalArgumentException e) {
            removeAndAnnounce(connection);
            return;
        }
        try {
            connection.setMessageHandler(handler);
        } catch (IllegalStateException e) {
            removeAndAnnounce(connection);
            return;
        }
        handler.setMessageListener(new MessageHandler.MessageListener() {
            @Override
            public void onMessageReceived(String line) {
                handleLine(connection, peer, line);
            }

            @Override
            public void onDisconnected() {
                removeAndAnnounce(connection);
            }

            @Override
            public void onMessageError(Exception exception) {
                // Informational only: send failures are handled via the
                // sendMessage() boolean in broadcastLine, EOF via onDisconnected.
            }
        });
        if (!handler.start()) {
            removeAndAnnounce(connection);
        }
    }

    // ------------------------------------------------------------------
    // Receive path
    // ------------------------------------------------------------------

    private void handleLine(ClientConnection connection, PeerState peer, String line) {
        if (!running.get() || !connection.isConnected()) {
            return;
        }
        if (!connection.isJoined()) {
            handlePreJoin(connection, peer, line);
        } else {
            handlePostJoin(connection, peer, line);
        }
    }

    private void handlePreJoin(ClientConnection connection, PeerState peer, String line) {
        ProtocolType type = MessageCodec.detectType(line);
        if (type == ProtocolType.JOIN) {
            JoinMessage join;
            try {
                join = MessageCodec.decodeJoin(line);
            } catch (MessageFormatException e) {
                recordHandshakeFailure(connection, peer);
                return;
            }
            String key = normalize(join.getSender());
            if (isReserved(key)) {
                sendRejectedAndRemove(connection, peer);
                return;
            }
            ClientConnection existing = usersByKey.putIfAbsent(key, connection);
            if (existing != null) {
                sendRejectedAndRemove(connection, peer);
                return;
            }
            // Won the username slot; mark joined (first registration always wins here).
            boolean registered = false;
            try {
                registered = connection.registerUsername(join.getSender());
            } catch (IllegalArgumentException e) {
                registered = false;
            }
            if (!registered) {
                usersByKey.remove(key, connection);
                recordHandshakeFailure(connection, peer);
                return;
            }
            synchronized (peer) {
                peer.handshakeFailures = 0;
            }
            peerStates.putIfAbsent(connection.getConnectionId(), peer);
            broadcastJoin(join.getSender());
            notifyUserJoined(join.getSender());
            // Authoritative snapshot goes only to the newly joined client,
            // after the join broadcast so line order is join-then-list.
            // A send failure removes only this client (at most one USER_LEFT).
            if (!sendUserListTo(connection)) {
                removeAndAnnounce(connection);
            }
            return;
        }
        if (type == ProtocolType.DISCONNECT) {
            // Unregistered client going away: close quietly, no broadcast.
            removeAndAnnounce(connection);
            return;
        }
        // CHAT before JOIN, client SYSTEM/USER_LIST forgery, and unknown lines
        // are invalid pre-join.
        recordHandshakeFailure(connection, peer);
    }

    private void handlePostJoin(ClientConnection connection, PeerState peer, String line) {
        ProtocolType type = MessageCodec.detectType(line);
        if (type == ProtocolType.CHAT) {
            Message msg;
            try {
                msg = MessageCodec.decode(line);
            } catch (MessageFormatException e) {
                recordViolation(connection, peer);
                return;
            }
            String registered = connection.getUsername();
            if (registered == null || !msg.getSender().equals(registered)) {
                // Sender spoofing: never broadcast.
                recordViolation(connection, peer);
                return;
            }
            broadcastLine(MessageCodec.encode(msg));
            notifyChatMessage(msg);
            return;
        }
        if (type == ProtocolType.DISCONNECT) {
            DisconnectMessage dm;
            try {
                dm = MessageCodec.decodeDisconnect(line);
            } catch (MessageFormatException e) {
                recordViolation(connection, peer);
                return;
            }
            String registered = connection.getUsername();
            if (registered == null || !dm.getSender().equals(registered)) {
                recordViolation(connection, peer);
                return;
            }
            // Graceful leave.
            removeAndAnnounce(connection);
            return;
        }
        // Second JOIN, client SYSTEM/USER_LIST forgery, or unknown line:
        // violation, never broadcast.
        recordViolation(connection, peer);
    }

    private void recordHandshakeFailure(ClientConnection connection, PeerState peer) {
        boolean over;
        synchronized (peer) {
            peer.handshakeFailures++;
            over = peer.handshakeFailures >= MAX_HANDSHAKE_FAILURES;
        }
        if (over) {
            // Quiet removal: never joined, so no leave broadcast.
            removeAndAnnounce(connection);
        }
    }

    private void recordViolation(ClientConnection connection, PeerState peer) {
        boolean over;
        synchronized (peer) {
            peer.violations++;
            over = peer.violations >= MAX_PROTOCOL_VIOLATIONS;
        }
        if (over) {
            // Joined clients get the standard leave broadcast via the funnel.
            removeAndAnnounce(connection);
        }
    }

    // ------------------------------------------------------------------
    // Removal funnel (exactly-once leave)
    // ------------------------------------------------------------------

    /**
     * Remove a connection exactly once.
     * Joined users: unregister, broadcast USER_LEFT, notify onUserLeft.
     * Unjoined: quiet removal. Room keeps running either way.
     */
    private void removeAndAnnounce(ClientConnection connection) {
        if (connection == null) {
            return;
        }
        if (removalClaimed.putIfAbsent(connection.getConnectionId(), Boolean.TRUE) != null) {
            return; // already handled (e.g. DISCONNECT then EOF, or double callback)
        }
        peerStates.remove(connection.getConnectionId());

        String username = connection.getUsername();
        boolean wasJoined = username != null;
        boolean unregistered = false;
        if (wasJoined) {
            unregistered = usersByKey.remove(normalize(username), connection);
        }

        // Close the transport outside any index lock (Handler stop is lock-free here).
        try {
            connection.close();
        } catch (Exception e) {
            // Ignore close errors
        }
        safeRemoveFromChatServer(connection);

        // Only the thread that unregistered a joined user announces the leave.
        if (wasJoined && unregistered) {
            if (running.get()) {
                broadcastLeave(username);
                notifyUserLeft(username);
            }
        }
    }

    private void safeRemoveFromChatServer(ClientConnection connection) {
        try {
            chatServer.removeClient(connection.getConnectionId());
        } catch (Exception e) {
            // Ignore removal errors
        }
    }

    private void sendRejectedAndRemove(ClientConnection connection, PeerState peer) {
        try {
            SystemMessage rejected = new SystemMessage(
                    EventType.USERNAME_REJECTED, "Username is already in use.", LocalDateTime.now());
            MessageHandler handler = connection.getMessageHandler();
            if (handler != null) {
                handler.sendMessage(MessageCodec.encodeSystem(rejected));
            }
        } catch (Exception e) {
            // Best effort only
        }
        // Never registered: quiet removal, no join event, no leave broadcast.
        removeAndAnnounce(connection);
    }

    // ------------------------------------------------------------------
    // Broadcast
    // ------------------------------------------------------------------

    /**
     * Broadcast one wire line to every joined, active connection.
     * Snapshot recipients first; never hold index locks during socket writes;
     * one failed recipient never blocks the others; failures are removed after.
     */
    private void broadcastLine(String line) {
        if (line == null) {
            return;
        }
        List<ClientConnection> recipients = new ArrayList<>();
        for (ClientConnection c : usersByKey.values()) {
            if (c.isJoined() && c.isConnected()) {
                recipients.add(c);
            }
        }
        List<ClientConnection> failed = new ArrayList<>();
        for (ClientConnection c : recipients) {
            boolean ok = false;
            try {
                MessageHandler h = c.getMessageHandler();
                ok = (h != null) && h.sendMessage(line);
            } catch (Exception e) {
                ok = false;
            }
            if (!ok) {
                failed.add(c);
            }
        }
        for (ClientConnection c : failed) {
            removeAndAnnounce(c);
        }
    }

    private void broadcastJoin(String username) {
        try {
            SystemMessage joined = new SystemMessage(
                    EventType.USER_JOINED, username + " joined the chat.", LocalDateTime.now());
            broadcastLine(MessageCodec.encodeSystem(joined));
        } catch (Exception e) {
            // Best effort only
        }
    }

    private void broadcastLeave(String username) {
        try {
            SystemMessage left = new SystemMessage(
                    EventType.USER_LEFT, username + " left the chat.", LocalDateTime.now());
            broadcastLine(MessageCodec.encodeSystem(left));
        } catch (Exception e) {
            // Best effort only
        }
    }

    /**
     * Build the authoritative membership snapshot: host first, remaining
     * users sorted case-insensitively, original casing retained.
     *
     * <p>Consistency model: the member set is copied from the concurrent
     * username index (a safe point-in-time view, no duplicates or invalid
     * names) without holding any lock; sorting and encoding happen outside
     * the copy, and broadcasting is never blocked. A join/leave racing the
     * copy is delivered by the subsequent USER_JOINED/USER_LEFT event, so
     * clients converge via incremental updates.
     */
    private UserListMessage createCurrentUserListMessage() {
        List<String> names = new ArrayList<>();
        for (ClientConnection c : usersByKey.values()) {
            String username = c.getUsername();
            if (username != null) {
                names.add(username);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (hostUsername != null) {
            names.removeIf(new java.util.function.Predicate<String>() {
                @Override
                public boolean test(String name) {
                    return name.equalsIgnoreCase(hostUsername);
                }
            });
            names.add(0, hostUsername);
        }
        return new UserListMessage(names, LocalDateTime.now());
    }

    /**
     * Unicast the authoritative snapshot to one newly joined client.
     * @return true if the line was accepted by the transport
     */
    private boolean sendUserListTo(ClientConnection connection) {
        try {
            String line = MessageCodec.encodeUserList(createCurrentUserListMessage());
            MessageHandler handler = connection.getMessageHandler();
            return handler != null && handler.sendMessage(line);
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Listener notifications (never under lock)
    // ------------------------------------------------------------------

    private void notifyUserJoined(final String username) {
        final GroupChatServerListener l = currentListener();
        if (l != null) {
            safeCallback(new Runnable() {
                @Override
                public void run() {
                    l.onUserJoined(username);
                }
            });
        }
    }

    private void notifyUserLeft(final String username) {
        final GroupChatServerListener l = currentListener();
        if (l != null) {
            safeCallback(new Runnable() {
                @Override
                public void run() {
                    l.onUserLeft(username);
                }
            });
        }
    }

    private void notifyChatMessage(final Message message) {
        final GroupChatServerListener l = currentListener();
        if (l != null) {
            safeCallback(new Runnable() {
                @Override
                public void run() {
                    l.onChatMessage(message);
                }
            });
        }
    }

    private void safeCallback(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            // Never let listener exceptions break server threads
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private boolean isReserved(String normalizedKey) {
        if (hostKey != null && hostKey.equals(normalizedKey)) {
            return true;
        }
        return usersByKey.containsKey(normalizedKey);
    }
}
