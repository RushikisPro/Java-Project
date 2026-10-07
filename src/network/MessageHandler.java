package network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * MessageHandler class to manage communication over an already-connected Socket.
 * This class handles sending and receiving plain-text messages using BufferedReader and PrintWriter.
 * Uses UTF-8 encoding and one message per line protocol.
 */
public class MessageHandler {
    
    /**
     * Listener interface for message events.
     * Callbacks are invoked on the receiver's background thread or on the thread that caused the event.
     */
    public interface MessageListener {
        /**
         * Called when a message is received.
         * @param message The received message
         */
        void onMessageReceived(String message);
        
        /**
         * Called when the connection is disconnected by the remote peer (EOF or unexpected network loss).
         * This is NOT called when the local application calls stop().
         */
        void onDisconnected();
        
        /**
         * Called when a message error occurs.
         * This is NOT called when the local application calls stop().
         * @param exception The exception that caused the error
         */
        void onMessageError(Exception exception);
    }
    
    /**
     * Lifecycle states for MessageHandler.
     */
    private enum State {
        NEW,        // Initial state, before start()
        STARTING,   // start() called, initialization in progress
        RUNNING,    // Successfully started, receiver thread active
        STOPPING,   // stop() called, cleanup in progress
        STOPPED,    // Cleanup complete
        FAILED      // Initialization failed
    }
    
    private final Socket socket;
    private final Object stateLock = new Object();
    private final Object writeLock = new Object();
    
    private BufferedReader reader;
    private PrintWriter writer;
    private MessageListener listener;
    private Thread receiverThread;
    
    private State state = State.NEW;
    private volatile boolean localShutdownRequested;
    private volatile boolean disconnectNotified;
    private volatile boolean errorNotified;
    private int receiverThreadStartCount = 0;
    
    /**
     * Constructor for MessageHandler with an already-connected Socket.
     * @param socket The connected socket to use for communication
     * @throws IllegalArgumentException if socket is null, closed, or unconnected
     */
    public MessageHandler(Socket socket) {
        if (socket == null) {
            throw new IllegalArgumentException("Socket cannot be null");
        }
        if (socket.isClosed()) {
            throw new IllegalArgumentException("Socket is closed");
        }
        if (!socket.isConnected()) {
            throw new IllegalArgumentException("Socket is not connected");
        }
        
        this.socket = socket;
    }
    
    /**
     * Set the message listener to receive message events.
     * @param listener The listener to set
     * @throws IllegalArgumentException if listener is null
     * @throws IllegalStateException if called after start()
     */
    public void setMessageListener(MessageListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        
        synchronized (stateLock) {
            if (state != State.NEW) {
                throw new IllegalStateException("Listener can only be set before start()");
            }
            this.listener = listener;
        }
    }
    
    /**
     * Start the message handler.
     * Creates BufferedReader and PrintWriter, and starts the receiver thread.
     * @return true if startup succeeded or already running, false if failed or already stopped
     */
    public boolean start() {
        MessageListener listenerToNotify = null;
        Exception initException = null;
        boolean success = false;
        boolean shouldCloseSocket = false;
        Thread threadToStart = null;
        
        synchronized (stateLock) {
            if (state == State.RUNNING) {
                return true; // Already running
            }
            
            if (state == State.STOPPED || state == State.FAILED) {
                return false; // Cannot restart
            }
            
            state = State.STARTING;
            
            try {
                // Create reader and writer with UTF-8 encoding
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
                
                localShutdownRequested = false;
                disconnectNotified = false;
                errorNotified = false;
                
                // Construct receiver thread but don't start it yet
                receiverThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        receiveMessages();
                    }
                });
                
                receiverThread.setDaemon(true);
                threadToStart = receiverThread;
                
                // Transition to RUNNING state
                state = State.RUNNING;
                success = true;
                
            } catch (IOException e) {
                // Initialization failed
                state = State.FAILED;
                initException = e;
                listenerToNotify = listener;
                shouldCloseSocket = true;
                success = false;
            }
        }
        
        // Close socket outside lock if needed
        if (shouldCloseSocket) {
            closeSocketQuietly();
        }
        
        // Start receiver thread outside lock after state is published
        if (threadToStart != null) {
            threadToStart.start();
            receiverThreadStartCount++;
        }
        
        // Notify error outside lock
        if (listenerToNotify != null && initException != null) {
            listenerToNotify.onMessageError(initException);
        }
        
        return success;
    }
    
    /**
     * Send a message through the socket.
     * @param message The message to send
     * @return true if sending succeeded, false otherwise
     */
    public boolean sendMessage(String message) {
        if (message == null) {
            return false;
        }
        
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        
        // Reject messages containing newline or carriage-return
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            return false;
        }
        
        // Check running state without holding write lock
        if (getState() != State.RUNNING) {
            return false;
        }
        
        // Check socket state without holding write lock
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            return false;
        }
        
        Exception failure = null;
        boolean sent = false;
        
        synchronized (writeLock) {
            // Re-check state after acquiring write lock
            if (getState() != State.RUNNING || writer == null) {
                return false;
            }
            
            try {
                writer.println(trimmed);
                writer.flush();
                
                if (writer.checkError()) {
                    failure = new IOException("Writer error occurred");
                    sent = false;
                } else {
                    sent = true;
                }
                
            } catch (Exception e) {
                failure = e;
                sent = false;
            }
        }
        
        // Notify error outside lock
        if (failure != null) {
            notifyErrorOutsideLock(failure);
        }
        
        return sent;
    }
    
    /**
     * Stop the message handler.
     * Closes all resources and stops the receiver thread.
     * This is a local shutdown and will NOT call onDisconnected() or onMessageError().
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
            localShutdownRequested = true;
        }
        
        // Close socket as primary way to unblock readLine()
        closeSocketQuietly();
        
        // Interrupt receiver thread if still running
        Thread threadToInterrupt;
        synchronized (stateLock) {
            threadToInterrupt = receiverThread;
        }
        
        if (threadToInterrupt != null && threadToInterrupt.isAlive()) {
            threadToInterrupt.interrupt();
        }
        
        // Finalize state to STOPPED
        synchronized (stateLock) {
            state = State.STOPPED;
        }
    }
    
    /**
     * Get the current state safely.
     * @return The current state
     */
    private State getState() {
        synchronized (stateLock) {
            return state;
        }
    }
    
    /**
     * Check if the handler is currently running.
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return getState() == State.RUNNING;
    }
    
    /**
     * Get the socket.
     * @return The socket
     */
    public Socket getSocket() {
        return socket;
    }
    
    /**
     * Receive messages in a background thread.
     */
    private void receiveMessages() {
        BufferedReader localReader;
        synchronized (stateLock) {
            localReader = reader;
        }
        
        // If we were stopped during STARTING, don't enter the receive loop
        if (getState() != State.RUNNING) {
            return;
        }
        
        try {
            String line;
            while (getState() == State.RUNNING && (line = localReader.readLine()) != null) {
                if (getState() != State.RUNNING) {
                    break;
                }
                
                final String message = line;
                // Notify listener without holding lock
                MessageListener currentListener;
                synchronized (stateLock) {
                    currentListener = listener;
                }
                if (currentListener != null) {
                    currentListener.onMessageReceived(message);
                }
            }
            
            // readLine() returned null - remote disconnection
            if (getState() == State.RUNNING) {
                synchronized (stateLock) {
                    state = State.STOPPED;
                }
                notifyDisconnectionOutsideLock();
            }
            
        } catch (IOException e) {
            boolean shouldNotify;
            synchronized (stateLock) {
                shouldNotify = (state == State.RUNNING) && !localShutdownRequested;
                if (shouldNotify) {
                    state = State.STOPPED;
                }
            }
            
            if (shouldNotify) {
                notifyErrorOutsideLock(e);
                notifyDisconnectionOutsideLock();
            }
        }
    }
    
    /**
     * Notify listener of disconnection (at most once).
     * Only called for remote disconnect, not local stop().
     */
    private void notifyDisconnectionOutsideLock() {
        boolean shouldNotify;
        MessageListener currentListener;
        
        synchronized (stateLock) {
            if (disconnectNotified || localShutdownRequested) {
                return;
            }
            shouldNotify = true;
            disconnectNotified = true;
            currentListener = listener;
        }
        
        if (shouldNotify && currentListener != null) {
            currentListener.onDisconnected();
        }
    }
    
    /**
     * Notify listener of an error (at most once per failure).
     * Only called for unexpected errors, not local stop().
     */
    private void notifyErrorOutsideLock(Exception exception) {
        boolean shouldNotify;
        MessageListener currentListener;
        
        synchronized (stateLock) {
            if (errorNotified || localShutdownRequested) {
                return;
            }
            shouldNotify = true;
            errorNotified = true;
            currentListener = listener;
        }
        
        if (shouldNotify && currentListener != null) {
            currentListener.onMessageError(exception);
        }
    }
    
    /**
     * Helper method to close socket quietly without throwing exceptions.
     */
    private void closeSocketQuietly() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }
    
    /**
     * Package-private test accessor to get the receiver thread start count.
     * @return The number of times the receiver thread was started
     */
    int getReceiverThreadStartCountForTest() {
        return receiverThreadStartCount;
    }
}
