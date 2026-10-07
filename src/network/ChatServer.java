package network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ChatServer class to handle server-side networking for the LAN Chat application.
 * This class manages multiple client connections using ServerSocket.
 * Uses a callback interface to notify listeners of server events.
 * 
 * MULTICLIENT ARCHITECTURE:
 * - ServerSocket remains open to accept multiple clients
 * - Each client gets a unique ClientConnection with a connection ID
 * - Clients are stored in a thread-safe ConcurrentHashMap
 * - Individual client disconnection does not stop the server
 * - Server stops only when explicitly requested
 */
public class ChatServer {
    
    /**
     * Default port for the chat server.
     */
    public static final int DEFAULT_PORT = 5000;
    
    /**
     * Lifecycle states for ChatServer.
     */
    private enum State {
        NEW,        // Initial state, before start()
        STARTING,   // Start in progress, ServerSocket being created
        LISTENING,  // ServerSocket created, accepting connections
        STOPPING,   // Stop in progress
        STOPPED,    // Cleanup complete
        FAILED      // Initialization failed
    }
    
    /**
     * Listener interface for server events.
     * Callbacks are invoked on the server's background thread.
     */
    public interface ServerListener {
        /**
         * Called when the server has successfully started.
         * @param ipAddress The local IP address the server is listening on
         * @param port The actual port the server is listening on (may differ from requested if port 0 was used)
         */
        void onServerStarted(String ipAddress, int port);
        
        /**
         * Called when a client has successfully connected.
         * @param connection The client connection object
         */
        void onClientConnected(ClientConnection connection);
        
        /**
         * Called when a client has disconnected.
         * @param connection The client connection that disconnected
         */
        void onClientDisconnected(ClientConnection connection);
        
        /**
         * Called when a server error occurs.
         * Not called for intentional shutdown via stop().
         * @param exception The exception that caused the error
         */
        void onServerError(Exception exception);
        
        /**
         * Called when the server has stopped.
         * Called after all client connections are closed and ServerSocket is closed.
         */
        void onServerStopped();
    }
    
    private final Object stateLock = new Object();
    private final ConcurrentHashMap<Long, ClientConnection> clientConnections;
    private final AtomicLong connectionIdGenerator;
    private ServerSocket serverSocket;
    private final int configuredPort;
    private volatile int actualPort = -1;
    private State state = State.NEW;
    private volatile boolean stopRequested;
    private ServerListener listener;
    private Thread acceptThread;
    
    /**
     * Default constructor for ChatServer using the default port (5000).
     */
    public ChatServer() {
        this(DEFAULT_PORT);
    }
    
    /**
     * Constructor for ChatServer with a custom port.
     * Port 0 can be used to let the OS assign an ephemeral port.
     * @param port The port number to listen on, or 0 for an ephemeral port
     */
    public ChatServer(int port) {
        this.configuredPort = port;
        this.clientConnections = new ConcurrentHashMap<>();
        this.connectionIdGenerator = new AtomicLong(0);
    }
    
    /**
     * Set the server listener to receive server events.
     * @param listener The listener to set
     */
    public void setServerListener(ServerListener listener) {
        synchronized (stateLock) {
            this.listener = listener;
        }
    }
    
    /**
     * Start the chat server.
     * Creates a ServerSocket and accepts multiple client connections in a background thread.
     * This method returns immediately; the actual connection waiting happens asynchronously.
     * If called when already in STARTING or LISTENING state, returns without doing anything.
     * If called after STOPPED or FAILED, does nothing.
     */
    public void start() {
        synchronized (stateLock) {
            if (state == State.STARTING || state == State.LISTENING) {
                return; // Already started
            }
            
            if (state == State.STOPPED || state == State.FAILED) {
                return; // Cannot restart
            }
            
            state = State.STARTING;
            stopRequested = false;
        }
        
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ServerSocket localServerSocket = null;
                String ipAddress = null;
                int actualPort = 0;
                Exception initException = null;
                
                try {
                    // Create server socket outside lock
                    localServerSocket = new ServerSocket(configuredPort);
                    ipAddress = getLocalIpAddress();
                    int boundPort = localServerSocket.getLocalPort();
                    ChatServer.this.actualPort = boundPort;
                    actualPort = boundPort;
                    
                    // Check if we should publish the server socket
                    boolean shouldPublish;
                    synchronized (stateLock) {
                        shouldPublish = (state == State.STARTING) && !stopRequested;
                        
                        if (shouldPublish) {
                            serverSocket = localServerSocket;
                            state = State.LISTENING;
                        }
                    }
                    
                    if (!shouldPublish) {
                        // Stop was requested before we could publish
                        closeServerSocketQuietly(localServerSocket);
                        return;
                    }
                    
                    // Notify listener that server has started (outside lock)
                    ServerListener currentListener;
                    synchronized (stateLock) {
                        currentListener = listener;
                    }
                    if (currentListener != null) {
                        currentListener.onServerStarted(ipAddress, actualPort);
                    }
                    
                    // Accept multiple client connections in a loop (outside lock)
                    while (true) {
                        // Check if stop was requested before accepting
                        boolean shouldContinue;
                        synchronized (stateLock) {
                            shouldContinue = !stopRequested && (state == State.LISTENING);
                        }
                        
                        if (!shouldContinue) {
                            break;
                        }
                        
                        Socket acceptedSocket;
                        try {
                            acceptedSocket = localServerSocket.accept();
                        } catch (IOException e) {
                            // Socket closed during accept - normal during shutdown
                            synchronized (stateLock) {
                                if (stopRequested || state != State.LISTENING) {
                                    break;
                                }
                            }
                            // Unexpected error during accept
                            throw e;
                        }
                        
                        // Check if stop was requested during accept
                        synchronized (stateLock) {
                            shouldContinue = !stopRequested && (state == State.LISTENING);
                        }
                        
                        if (!shouldContinue) {
                            // Stop was requested during accept - close the socket
                            closeSocketQuietly(acceptedSocket);
                            break;
                        }
                        
                        // Create client connection with unique ID
                        long connectionId = connectionIdGenerator.incrementAndGet();
                        ClientConnection connection = new ClientConnection(connectionId, acceptedSocket);
                        
                        // Add to connection map
                        clientConnections.put(connectionId, connection);
                        
                        // Notify listener that client has connected (outside lock)
                        synchronized (stateLock) {
                            currentListener = listener;
                        }
                        if (currentListener != null) {
                            currentListener.onClientConnected(connection);
                        }
                        
                        // Continue accepting more connections
                    }
                    
                } catch (IOException e) {
                    boolean shouldReport;
                    synchronized (stateLock) {
                        shouldReport = (state == State.STARTING || state == State.LISTENING) && !stopRequested;
                        
                        if (shouldReport) {
                            state = State.FAILED;
                            initException = e;
                        }
                    }
                    
                    if (shouldReport) {
                        ServerListener currentListener;
                        synchronized (stateLock) {
                            currentListener = listener;
                        }
                        if (currentListener != null) {
                            currentListener.onServerError(e);
                        }
                    }
                    
                    // Clean up if we created a socket but failed
                    if (localServerSocket != null) {
                        closeServerSocketQuietly(localServerSocket);
                    }
                } finally {
                    // If we exit the accept loop normally (not due to error), 
                    // the stop() method will handle cleanup
                }
            }
        });
        
        acceptThread.setDaemon(true);
        acceptThread.start();
    }
    
    /**
     * Stop the chat server.
     * Safely closes the server socket and all client connections.
     * Safe to call multiple times.
     * Does not call onServerError for intentional shutdown.
     */
    public void stop() {
        synchronized (stateLock) {
            if (state == State.STOPPED || state == State.FAILED) {
                return; // Already stopped or failed
            }
            
            if (state == State.STOPPING) {
                return; // Already stopping
            }
            
            state = State.STOPPING;
            stopRequested = true;
        }
        
        // Close server socket (safe to call multiple times)
        ServerSocket serverSocketToClose;
        synchronized (stateLock) {
            serverSocketToClose = serverSocket;
            serverSocket = null;
        }
        
        if (serverSocketToClose != null) {
            closeServerSocketQuietly(serverSocketToClose);
        }
        
        // Interrupt accept thread if it's still running
        Thread threadToInterrupt;
        synchronized (stateLock) {
            threadToInterrupt = acceptThread;
        }
        
        if (threadToInterrupt != null && threadToInterrupt.isAlive()) {
            threadToInterrupt.interrupt();
        }
        
        // Close all client connections
        Map<Long, ClientConnection> connectionsToClose;
        synchronized (stateLock) {
            connectionsToClose = new HashMap<>(clientConnections);
            clientConnections.clear();
        }
        
        for (ClientConnection connection : connectionsToClose.values()) {
            connection.close();
        }
        
        // Notify listener that server has stopped (outside lock)
        ServerListener currentListener;
        synchronized (stateLock) {
            currentListener = listener;
            state = State.STOPPED;
        }
        
        if (currentListener != null) {
            currentListener.onServerStopped();
        }
    }
    
    /**
     * Remove a specific client connection.
     * Closes the client connection and removes it from the connection map.
     * Does not stop the server or close the ServerSocket.
     * @param connectionId The connection ID to remove
     */
    public void removeClient(long connectionId) {
        ClientConnection connection = clientConnections.remove(connectionId);
        
        if (connection != null) {
            connection.close();
            
            // Notify listener that client has disconnected (outside lock)
            ServerListener currentListener;
            synchronized (stateLock) {
                currentListener = listener;
            }
            if (currentListener != null) {
                currentListener.onClientDisconnected(connection);
            }
        }
    }
    
    /**
     * Get a defensive snapshot of all connected clients.
     * Returns an unmodifiable copy so callers cannot mutate internal state.
     * @return An unmodifiable snapshot collection of client connections
     */
    public Collection<ClientConnection> getClientConnections() {
        return Collections.unmodifiableCollection(new ArrayList<>(clientConnections.values()));
    }
    
    /**
     * Get the number of currently connected clients.
     * @return The connected client count
     */
    public int getConnectedClientCount() {
        return clientConnections.size();
    }
    
    /**
     * Get the local IP address of the machine.
     * @return The local IP address as a string, or "Unknown" if unable to determine
     */
    public String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "Unknown";
        }
    }
    
    /**
     * Check if the server is currently running.
     * @return true if the server is in STARTING or LISTENING state, false otherwise
     */
    public boolean isRunning() {
        synchronized (stateLock) {
            return state == State.STARTING || state == State.LISTENING;
        }
    }
    
    /**
     * Get the port the server is listening on.
     * If an ephemeral port (0) was requested, returns the actual bound port
     * once the server has started; otherwise returns the configured port.
     * @return The actual bound port if available, else the configured port
     */
    public int getPort() {
        int bound = actualPort;
        if (bound > 0) {
            return bound;
        }
        return configuredPort;
    }

    /**
     * Get the configured port passed to the constructor.
     * @return The configured port (may be 0 for ephemeral)
     */
    public int getConfiguredPort() {
        return configuredPort;
    }

    /**
     * Get the actual bound port, or -1 if the server has not bound yet.
     * @return The actual bound port, or -1 if not yet bound
     */
    public int getActualPort() {
        return actualPort;
    }
    
    /**
     * Helper method to close a socket quietly without throwing exceptions.
     * @param socket The socket to close
     */
    private void closeSocketQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore close errors - socket may already be closed by MessageHandler
            }
        }
    }
    
    /**
     * Helper method to close a server socket quietly without throwing exceptions.
     * @param serverSocket The server socket to close
     */
    private void closeServerSocketQuietly(ServerSocket serverSocket) {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }
}
