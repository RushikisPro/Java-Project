package ui;

import model.Message;
import model.SystemMessage;

import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ChatFrame class representing the main chat window UI.
 * This frame displays chat messages and provides input for sending messages.
 *
 * <p>Step 9C-2 group presentation: three-area room layout (header status,
 * center chat, right online-users panel, bottom entry), group message and
 * trusted SYSTEM formatting, labeled online users, room status and
 * role-aware leave-button labels. No networking logic lives here.
 */
public class ChatFrame extends JFrame implements ChatView {

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton disconnectButton;
    private JLabel characterCountLabel;
    private JLabel statusLabel;
    private String userName;
    private String role;
    private volatile int maximumMessageLength = Message.MAX_MESSAGE_LENGTH;
    private Color normalCharacterCountColor;
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // Group-room online-users panel state. Raw (undecorated) usernames are the
    // source of truth; decorated display rows are rebuilt after each change.
    // Accessed only on the EDT.
    private DefaultListModel<String> onlineUsersModel;
    private JList<String> onlineUsersList;
    private JLabel onlineUsersTitleLabel;
    private final List<String> rawOnlineUsernames = new ArrayList<>();
    private String onlineLocalUsername;
    private String onlineHostUsername;

    /**
     * Default constructor for ChatFrame.
     * Sets up the chat window UI with message display and input components.
     */
    public ChatFrame() {
        this(null, null);
    }

    /**
     * Constructor for ChatFrame with user name (legacy compatibility).
     * Sets up the chat window UI with message display and input components.
     * @param userName The user's name (can be null)
     */
    public ChatFrame(String userName) {
        this(userName, userName != null ? "Host" : null);
    }

    /**
     * Constructor for ChatFrame with user name and role.
     * Host role configures title "LAN Chat - Alice (Host)" with a
     * "Close Room" leave button; Client configures "LAN Chat - Bob (Client)"
     * with a "Leave Room" button. Unknown/null roles keep safe defaults.
     * @param userName The user's name (can be null)
     * @param role The user's role ("Host" or "Client", can be null)
     */
    public ChatFrame(String userName, String role) {
        this.userName = userName;
        this.role = role;

        // Set title based on role
        if (userName != null && role != null) {
            setTitle("LAN Chat - " + userName + " (" + role + ")");
        } else if (userName != null) {
            setTitle("LAN Chat - " + userName);
        } else {
            setTitle("LAN Chat - Chat Room");
        }

        initializeUI();
        applyRoleLeaveButton();
    }

    /**
     * Apply the role-aware leave-button label after components exist.
     */
    private void applyRoleLeaveButton() {
        if ("Host".equals(role)) {
            updateLeaveButton("Close Room");
        } else if ("Client".equals(role)) {
            updateLeaveButton("Leave Room");
        }
    }

    /**
     * Initialize the chat frame UI components.
     */
    private void initializeUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(800, 550);
        setLocationRelativeTo(null);

        // Create main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // North: room status label + leave/disconnect button
        statusLabel = new JLabel("Connected to peer");

        // Disconnect button (leave/close room in group usage)
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        disconnectButton.setToolTipText("Leave this chat");

        // Status panel with label and disconnect button
        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.add(disconnectButton, BorderLayout.EAST);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        // Center: chat message display area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // East: online-users panel
        JPanel onlinePanel = new JPanel(new BorderLayout(0, 4));
        onlinePanel.setBorder(BorderFactory.createTitledBorder("Room"));
        onlinePanel.setPreferredSize(new Dimension(170, 0));

        onlineUsersTitleLabel = new JLabel("Online Users (0)");
        onlineUsersModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(onlineUsersModel);
        onlineUsersList.setEnabled(false);
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersList.setToolTipText("Users currently in this chat room");
        JScrollPane onlineScrollPane = new JScrollPane(onlineUsersList);

        onlinePanel.add(onlineUsersTitleLabel, BorderLayout.NORTH);
        onlinePanel.add(onlineScrollPane, BorderLayout.CENTER);

        // South: input panel for typing messages
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        messageField = new JTextField();
        messageField.setEnabled(false); // Disabled until controller enables it
        messageField.setToolTipText("Type a message and press Enter");

        sendButton = new JButton("Send");
        sendButton.setEnabled(false); // Disabled until controller enables it
        sendButton.setToolTipText("Send message");

        // Character count label
        characterCountLabel = new JLabel("0 / " + maximumMessageLength);
        characterCountLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        normalCharacterCountColor = characterCountLabel.getForeground();

        // Input components panel with spacing
        JPanel inputComponentsPanel = new JPanel(new BorderLayout(8, 0));
        inputComponentsPanel.add(messageField, BorderLayout.CENTER);
        inputComponentsPanel.add(sendButton, BorderLayout.EAST);

        // Bottom panel with input and character count
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputComponentsPanel, BorderLayout.CENTER);
        bottomPanel.add(characterCountLabel, BorderLayout.SOUTH);

        inputPanel.add(bottomPanel, BorderLayout.CENTER);

        // Add system message based on role
        if (role != null) {
            appendSystemMessage("Connected as " + role + ".");
        }

        // Add components to main panel
        mainPanel.add(statusPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(onlinePanel, BorderLayout.EAST);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        // Add main panel to frame
        add(mainPanel);
    }

    /**
     * Get the chat message display area.
     * @return The JTextArea for displaying messages
     */
    public JTextArea getChatArea() {
        return chatArea;
    }

    /**
     * Get the message input field.
     * @return The JTextField for typing messages
     */
    public JTextField getMessageField() {
        return messageField;
    }

    /**
     * Get the send button.
     * @return The JButton for sending messages
     */
    public JButton getSendButton() {
        return sendButton;
    }

    /**
     * Get the disconnect button.
     * @return The JButton for disconnecting
     */
    public JButton getDisconnectButton() {
        return disconnectButton;
    }

    /**
     * Get the user name.
     * @return The user's name
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Get the user's role.
     * @return The user's role ("Host" or "Client")
     */
    public String getRole() {
        return role;
    }

    /**
     * Get the decorated online-user rows currently displayed.
     * @return A defensive snapshot copy
     */
    public List<String> getDisplayedOnlineUsers() {
        List<String> snapshot = new ArrayList<>();
        for (int i = 0; i < onlineUsersModel.getSize(); i++) {
            snapshot.add(onlineUsersModel.getElementAt(i));
        }
        return snapshot;
    }

    /**
     * Get the online-users title (includes the unique user count).
     * @return The title text
     */
    public String getOnlineUsersTitle() {
        return onlineUsersTitleLabel.getText();
    }

    /**
     * Append a message to the chat area.
     * This method is EDT-safe - it schedules the update if not already on the EDT.
     * @param message The message to append
     */
    public void appendMessage(String message) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                chatArea.append(message + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        });
    }

    /**
     * Format one group message line without touching the UI.
     * @param message The message (must not be null)
     * @param localUsername The local username (may be null)
     * @return The formatted line
     */
    private static String formatGroupLine(Message message, String localUsername) {
        String time = message.getTimestamp().format(DISPLAY_TIME_FORMATTER);
        if (localUsername != null && localUsername.equals(message.getSender())) {
            return "[" + time + "] You: " + message.getText();
        }
        return "[" + time + "] " + message.getSender() + ": " + message.getText();
    }

    /**
     * Append a local message (sent by this user) to the chat area.
     * Displays as "[HH:mm] You: &lt;text&gt;"
     * This method is EDT-safe.
     * @param message The message to append
     */
    public void appendLocalMessage(Message message) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                String time = message.getTimestamp().format(DISPLAY_TIME_FORMATTER);
                chatArea.append("[" + time + "] You: " + message.getText() + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        });
    }

    /**
     * Append a remote message (received from peer) to the chat area.
     * Displays as "[HH:mm] &lt;sender&gt;: &lt;text&gt;"
     * This method is EDT-safe.
     * @param message The message to append
     */
    public void appendRemoteMessage(Message message) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                String time = message.getTimestamp().format(DISPLAY_TIME_FORMATTER);
                chatArea.append("[" + time + "] " + message.getSender() + ": " + message.getText() + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        });
    }

    /**
     * Append a group message, rendering the local user's own messages as You.
     * Uses the message timestamp (never generates a new one), appends exactly
     * one line and moves the caret to the end. Null messages are ignored.
     * This method is EDT-safe.
     * @param message The message to append
     * @param localUsername The local username (null shows the sender name)
     */
    @Override
    public void appendGroupMessage(final Message message, final String localUsername) {
        if (message == null) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                chatArea.append(formatGroupLine(message, localUsername) + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        });
    }

    /**
     * Append a system message to the chat area.
     * System messages are formatted with "[System]" prefix.
     * This method is EDT-safe - it schedules the update if not already on the EDT.
     * @param message The system message to append
     */
    public void appendSystemMessage(String message) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                chatArea.append("[System] " + message + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        });
    }

    /**
     * Append a trusted server SYSTEM event as "[System] &lt;text&gt;".
     * Null messages are ignored. Appends exactly once and scrolls to bottom.
     * This method is EDT-safe.
     * @param message The validated system message
     */
    @Override
    public void appendTrustedSystemMessage(final SystemMessage message) {
        if (message == null) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                chatArea.append("[System] " + message.getText() + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        });
    }

    /**
     * Set the connection status label.
     * This method is EDT-safe.
     * @param status The status text to display
     */
    public void setConnectionStatus(String status) {
        setRoomStatus(status);
    }

    /**
     * Set the room status label. Null or blank statuses are ignored.
     * This method is EDT-safe.
     * @param status The status text to display
     */
    @Override
    public void setRoomStatus(final String status) {
        if (status == null || status.trim().isEmpty()) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

    /**
     * Update the character count label based on current text.
     * This method is EDT-safe.
     * @param currentLength The current length of the text
     */
    public void updateCharacterCount(int currentLength) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                characterCountLabel.setText(currentLength + " / " + maximumMessageLength);
                if (currentLength > maximumMessageLength) {
                    characterCountLabel.setForeground(Color.RED);
                } else {
                    characterCountLabel.setForeground(normalCharacterCountColor);
                }
            }
        });
    }

    /**
     * Set the maximum message length.
     * This method is fully EDT-safe.
     * @param maximumLength The maximum message length
     */
    public void setMaximumMessageLength(int maximumLength) {
        if (maximumLength < 1) {
            throw new IllegalArgumentException("Maximum length must be at least 1");
        }

        final int maxLength = maximumLength;
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                maximumMessageLength = maxLength;
                characterCountLabel.setText(messageField.getText().length() + " / " + maximumMessageLength);
            }
        });
    }

    /**
     * Get the connection status text for testing.
     * @return The current connection status text
     */
    String getConnectionStatusText() {
        return statusLabel.getText();
    }

    /**
     * Get the character count text for testing.
     * @return The current character count text
     */
    String getCharacterCountText() {
        return characterCountLabel.getText();
    }

    /**
     * Get the character count foreground color for testing.
     * @return The current character count foreground color
     */
    Color getCharacterCountForeground() {
        return characterCountLabel.getForeground();
    }

    /**
     * Enable or disable messaging controls.
     * This method is EDT-safe - it schedules the update if not already on the EDT.
     * @param enabled true to enable, false to disable
     */
    public void setMessagingEnabled(boolean enabled) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                messageField.setEnabled(enabled);
                sendButton.setEnabled(enabled);
                if (enabled) {
                    messageField.requestFocusInWindow();
                }
            }
        });
    }

    /**
     * Enable or disable the disconnect button.
     * This method is EDT-safe - it schedules the update if not already on the EDT.
     * @param enabled true to enable, false to disable
     */
    public void setDisconnectEnabled(boolean enabled) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                disconnectButton.setEnabled(enabled);
            }
        });
    }

    /**
     * Enable or disable all room controls (message field, send and
     * leave/disconnect buttons). Enabling also focuses the message field.
     * This method is EDT-safe.
     * @param enabled true to enable, false to disable
     */
    @Override
    public void setRoomControlsEnabled(boolean enabled) {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                messageField.setEnabled(enabled);
                sendButton.setEnabled(enabled);
                disconnectButton.setEnabled(enabled);
                if (enabled) {
                    messageField.requestFocusInWindow();
                }
            }
        });
    }

    /**
     * Update the leave/disconnect button text and its tooltip.
     * Blank labels are rejected. This method is EDT-safe.
     * @param text The button text ("Close Room" or "Leave Room" in group use)
     */
    @Override
    public void setLeaveButtonText(final String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                updateLeaveButton(text);
            }
        });
    }

    /**
     * Apply button text plus a matching tooltip. Must run on the EDT.
     * @param text The non-blank button text
     */
    private void updateLeaveButton(String text) {
        disconnectButton.setText(text);
        if ("Close Room".equals(text)) {
            disconnectButton.setToolTipText("Close this chat room");
        } else if ("Leave Room".equals(text)) {
            disconnectButton.setToolTipText("Leave this chat room");
        }
    }

    /**
     * Replace the displayed online users. The supplied collection is copied,
     * never stored or mutated. Null/blank entries are dropped, duplicates are
     * removed case-insensitively, users sort case-insensitively with the host
     * first, and the title counts unique users.
     * This method is EDT-safe.
     * @param usernames The usernames to display (may be null for clear)
     * @param localUsername The local username for the (You) label
     * @param hostUsername The host username for the (Host) label
     */
    @Override
    public void setOnlineUsers(final Collection<String> usernames, final String localUsername,
            final String hostUsername) {
        final List<String> copy = (usernames == null) ? new ArrayList<String>()
                : new ArrayList<>(usernames);
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                rawOnlineUsernames.clear();
                rawOnlineUsernames.addAll(copy);
                onlineLocalUsername = localUsername;
                onlineHostUsername = hostUsername;
                rebuildOnlineUsers();
            }
        });
    }

    /**
     * Add one online user unless the name is blank or already present
     * (case-insensitively). Labels and ordering are preserved via rebuild.
     * This method is EDT-safe.
     * @param username The username to add
     * @param localUsername The local username for the (You) label
     * @param hostUsername The host username for the (Host) label
     */
    @Override
    public void addOnlineUser(final String username, final String localUsername,
            final String hostUsername) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                onlineLocalUsername = localUsername;
                onlineHostUsername = hostUsername;
                String trimmed = username.trim();
                boolean present = false;
                for (String existing : rawOnlineUsernames) {
                    if (existing.equalsIgnoreCase(trimmed)) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    rawOnlineUsernames.add(trimmed);
                }
                rebuildOnlineUsers();
            }
        });
    }

    /**
     * Remove one online user case-insensitively. Repeated or unknown removals
     * are harmless. This method is EDT-safe.
     * @param username The username to remove
     */
    @Override
    public void removeOnlineUser(final String username) {
        if (username == null) {
            return;
        }
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                String trimmed = username.trim();
                for (int i = rawOnlineUsernames.size() - 1; i >= 0; i--) {
                    if (rawOnlineUsernames.get(i).equalsIgnoreCase(trimmed)) {
                        rawOnlineUsernames.remove(i);
                    }
                }
                rebuildOnlineUsers();
            }
        });
    }

    /**
     * Clear the online-users list and reset the count to zero.
     * This method is EDT-safe.
     */
    @Override
    public void clearOnlineUsers() {
        runOnEdt(new Runnable() {
            @Override
            public void run() {
                rawOnlineUsernames.clear();
                rebuildOnlineUsers();
            }
        });
    }

    /**
     * Rebuild decorated display rows from raw usernames. Must run on the EDT.
     */
    private void rebuildOnlineUsers() {
        Map<String, String> unique = new LinkedHashMap<>();
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
        List<String> ordered = new ArrayList<>(unique.values());
        final String host = (onlineHostUsername == null) ? null : onlineHostUsername.trim();
        ordered.sort(new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                boolean aHost = host != null && !host.isEmpty() && a.equalsIgnoreCase(host);
                boolean bHost = host != null && !host.isEmpty() && b.equalsIgnoreCase(host);
                if (aHost != bHost) {
                    return aHost ? -1 : 1;
                }
                return String.CASE_INSENSITIVE_ORDER.compare(a, b);
            }
        });
        onlineUsersModel.clear();
        for (String name : ordered) {
            onlineUsersModel.addElement(decorateOnlineUser(name, onlineLocalUsername, host));
        }
        onlineUsersTitleLabel.setText("Online Users (" + ordered.size() + ")");
    }

    /**
     * Decorate one username with (You)/(Host) labels.
     * @param name The display name with original casing
     * @param localUsername The local username (matched case-sensitively)
     * @param hostUsername The host username (matched case-insensitively)
     * @return The decorated row
     */
    private static String decorateOnlineUser(String name, String localUsername, String hostUsername) {
        boolean isLocal = localUsername != null && localUsername.equals(name);
        boolean isHost = hostUsername != null && !hostUsername.trim().isEmpty()
                && name.equalsIgnoreCase(hostUsername.trim());
        if (isLocal && isHost) {
            return name + " (You, Host)";
        }
        if (isLocal) {
            return name + " (You)";
        }
        if (isHost) {
            return name + " (Host)";
        }
        return name;
    }

    /**
     * Helper method to run an action on the Event Dispatch Thread.
     * If already on the EDT, runs immediately; otherwise schedules via invokeLater.
     * @param action The action to run
     */
    private void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    @Override
    public java.awt.Component getParentComponent() {
        return this;
    }
}
