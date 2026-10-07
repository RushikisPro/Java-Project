package model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Message class representing a chat message in the LAN Chat application.
 * Contains sender information, message text, and timestamp.
 * This class is immutable.
 */
public class Message {
    
    public static final int MAX_MESSAGE_LENGTH = 1000;
    public static final int MAX_SENDER_LENGTH = 50;
    
    private final String sender;
    private final String text;
    private final LocalDateTime timestamp;
    
    /**
     * Full constructor with all fields.
     * @param sender The username of the message sender (must not be null or blank)
     * @param text The message content (must not be null, blank, or contain newlines/carriage returns)
     * @param timestamp The time when the message was sent (must not be null)
     * @throws IllegalArgumentException if any validation fails
     */
    public Message(String sender, String text, LocalDateTime timestamp) {
        if (sender == null || sender.trim().isEmpty()) {
            throw new IllegalArgumentException("Sender cannot be null or blank");
        }
        if (sender.contains("\n") || sender.contains("\r")) {
            throw new IllegalArgumentException("Sender cannot contain newline or carriage return");
        }
        if (sender.trim().length() > MAX_SENDER_LENGTH) {
            throw new IllegalArgumentException("Sender exceeds maximum length of " + MAX_SENDER_LENGTH + " characters");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or blank");
        }
        if (text.contains("\n") || text.contains("\r")) {
            throw new IllegalArgumentException("Text cannot contain newline or carriage return");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        if (text.trim().length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Text exceeds maximum length of " + MAX_MESSAGE_LENGTH + " characters");
        }
        
        this.sender = sender.trim();
        this.text = text.trim();
        this.timestamp = timestamp;
    }
    
    /**
     * Convenience constructor with sender and text, using current timestamp.
     * @param sender The username of the message sender (must not be null or blank)
     * @param text The message content (must not be null, blank, or contain newlines/carriage returns)
     * @throws IllegalArgumentException if any validation fails
     */
    public Message(String sender, String text) {
        this(sender, text, LocalDateTime.now());
    }
    
    /**
     * Get the sender's username.
     * @return The sender's username
     */
    public String getSender() {
        return sender;
    }
    
    /**
     * Get the message text.
     * @return The message content
     */
    public String getText() {
        return text;
    }
    
    /**
     * Get the timestamp of the message.
     * @return The timestamp when the message was sent
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(sender, message.sender) &&
               Objects.equals(text, message.text) &&
               Objects.equals(timestamp, message.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sender, text, timestamp);
    }
    
    @Override
    public String toString() {
        return "Message{" +
               "sender='" + sender + '\'' +
               ", text='" + text + '\'' +
               ", timestamp=" + timestamp +
               '}';
    }
}
