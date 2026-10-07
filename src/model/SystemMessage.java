package model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * SystemMessage class representing trusted server-generated room events.
 * Instances must only be created by trusted application/server code.
 * A normal client CHAT line must never be treated as a SystemMessage.
 * This class is immutable.
 */
public final class SystemMessage {

    /**
     * Trusted server event types.
     */
    public enum EventType {
        USER_JOINED,
        USER_LEFT,
        ROOM_CLOSED,
        USERNAME_REJECTED
    }

    public static final int MAX_TEXT_LENGTH = 200;

    private final EventType eventType;
    private final String text;
    private final LocalDateTime timestamp;

    /**
     * Full constructor with all fields.
     * @param eventType The event type (must not be null)
     * @param text The event text (must not be null or blank, max 200 chars after trim)
     * @param timestamp The time when the event occurred (must not be null)
     * @throws IllegalArgumentException if any validation fails
     */
    public SystemMessage(EventType eventType, String text, LocalDateTime timestamp) {
        if (eventType == null) {
            throw new IllegalArgumentException("EventType cannot be null");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or blank");
        }
        if (text.contains("\n") || text.contains("\r")) {
            throw new IllegalArgumentException("Text cannot contain newline or carriage return");
        }
        if (text.trim().length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text exceeds maximum length of " + MAX_TEXT_LENGTH + " characters");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }

        this.eventType = eventType;
        this.text = text.trim();
        this.timestamp = timestamp;
    }

    /**
     * Convenience constructor with event type and text, using current timestamp.
     * @param eventType The event type (must not be null)
     * @param text The event text (must not be null or blank)
     * @throws IllegalArgumentException if any validation fails
     */
    public SystemMessage(EventType eventType, String text) {
        this(eventType, text, LocalDateTime.now());
    }

    /**
     * Get the event type.
     * @return The event type
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Get the event text.
     * @return The event text
     */
    public String getText() {
        return text;
    }

    /**
     * Get the timestamp of the event.
     * @return The timestamp when the event occurred
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SystemMessage that = (SystemMessage) o;
        return eventType == that.eventType &&
               Objects.equals(text, that.text) &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, text, timestamp);
    }

    @Override
    public String toString() {
        return "SystemMessage{" +
               "eventType=" + eventType +
               ", text='" + text + '\'' +
               ", timestamp=" + timestamp +
               '}';
    }
}
