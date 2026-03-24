package com.axiseditor;

import com.axiseditor.ui.MainWindow;

import javax.swing.*;

/**
 * Axis Editor and IDE - Main Entry Point
 * Phase 1: Project Setup & Basic UI
 */
public class Main {

    public static void main(String[] args) {
        // Run UI on Event Dispatch Thread (Swing requirement)
        SwingUtilities.invokeLater(() -> {
            try {
                // Use system look and feel for a native appearance
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Fall back to default look and feel
                System.err.println("Could not set system look and feel: " + e.getMessage());
            }

            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
