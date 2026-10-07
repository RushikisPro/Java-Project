import ui.StartFrame;

import javax.swing.*;

/**
 * Main class for the LAN Chat application.
 * This class launches the Swing application and opens the StartFrame.
 */
public class Main {
    
    /**
     * Main entry point for the LAN Chat application.
     * Initializes the Swing application on the Event Dispatch Thread.
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        // Swing components must be created on the Event Dispatch Thread
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    // Set system look and feel for native appearance
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    // If system look and feel fails, use default
                    e.printStackTrace();
                }
                
                // Create and display the start frame
                StartFrame startFrame = new StartFrame();
                startFrame.setVisible(true);
            }
        });
    }
}
