package controller;

import javax.swing.JOptionPane;
import java.awt.Component;

public class SwingDisconnectConfirmation implements DisconnectConfirmation {
    @Override
    public boolean confirm(Component parent) {
        int choice = JOptionPane.showConfirmDialog(
            parent,
            "Leave this chat?",
            "Disconnect",
            JOptionPane.YES_NO_OPTION
        );
        return choice == JOptionPane.YES_OPTION;
    }
}
