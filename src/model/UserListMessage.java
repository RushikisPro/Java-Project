package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UserListMessage carries an authoritative room-membership snapshot.
 * Only the server may originate snapshots. Immutable: usernames are stored
 * trimmed, deduplicated case-insensitively (first casing wins), in supplied
 * order, behind an unmodifiable defensive copy.
 */
public final class UserListMessage {

    public static final int MAX_USERS = 100;

    private final List<String> usernames;
    private final LocalDateTime timestamp;

    /**
     * Full constructor.
     * @param usernames Non-empty collection of valid usernames (max 100 unique)
     * @param timestamp Snapshot time (must not be null)
     * @throws IllegalArgumentException if any validation fails
     */
    public UserListMessage(Collection<String> usernames, LocalDateTime timestamp) {
        if (usernames == null) {
            throw new IllegalArgumentException("Usernames cannot be null");
        }
        if (usernames.isEmpty()) {
            throw new IllegalArgumentException("Usernames cannot be empty");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : usernames) {
            if (raw == null || raw.trim().isEmpty()) {
                throw new IllegalArgumentException("Username cannot be null or blank");
            }
            if (raw.contains("\n") || raw.contains("\r")) {
                throw new IllegalArgumentException("Username cannot contain newline or carriage return");
            }
            if (raw.trim().length() > Message.MAX_SENDER_LENGTH) {
                throw new IllegalArgumentException("Username exceeds maximum length of "
                        + Message.MAX_SENDER_LENGTH + " characters");
            }
            String key = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (!unique.containsKey(key)) {
                unique.put(key, raw.trim());
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("Usernames cannot be empty");
        }
        if (unique.size() > MAX_USERS) {
            throw new IllegalArgumentException("User list exceeds maximum of "
                    + MAX_USERS + " users");
        }
        this.usernames = Collections.unmodifiableList(new ArrayList<>(unique.values()));
        this.timestamp = timestamp;
    }

    /**
     * Convenience constructor using the current timestamp.
     * @param usernames Non-empty collection of valid usernames
     * @throws IllegalArgumentException if any validation fails
     */
    public UserListMessage(Collection<String> usernames) {
        this(usernames, LocalDateTime.now());
    }

    /**
     * Get the snapshot usernames (immutable, order-preserved).
     * @return An unmodifiable view of the usernames
     */
    public List<String> getUsernames() {
        return usernames;
    }

    /**
     * Get the snapshot timestamp.
     * @return The timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserListMessage that = (UserListMessage) o;
        return Objects.equals(usernames, that.usernames) &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usernames, timestamp);
    }

    @Override
    public String toString() {
        return "UserListMessage{" +
               "usernames=" + usernames +
               ", timestamp=" + timestamp +
               '}';
    }
}
