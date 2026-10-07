package model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * DisconnectMessage class representing a graceful disconnect notification.
 * This class is immutable.
 */
public final class DisconnectMessage {
    
    private final String sender;
    private final LocalDateTime timestamp;
    
    /**
     * Full constructor with all fields.
     * @param sender The username of the user disconnecting (must not be null or blank)
     * @param timestamp The time when the disconnect occurred (must not be null)
     * @throws IllegalArgumentException if any validation fails
     */
    public DisconnectMessage(String sender, LocalDateTime timestamp) {
        if (sender == null || sender.trim().isEmpty()) {
            throw new IllegalArgumentException("Sender cannot be null or blank");
        }
        if (sender.contains("\n") || sender.contains("\r")) {
            throw new IllegalArgumentException("Sender cannot contain newline or carriage return");
        }
        if (sender.trim().length() > Message.MAX_SENDER_LENGTH) {
            throw new IllegalArgumentException("Sender exceeds maximum length of " + Message.MAX_SENDER_LENGTH + " characters");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        
        this.sender = sender.trim();
        this.timestamp = timestamp;
    }
    
    /**
     * Convenience constructor with sender, using current timestamp.
     * @param sender The username of the user disconnecting (must not be null or blank)
     * @throws IllegalArgumentException if any validation fails
     */
    public DisconnectMessage(String sender) {
        this(sender, LocalDateTime.now());
    }
    
    /**
     * Get the sender's username.
     * @return The sender's username
     */
    public String getSender() {
        return sender;
    }
    
    /**
     * Get the timestamp of the disconnect.
     * @return The timestamp when the disconnect occurred
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisconnectMessage that = (DisconnectMessage) o;
        return Objects.equals(sender, that.sender) &&
               Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sender, timestamp);
    }
    
    @Override
    public String toString() {
        return "DisconnectMessage{" +
               "sender='" + sender + '\'' +
               ", timestamp=" + timestamp +
               '}';
    }
}
