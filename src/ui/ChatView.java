package ui;

import model.Message;
import model.SystemMessage;

import javax.swing.*;
import java.awt.Component;
import java.util.Collection;

public interface ChatView {
    JTextField getMessageField();
    JButton getSendButton();
    JButton getDisconnectButton();

    void appendLocalMessage(Message message);
    void appendRemoteMessage(Message message);
    void appendSystemMessage(String message);

    void setMessagingEnabled(boolean enabled);
    void setDisconnectEnabled(boolean enabled);
    void setConnectionStatus(String status);
    void updateCharacterCount(int currentLength);
    void setMaximumMessageLength(int maximumLength);

    Component getParentComponent();

    // Step 9C-2 group-room presentation. Model types only; no networking.

    void appendGroupMessage(Message message, String localUsername);

    void appendTrustedSystemMessage(SystemMessage message);

    void setOnlineUsers(Collection<String> usernames, String localUsername, String hostUsername);

    void addOnlineUser(String username, String localUsername, String hostUsername);

    void removeOnlineUser(String username);

    void clearOnlineUsers();

    void setRoomStatus(String status);

    void setRoomControlsEnabled(boolean enabled);

    void setLeaveButtonText(String text);
}
