package network;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ClientConnection represents a single connected client to the chat server.
 * Each connection has a unique ID and owns one accepted Socket.
 * This class is thread-safe and provides idempotent close operations.
 *
 * <p>Step 9B group-chat metadata:
 * - A username may be registered at most once via {@link #registerUsername(String)}.
 * - The username cannot change after successful registration.
 * - {@link #isJoined()} is false until registration succeeds.
 * - At most one active {@link MessageHandler} may be owned at a time.
 * - {@link #close()} is idempotent, stops the owned handler, and never
 *   closes the ChatServer. Handler shutdown is performed outside internal
 *   locks to avoid recursive cleanup deadlocks when the handler closes
 *   the same underlying socket.
 */
public class ClientConnection {

    private final long connectionId;
    private final Socket socket;
    private final AtomicBoolean connected;
    private final Object metaLock = new Object();
    private String username;
    private MessageHandler messageHandler;

    /**
     * Constructor for ClientConnection.
     * @param connectionId The unique connection ID
     * @param socket The accepted client socket
     * @throws IllegalArgumentException if socket is null
     */
    public ClientConnection(long connectionId, Socket socket) {
        if (socket == null) {
            throw new IllegalArgumentException("Socket cannot be null");
        }
        this.connectionId = connectionId;
        this.socket = socket;
        this.connected = new AtomicBoolean(true);
    }

    /**
     * Get the unique connection ID.
     * @return The connection ID
     */
    public long getConnectionId() {
        return connectionId;
    }

    /**
     * Get the client socket.
     * @return The socket
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Check if this connection is still connected.
     * A closed connection is in its terminal state and never reopens.
     * @return true if connected, false if closed
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Get the registered username, or null if JOIN has not succeeded yet.
     * @return The username, or null when unregistered
     */
    public String getUsername() {
        synchronized (metaLock) {
            return username;
        }
    }

    /**
     * Check whether this connection completed the username handshake.
     * @return true after successful username registration, false before
     */
    public boolean isJoined() {
        synchronized (metaLock) {
            return username != null;
        }
    }

    /**
     * Register the username for this connection.
     * May only succeed once; the username cannot change afterwards.
     * Thread-safe: concurrent callers are serialized and at most one wins.
     * @param username The username to register (must not be null or blank)
     * @return true if this call registered the username, false if one was already registered
     * @throws IllegalArgumentException if username is null or blank
     */
    public boolean registerUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        synchronized (metaLock) {
            if (this.username != null) {
                return false;
            }
            this.username = username;
            return true;
        }
    }

    /**
     * Get the owned MessageHandler, or null if none was assigned.
     * @return The message handler, or null
     */
    public MessageHandler getMessageHandler() {
        synchronized (metaLock) {
            return messageHandler;
        }
    }

    /**
     * Assign the MessageHandler owned by this connection.
     * A connection may not own multiple active handlers: assigning a second,
     * different handler while one is already assigned throws.
     * Assigning the same instance again is tolerated.
     * @param handler The handler to assign (must not be null)
     * @throws IllegalArgumentException if handler is null
     * @throws IllegalStateException if a different handler is already assigned
     */
    public void setMessageHandler(MessageHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("MessageHandler cannot be null");
        }
        synchronized (metaLock) {
            if (messageHandler != null && messageHandler != handler) {
                throw new IllegalStateException("ClientConnection already owns a MessageHandler");
            }
            messageHandler = handler;
        }
    }

    /**
     * Close this client connection.
     * This method is idempotent - calling it multiple times has no additional effect.
     * Closing one ClientConnection does not close the ChatServer.
     * The owned MessageHandler, if any, is stopped without holding internal locks.
     */
    public void close() {
        if (connected.compareAndSet(true, false)) {
            MessageHandler handlerToStop;
            synchronized (metaLock) {
                handlerToStop = messageHandler;
            }
            if (handlerToStop != null) {
                try {
                    handlerToStop.stop();
                } catch (Exception e) {
                    // Ignore handler shutdown errors - socket close below is authoritative
                }
            }
            closeSocketQuietly();
        }
    }

    /**
     * Helper method to close the socket quietly without throwing exceptions.
     */
    private void closeSocketQuietly() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (java.io.IOException e) {
                // Ignore close errors - socket may already be closed by MessageHandler
            }
        }
    }
}
