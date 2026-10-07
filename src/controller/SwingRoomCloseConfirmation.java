package controller;

import javax.swing.JOptionPane;
import java.awt.Component;

public class SwingRoomCloseConfirmation implements RoomCloseConfirmation {
    @Override
    public boolean confirm(Component parent) {
        int choice = JOptionPane.showConfirmDialog(
            parent,
            "Close this chat room for everyone?",
            "Close Room",
            JOptionPane.YES_NO_OPTION
        );
        return choice == JOptionPane.YES_OPTION;
    }
}
