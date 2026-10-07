package controller;

import model.Message;
import model.SystemMessage;
import ui.ChatView;

import javax.swing.*;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FakeChatView implements ChatView {
    private final JTextField messageField = new JTextField();
    private final JButton sendButton = new JButton();
    private final JButton disconnectButton = new JButton();

    private final List<Message> localMessages = Collections.synchronizedList(new ArrayList<>());
    private final List<Message> remoteMessages = Collections.synchronizedList(new ArrayList<>());
    private final List<String> systemMessages = Collections.synchronizedList(new ArrayList<>());

    private final AtomicReference<String> connectionStatus = new AtomicReference<>();
    private final AtomicBoolean messagingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean disconnectEnabled = new AtomicBoolean(false);
    private final AtomicInteger characterCount = new AtomicInteger(0);
    private final AtomicInteger maximumMessageLength = new AtomicInteger(Message.MAX_MESSAGE_LENGTH);

    // Step 9C-2 group presentation state (thread-safe).
    private final List<String> groupMessages = Collections.synchronizedList(new ArrayList<>());
    private final List<SystemMessage> trustedSystemMessages =
            Collections.synchronizedList(new ArrayList<>());
    private final List<String> rawOnlineUsernames = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<String> onlineLocalUsername = new AtomicReference<>();
    private final AtomicReference<String> onlineHostUsername = new AtomicReference<>();
    private final AtomicReference<String> roomStatus = new AtomicReference<>();
    private final AtomicBoolean roomControlsEnabled = new AtomicBoolean(false);
    private final AtomicReference<String> leaveButtonText = new AtomicReference<>();

    @Override
    public JTextField getMessageField() {
        return messageField;
    }

    @Override
    public JButton getSendButton() {
        return sendButton;
    }

    @Override
    public JButton getDisconnectButton() {
        return disconnectButton;
    }

    @Override
    public void appendLocalMessage(Message message) {
        localMessages.add(message);
    }

    @Override
    public void appendRemoteMessage(Message message) {
        remoteMessages.add(message);
    }

    @Override
    public void appendSystemMessage(String message) {
        systemMessages.add(message);
    }

    @Override
    public void setMessagingEnabled(boolean enabled) {
        messagingEnabled.set(enabled);
    }

    @Override
    public void setDisconnectEnabled(boolean enabled) {
        disconnectEnabled.set(enabled);
    }

    @Override
    public void setConnectionStatus(String status) {
        connectionStatus.set(status);
    }

    @Override
    public void updateCharacterCount(int currentLength) {
        characterCount.set(currentLength);
    }

    @Override
    public void setMaximumMessageLength(int maximumLength) {
        maximumMessageLength.set(maximumLength);
    }

    @Override
    public Component getParentComponent() {
        return null;
    }

    // Step 9C-2 group presentation (mirrors ChatFrame display rules).

    @Override
    public void appendGroupMessage(Message message, String localUsername) {
        if (message == null) {
            return;
        }
        String line;
        if (localUsername != null && localUsername.equals(message.getSender())) {
            line = "[You] " + message.getText();
        } else {
            line = "[" + message.getSender() + "] " + message.getText();
        }
        groupMessages.add(line);
    }

    @Override
    public void appendTrustedSystemMessage(SystemMessage message) {
        if (message == null) {
            return;
        }
        trustedSystemMessages.add(message);
    }

    @Override
    public void setOnlineUsers(Collection<String> usernames, String localUsername, String hostUsername) {
        onlineLocalUsername.set(localUsername);
        onlineHostUsername.set(hostUsername);
        rawOnlineUsernames.clear();
        if (usernames != null) {
            for (String name : usernames) {
                if (name != null && !name.trim().isEmpty()) {
                    rawOnlineUsernames.add(name.trim());
                }
            }
        }
    }

    @Override
    public void addOnlineUser(String username, String localUsername, String hostUsername) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        onlineLocalUsername.set(localUsername);
        onlineHostUsername.set(hostUsername);
        String trimmed = username.trim();
        synchronized (rawOnlineUsernames) {
            for (String existing : rawOnlineUsernames) {
                if (existing.equalsIgnoreCase(trimmed)) {
                    return;
                }
            }
            rawOnlineUsernames.add(trimmed);
        }
    }

    @Override
    public void removeOnlineUser(String username) {
        if (username == null) {
            return;
        }
        String trimmed = username.trim();
        synchronized (rawOnlineUsernames) {
            for (int i = rawOnlineUsernames.size() - 1; i >= 0; i--) {
                if (rawOnlineUsernames.get(i).equalsIgnoreCase(trimmed)) {
                    rawOnlineUsernames.remove(i);
                }
            }
        }
    }

    @Override
    public void clearOnlineUsers() {
        rawOnlineUsernames.clear();
    }

    @Override
    public void setRoomStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return;
        }
        roomStatus.set(status);
        connectionStatus.set(status);
    }

    @Override
    public void setRoomControlsEnabled(boolean enabled) {
        roomControlsEnabled.set(enabled);
        messagingEnabled.set(enabled);
        disconnectEnabled.set(enabled);
    }

    @Override
    public void setLeaveButtonText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        leaveButtonText.set(text);
        disconnectButton.setText(text);
    }

    // Test accessors

    public List<Message> getLocalMessages() {
        return new ArrayList<>(localMessages);
    }

    public List<Message> getRemoteMessages() {
        return new ArrayList<>(remoteMessages);
    }

    public List<String> getSystemMessages() {
        return new ArrayList<>(systemMessages);
    }

    public String getConnectionStatus() {
        return connectionStatus.get();
    }

    public boolean isMessagingEnabled() {
        return messagingEnabled.get();
    }

    public boolean isDisconnectEnabled() {
        return disconnectEnabled.get();
    }

    public int getCharacterCount() {
        return characterCount.get();
    }

    public int getMaximumMessageLength() {
        return maximumMessageLength.get();
    }

    // Group presentation accessors for future controller tests.

    public List<String> getGroupMessages() {
        return new ArrayList<>(groupMessages);
    }

    public List<SystemMessage> getTrustedSystemMessages() {
        return new ArrayList<>(trustedSystemMessages);
    }

    public List<String> getDisplayedOnlineUsers() {
        Map<String, String> unique = new LinkedHashMap<>();
        synchronized (rawOnlineUsernames) {
            for (String raw : rawOnlineUsernames) {
                if (raw == null) {
                    continue;
                }
                String trimmed = raw.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String key = trimmed.toLowerCase(java.util.Locale.ROOT);
                if (!unique.containsKey(key)) {
                    unique.put(key, trimmed);
                }
            }
        }
        List<String> ordered = new ArrayList<>(unique.values());
        final String host = onlineHostUsername.get();
        ordered.sort(new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                boolean aHost = host != null && !host.trim().isEmpty() && a.equalsIgnoreCase(host.trim());
                boolean bHost = host != null && !host.trim().isEmpty() && b.equalsIgnoreCase(host.trim());
                if (aHost != bHost) {
                    return aHost ? -1 : 1;
                }
                return String.CASE_INSENSITIVE_ORDER.compare(a, b);
            }
        });
        String local = onlineLocalUsername.get();
        List<String> decorated = new ArrayList<>();
        for (String name : ordered) {
            boolean isLocal = local != null && local.equals(name);
            boolean isHost = host != null && !host.trim().isEmpty()
                    && name.equalsIgnoreCase(host.trim());
            if (isLocal && isHost) {
                decorated.add(name + " (You, Host)");
            } else if (isLocal) {
                decorated.add(name + " (You)");
            } else if (isHost) {
                decorated.add(name + " (Host)");
            } else {
                decorated.add(name);
            }
        }
        return decorated;
    }

    public String getRoomStatus() {
        return roomStatus.get();
    }

    public boolean isRoomControlsEnabled() {
        return roomControlsEnabled.get();
    }

    public String getLeaveButtonText() {
        return leaveButtonText.get();
    }

    // Bounded waiting helpers

    public boolean waitForSystemMessage(int expectedCount, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (systemMessages.size() >= expectedCount) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    public boolean waitForRemoteMessage(int expectedCount, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (remoteMessages.size() >= expectedCount) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    public boolean waitForLocalMessage(int expectedCount, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (localMessages.size() >= expectedCount) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    public boolean waitForStatus(String expectedStatus, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (expectedStatus.equals(connectionStatus.get())) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    public boolean waitForMessagingEnabled(boolean expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (messagingEnabled.get() == expected) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    public boolean waitForDisconnectEnabled(boolean expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (disconnectEnabled.get() == expected) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }
}
