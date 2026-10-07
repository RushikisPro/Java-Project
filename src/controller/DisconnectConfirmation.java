package controller;

import java.awt.Component;

@FunctionalInterface
public interface DisconnectConfirmation {
    boolean confirm(Component parent);
}
