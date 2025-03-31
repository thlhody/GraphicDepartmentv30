package com.ctgraphdep.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Concrete implementation of NotificationButton for use with ButtonFactory
 * Allows ActionListener to be injected for flexible button behavior
 */
public class FactoryNotificationButton extends NotificationButton {
    private final ActionListener actionListener;

    /**
     * Creates a notification button with specified text, color, and action listener
     *
     * @param text The button text to display
     * @param color The background color of the button
     * @param actionListener The action listener to execute when clicked
     * @param userResponded Shared atomic boolean to track user response
     */
    public FactoryNotificationButton(String text, Color color, ActionListener actionListener, AtomicBoolean userResponded) {
        super(text, color, userResponded);
        this.actionListener = actionListener;
    }

    @Override
    protected void handleAction(ActionEvent e) {
        // Delegate to the provided action listener
        if (actionListener != null) {
            actionListener.actionPerformed(e);
        }
    }

    /**
     * Override create method to add additional styling
     */
    @Override
    public JButton create() {
        JButton button = super.create();

        // Add factory-specific styling
        button.setMargin(new Insets(10, 20, 10, 20));
        button.setBorder(new EmptyBorder(5, 15, 5, 15));

        return button;
    }

    /**
     * Special factory method for creating outline styled buttons
     * Swaps background/foreground colors for outline style
     */
    public JButton createOutlineStyle() {
        JButton button = super.create();

        // Invert colors for outline style
        Color originalColor = button.getBackground();
        button.setBackground(Color.WHITE);
        button.setForeground(originalColor);

        // Add factory-specific styling
        button.setMargin(new Insets(10, 20, 10, 20));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(originalColor, 2, true),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));

        return button;
    }
}