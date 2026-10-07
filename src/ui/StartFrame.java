package ui;

import controller.ClientGroupChatController;
import controller.HostGroupChatController;
import network.ChatServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StartFrame class representing the startup UI for the LAN Chat application.
 * This frame allows users to enter their username and choose to create or join a chat.
 *
 * <p>Final group-room routing (Step 9C-4A):
 * Create Chat: StartFrame -&gt; ChatFrame -&gt; HostGroupChatController -&gt; GroupChatServer.
 * Join Chat: StartFrame -&gt; ChatFrame -&gt; ClientGroupChatController
 * -&gt; GroupChatClientSession -&gt; GroupChatServer.
 * The old one-to-one ChatController class is retained for regression
 * compatibility but is no longer used by either launch path.
 *
 * <p>Known expected limitation: no chat history exists, so Clients joining
 * later do not receive Host messages sent before they joined.
 */
public class StartFrame extends JFrame {

    private enum SessionMode {
        NONE,
        HOST,
        CLIENT
    }

    private JTextField userNameField;
    private JTextField hostIPField;
    private JButton createChatButton;
    private JButton joinChatButton;
    private JLabel statusLabel;
    private final int chatPort;
    private volatile ChatFrame currentChatFrame;
    private volatile HostGroupChatController hostGroupController;
    private volatile ClientGroupChatController clientGroupController;
    private volatile SessionMode sessionMode = SessionMode.NONE;
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final AtomicLong sessionGeneration = new AtomicLong(0);

    /**
     * Production constructor for StartFrame using the default chat port.
     * Sets up the startup UI with username, host IP fields, and action buttons.
     */
    public StartFrame() {
        this(ChatServer.DEFAULT_PORT);
    }

    /**
     * Package-private constructor with a configurable chat port for tests.
     * Production always uses {@link ChatServer#DEFAULT_PORT}.
     * @param chatPort The port used for Host and Client group controllers
     */
    StartFrame(int chatPort) {
        super("LAN Chat");
        this.chatPort = chatPort;
        initializeUI();
    }

    /**
     * Initialize the start frame UI components.
     */
    private void initializeUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(400, 350);
        setLocationRelativeTo(null);

        // Add window listener for proper shutdown
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestApplicationExit();
            }
        });

        // Create main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Create form panel for input fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // User Name label and field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("User Name:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        userNameField = new JTextField(20);
        formPanel.add(userNameField, gbc);

        // Host IP label and field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Host IP:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        hostIPField = new JTextField(20);
        hostIPField.setText("localhost");
        formPanel.add(hostIPField, gbc);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        createChatButton = new JButton("Create Chat");
        joinChatButton = new JButton("Join Chat");

        buttonPanel.add(createChatButton);
        buttonPanel.add(joinChatButton);

        // Status label
        statusLabel = new JLabel(" ");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // Add components to main panel
        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Create a panel for status label at the bottom
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.NORTH);

        // Add main panel to frame
        add(mainPanel);

        // Add action listeners
        createChatButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleCreateChat();
            }
        });

        joinChatButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleJoinChat();
            }
        });
    }

    /**
     * Handle the "Create Chat" button click.
     * Opens the Host room immediately without waiting for Clients.
     */
    private void handleCreateChat() {
        if (shutdownInProgress.get()) {
            return;
        }

        String userName = userNameField.getText().trim();

        if (userName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a user name.",
                "Validation Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (sessionMode != SessionMode.NONE) {
            JOptionPane.showMessageDialog(this,
                "A chat session is already active.",
                "Conflict",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        setStartupControlsEnabled(false);

        final long generation = sessionGeneration.incrementAndGet();
        final String finalUserName = userName;

        final ChatFrame chatFrame = new ChatFrame(finalUserName, "Host");
        currentChatFrame = chatFrame;
        HostGroupChatController controller = new HostGroupChatController(
            chatFrame,
            chatPort,
            finalUserName,
            new HostGroupChatController.HostGroupChatLifecycleListener() {
                @Override
                public void onHostRoomEnded(String reason, boolean initiatedLocally) {
                    handleHostRoomEnded(generation, reason);
                }
            });
        hostGroupController = controller;
        sessionMode = SessionMode.HOST;

        chatFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleChatFrameClosing();
            }
        });

        chatFrame.setVisible(true);
        this.setVisible(false);
    }

    /**
     * Handle the "Join Chat" button click.
     * Opens the Client room immediately; connection proceeds in the background.
     */
    private void handleJoinChat() {
        if (shutdownInProgress.get()) {
            return;
        }

        String userName = userNameField.getText().trim();
        String hostIP = hostIPField.getText().trim();

        if (userName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a user name.",
                "Validation Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (hostIP.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a host IP address.",
                "Validation Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (sessionMode != SessionMode.NONE) {
            JOptionPane.showMessageDialog(this,
                "A chat session is already active.",
                "Conflict",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        setStartupControlsEnabled(false);

        final long generation = sessionGeneration.incrementAndGet();
        final String finalUserName = userName;
        final String finalHostIP = hostIP;

        final ChatFrame chatFrame = new ChatFrame(finalUserName, "Client");
        currentChatFrame = chatFrame;
        ClientGroupChatController controller = new ClientGroupChatController(
            chatFrame,
            finalHostIP,
            chatPort,
            finalUserName,
            new ClientGroupChatController.ClientGroupChatLifecycleListener() {
                @Override
                public void onClientRoomEnded(String reason, boolean initiatedLocally) {
                    handleClientRoomEnded(generation, reason);
                }
            });
        clientGroupController = controller;
        sessionMode = SessionMode.CLIENT;

        chatFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleChatFrameClosing();
            }
        });

        chatFrame.setVisible(true);
        this.setVisible(false);
    }

    /**
     * Handle Host room end: verify generation, then navigate on the EDT.
     * @param generation The session generation captured at launch
     * @param reason The terminal reason for StartFrame status
     */
    private void handleHostRoomEnded(long generation, final String reason) {
        if (generation != sessionGeneration.get()) {
            return;
        }
        final HostGroupChatController controller = hostGroupController;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (shutdownInProgress.get()) {
                    return;
                }
                if (controller != null) {
                    controller.close();
                }
                hostGroupController = null;
                clientGroupController = null;
                if (currentChatFrame != null) {
                    currentChatFrame.dispose();
                    currentChatFrame = null;
                }
                sessionMode = SessionMode.NONE;
                sessionGeneration.incrementAndGet();
                showStartScreen(reason);
            }
        });
    }

    /**
     * Handle Client room end: verify generation, then navigate on the EDT.
     * @param generation The session generation captured at launch
     * @param reason The terminal reason for StartFrame status
     */
    private void handleClientRoomEnded(long generation, final String reason) {
        if (generation != sessionGeneration.get()) {
            return;
        }
        final ClientGroupChatController controller = clientGroupController;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (shutdownInProgress.get()) {
                    return;
                }
                if (controller != null) {
                    controller.close();
                }
                hostGroupController = null;
                clientGroupController = null;
                if (currentChatFrame != null) {
                    currentChatFrame.dispose();
                    currentChatFrame = null;
                }
                sessionMode = SessionMode.NONE;
                sessionGeneration.incrementAndGet();
                showStartScreen(reason);
            }
        });
    }

    /**
     * Enable or disable the startup controls.
     * @param enabled true to enable, false to disable
     */
    private void setStartupControlsEnabled(boolean enabled) {
        userNameField.setEnabled(enabled);
        hostIPField.setEnabled(enabled);
        createChatButton.setEnabled(enabled);
        joinChatButton.setEnabled(enabled);
    }

    /**
     * Show the start screen with a status message. Runs on the EDT.
     * Disposes any lingering chat frame, resets status, re-enables controls
     * and preserves the entered username. The last Host IP is preserved
     * because it is useful for rejoining.
     * @param status The status text (blank falls back to Ready)
     */
    private void showStartScreen(final String status) {
        if (SwingUtilities.isEventDispatchThread()) {
            showStartScreenOnEdt(status);
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showStartScreenOnEdt(status);
                }
            });
        }
    }

    private void showStartScreenOnEdt(String status) {
        if (currentChatFrame != null) {
            currentChatFrame.dispose();
            currentChatFrame = null;
        }
        if (status == null || status.trim().isEmpty()) {
            statusLabel.setText("Ready");
        } else {
            statusLabel.setText(status);
        }
        setStartupControlsEnabled(true);
        this.setVisible(true);
        this.toFront();
        userNameField.requestFocusInWindow();
    }

    /**
     * Close the active session controller based on the session mode.
     * Idempotent; never notifies lifecycle listeners itself.
     */
    private void closeActiveSession() {
        HostGroupChatController hostController = null;
        ClientGroupChatController clientController = null;
        if (sessionMode == SessionMode.HOST) {
            hostController = hostGroupController;
            hostGroupController = null;
        } else if (sessionMode == SessionMode.CLIENT) {
            clientController = clientGroupController;
            clientGroupController = null;
        } else {
            return;
        }
        if (hostController != null) {
            try {
                hostController.close();
            } catch (Exception e) {
                // Ignore shutdown errors.
            }
        }
        if (clientController != null) {
            try {
                clientController.close();
            } catch (Exception e) {
                // Ignore shutdown errors.
            }
        }
    }

    /**
     * Handle a group ChatFrame close request: confirm application exit.
     */
    private void handleChatFrameClosing() {
        requestApplicationExit();
    }

    /**
     * Ask for application-exit confirmation (single dialog site for both
     * StartFrame and ChatFrame close controls; shutdown itself is atomic).
     */
    private void requestApplicationExit() {
        if (shutdownInProgress.get()) {
            return;
        }
        Component parent = (currentChatFrame != null) ? currentChatFrame : this;
        int choice = JOptionPane.showConfirmDialog(
            parent,
            "Close the application?",
            "Exit",
            JOptionPane.YES_NO_OPTION
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        shutdownGracefully();
    }

    /**
     * Shut down gracefully: invalidate generations first so lifecycle
     * callbacks are ignored, notify peers, then close everything and exit.
     * Idempotent and safe to call multiple times.
     */
    private void shutdownGracefully() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return;
        }
        sessionGeneration.incrementAndGet();
        if (sessionMode == SessionMode.HOST && hostGroupController != null) {
            try {
                hostGroupController.closeRoomForApplicationExit();
            } catch (Exception e) {
                // Ignore shutdown errors.
            }
        } else if (sessionMode == SessionMode.CLIENT && clientGroupController != null) {
            try {
                clientGroupController.leaveRoomForApplicationExit();
            } catch (Exception e) {
                // Ignore shutdown errors.
            }
        }
        closeActiveSession();
        if (currentChatFrame != null) {
            currentChatFrame.dispose();
            currentChatFrame = null;
        }
        dispose();
        System.exit(0);
    }

    /**
     * Shutdown the application cleanly.
     * This method is idempotent and safe to call multiple times.
     */
    private void shutdownApplication() {
        shutdownGracefully();
    }

    /**
     * Package-private bound room port for tests.
     * @return The Host controller port in HOST mode, otherwise -1
     */
    int getActiveRoomPortForTest() {
        if (sessionMode == SessionMode.HOST && hostGroupController != null) {
            try {
                return hostGroupController.getPort();
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Package-private current chat frame for tests.
     * @return The active ChatFrame, or null
     */
    ChatFrame getCurrentChatFrameForTest() {
        return currentChatFrame;
    }

    /**
     * Package-private Host controller for tests.
     * @return The active HostGroupChatController, or null
     */
    HostGroupChatController getHostGroupControllerForTest() {
        return hostGroupController;
    }

    /**
     * Package-private Client controller for tests.
     * @return The active ClientGroupChatController, or null
     */
    ClientGroupChatController getClientGroupControllerForTest() {
        return clientGroupController;
    }

    /**
     * Package-private status text for tests.
     * @return The current status label text
     */
    String getStatusTextForTest() {
        return statusLabel.getText();
    }

    /**
     * Get the user name field.
     * @return The JTextField for username input
     */
    public JTextField getUserNameField() {
        return userNameField;
    }

    /**
     * Get the host IP field.
     * @return The JTextField for host IP input
     */
    public JTextField getHostIPField() {
        return hostIPField;
    }

    /**
     * Get the create chat button.
     * @return The JButton for creating a chat
     */
    public JButton getCreateChatButton() {
        return createChatButton;
    }

    /**
     * Get the join chat button.
     * @return The JButton for joining a chat
     */
    public JButton getJoinChatButton() {
        return joinChatButton;
    }
}
