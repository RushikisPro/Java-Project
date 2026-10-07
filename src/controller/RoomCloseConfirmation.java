package controller;

import java.awt.Component;

@FunctionalInterface
public interface RoomCloseConfirmation {
    boolean confirm(Component parent);
}
