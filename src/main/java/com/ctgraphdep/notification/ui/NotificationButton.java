package com.ctgraphdep.notification.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract base class for notification buttons with consistent styling and behavior
 */
public abstract class NotificationButton {
    protected final String text;
    protected final Color color;
    protected final AtomicBoolean userResponded;
    protected static final Dimension BUTTON_SIZE = new Dimension(160, 35);

    /**
     * Constructs a notification button with specified text and color
     *
     * @param text The button text to display
     * @param color The background color of the button
     * @param userResponded Shared atomic boolean to track user response
     */
    protected NotificationButton(String text, Color color, AtomicBoolean userResponded) {
        this.text = text;
        this.color = color;
        this.userResponded = userResponded;
    }

    /**
     * Creates and configures a styled JButton with standard appearance and behavior
     *
     * @return The configured JButton instance
     */
    public JButton create() {
        JButton button = new JButton(text);
        button.setPreferredSize(BUTTON_SIZE);
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);

        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 2, true),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.darker());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        button.addActionListener(e -> {
            userResponded.set(true);
            handleAction(e);
        });

        return button;
    }

    /**
     * Abstract method to be implemented by subclasses to handle button click actions
     *
     * @param e The ActionEvent from the button click
     */
    protected abstract void handleAction(ActionEvent e);
}