package network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ChatClient class to handle client-side networking for the LAN Chat application.
 * This class manages connection to the server using Socket.
 * Uses a callback interface to notify listeners of connection events.
 * Each ChatClient instance is single-use - after disconnect, connect() will throw IllegalStateException.
 */
public class ChatClient {
    
    /**
     * Default port for the chat client.
     */
    public static final int DEFAULT_PORT = 5000;
    
    /**
     * Connection timeout in milliseconds.
     */
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    
    /**
     * Listener interface for client connection events.
     * Callbacks are invoked on the client's background thread.
     */
    public interface ClientListener {
        /**
         * Called when the client is attempting to connect.
         * @param hostAddress The host address being connected to
         * @param port The port being connected to
         */
        void onConnecting(String hostAddress, int port);
        
        /**
         * Called when the client has successfully connected.
         * @param socket The connected socket
         */
        void onConnected(Socket socket);
        
        /**
         * Called when a connection error occurs.
         * Not called for intentional cancellation via disconnect().
         * @param exception The exception that caused the error
         */
        void onConnectionError(Exception exception);
        
        /**
         * Called when the client disconnects.
         */
        void onDisconnected();
    }
    
    /**
     * Lifecycle states for ChatClient.
     */
    private enum State {
        IDLE,           // Initial state, before any connection attempt
        CONNECTING,     // Connection attempt in progress
        CONNECTED,      // Successfully connected
        DISCONNECTING,  // Disconnect in progress
        DISCONNECTED    // Disconnected (single-use, cannot reconnect)
    }
    
    private final Object stateLock = new Object();
    private Socket socket;
    private Socket connectingSocket;
    private String hostAddress;
    private int port;
    private State state = State.IDLE;
    private volatile boolean disconnectNotified;
    private ClientListener listener;
    private Thread connectionThread;
    private final AtomicLong attemptId = new AtomicLong(0);
    private volatile long currentAttemptId = 0;
    private volatile boolean deliverConnected = false;
    
    /**
     * Constructor for ChatClient with host address using default port (5000).
     * @param hostAddress The server host address
     */
    public ChatClient(String hostAddress) {
        this(hostAddress, DEFAULT_PORT);
    }
    
    /**
     * Constructor for ChatClient with host address and custom port.
     * @param hostAddress The server host address
     * @param port The server port number
     */
    public ChatClient(String hostAddress, int port) {
        this.hostAddress = hostAddress;
        this.port = port;
    }
    
    /**
     * Set the client listener to receive connection events.
     * @param listener The listener to set
     */
    public void setClientListener(ClientListener listener) {
        synchronized (stateLock) {
            this.listener = listener;
        }
    }
    
    /**
     * Connect to the server.
     * This method runs the connection attempt in a background thread.
     * It prevents duplicate connection attempts and uses a timeout.
     * After disconnect, this method will throw IllegalStateException.
     * @throws IllegalStateException if called after disconnect
     */
    public void connect() {
        synchronized (stateLock) {
            if (state == State.DISCONNECTED) {
                throw new IllegalStateException("ChatClient cannot reconnect after disconnect");
            }
            
            if (state != State.IDLE) {
                return; // Already connecting or connected
            }
            
            state = State.CONNECTING;
            currentAttemptId = attemptId.incrementAndGet();
            deliverConnected = false;
        }
        
        final long attemptId = currentAttemptId;
        
        connectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket newSocket = null;
                
                try {
                    // Notify listener that connection is starting
                    ClientListener currentListener;
                    synchronized (stateLock) {
                        currentListener = listener;
                    }
                    if (currentListener != null) {
                        currentListener.onConnecting(hostAddress, port);
                    }
                    
                    // Create socket and connect with timeout
                    newSocket = new Socket();
                    synchronized (stateLock) {
                        connectingSocket = newSocket;
                    }
                    newSocket.connect(new InetSocketAddress(hostAddress, port), CONNECTION_TIMEOUT_MS);
                    
                    // Verify this attempt is still current and can deliver connection
                    boolean shouldDeliver;
                    synchronized (stateLock) {
                        shouldDeliver = (attemptId == currentAttemptId) && 
                                       (state == State.CONNECTING) && 
                                       deliverConnected == false;
                        
                        if (shouldDeliver) {
                            state = State.CONNECTED;
                            socket = newSocket;
                            connectingSocket = null;
                            deliverConnected = true;
                            currentListener = listener;
                        }
                    }
                    
                    // Deliver connection callback outside lock
                    if (shouldDeliver && currentListener != null) {
                        currentListener.onConnected(socket);
                    }
                    
                } catch (UnknownHostException e) {
                    handleConnectionFailure(newSocket, e, attemptId);
                } catch (SocketTimeoutException e) {
                    handleConnectionFailure(newSocket, e, attemptId);
                } catch (IOException e) {
                    handleConnectionFailure(newSocket, e, attemptId);
                } catch (IllegalArgumentException e) {
                    handleConnectionFailure(newSocket, e, attemptId);
                }
            }
        });
        
        connectionThread.setDaemon(true);
        connectionThread.start();
    }
    
    /**
     * Handle connection failure.
     */
    private void handleConnectionFailure(Socket failedSocket, Exception exception, long attemptId) {
        closeSocketQuietly(failedSocket);
        
        boolean shouldNotify;
        ClientListener currentListener = null;
        
        synchronized (stateLock) {
            connectingSocket = null;
            
            // Only notify if this was the current attempt and not cancelled
            shouldNotify = (attemptId == currentAttemptId) && (state == State.CONNECTING);
            
            if (shouldNotify) {
                state = State.IDLE;
                currentListener = listener;
            }
        }
        
        if (shouldNotify && currentListener != null) {
            currentListener.onConnectionError(exception);
        }
    }
    
    /**
     * Disconnect from the server.
     * Safely closes the socket and resets connection state.
     * Safe to call multiple times.
     * Invalidates any pending connection callbacks.
     */
    public void disconnect() {
        synchronized (stateLock) {
            if (state == State.DISCONNECTED || state == State.DISCONNECTING) {
                return; // Already disconnected or disconnecting
            }
            
            state = State.DISCONNECTING;
            deliverConnected = false; // Invalidate pending connection callback
        }
        
        // Close connecting socket if connection attempt is in progress
        Socket socketToClose;
        synchronized (stateLock) {
            socketToClose = connectingSocket;
            connectingSocket = null;
        }
        
        if (socketToClose != null) {
            closeSocketQuietly(socketToClose);
        }
        
        // Close connected socket if it exists
        synchronized (stateLock) {
            socketToClose = socket;
            socket = null;
        }
        
        if (socketToClose != null) {
            closeSocketQuietly(socketToClose);
        }
        
        // Interrupt connection thread if it's still running
        Thread threadToInterrupt;
        synchronized (stateLock) {
            threadToInterrupt = connectionThread;
        }
        
        if (threadToInterrupt != null && threadToInterrupt.isAlive()) {
            threadToInterrupt.interrupt();
        }
        
        // Notify listener of disconnection once
        boolean shouldNotify;
        ClientListener currentListener;
        
        synchronized (stateLock) {
            if (disconnectNotified) {
                state = State.DISCONNECTED;
                return;
            }
            shouldNotify = true;
            disconnectNotified = true;
            state = State.DISCONNECTED;
            currentListener = listener;
        }
        
        if (shouldNotify && currentListener != null) {
            currentListener.onDisconnected();
        }
    }
    
    /**
     * Check if the client is currently connected.
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        synchronized (stateLock) {
            return state == State.CONNECTED;
        }
    }
    
    /**
     * Check if the client is currently attempting to connect.
     * @return true if connecting, false otherwise
     */
    public boolean isConnecting() {
        synchronized (stateLock) {
            return state == State.CONNECTING;
        }
    }
    
    /**
     * Get the connected socket.
     * @return The socket, or null if not connected
     */
    public Socket getSocket() {
        synchronized (stateLock) {
            return socket;
        }
    }
    
    /**
     * Get the host address.
     * @return The host address
     */
    public String getHostAddress() {
        return hostAddress;
    }
    
    /**
     * Get the port.
     * @return The port number
     */
    public int getPort() {
        return port;
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
                // Ignore close errors
            }
        }
    }
}
