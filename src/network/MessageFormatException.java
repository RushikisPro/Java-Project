package network;

/**
 * Exception thrown when a message cannot be encoded or decoded.
 * This is a checked exception to distinguish protocol errors from network errors.
 */
public class MessageFormatException extends Exception {
    
    /**
     * Constructs a new MessageFormatException with the specified detail message.
     * @param message The detail message
     */
    public MessageFormatException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new MessageFormatException with the specified detail message and cause.
     * @param message The detail message
     * @param cause The cause
     */
    public MessageFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
