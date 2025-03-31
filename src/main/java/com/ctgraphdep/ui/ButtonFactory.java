package com.ctgraphdep.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Factory for creating consistently styled buttons throughout the application
 * Uses the FactoryNotificationButton class for implementation
 */
public class ButtonFactory {

    // Button types
    public static final int BUTTON_PRIMARY = 1;  // Green - for positive actions (save, start, continue)
    public static final int BUTTON_SECONDARY = 2; // Blue - for neutral actions (open, view)
    public static final int BUTTON_DANGER = 3;   // Red - for negative actions (cancel, skip, end)
    public static final int BUTTON_NEUTRAL = 4;  // Gray - for dismissive actions

    // Button styles
    public static final int STYLE_OUTLINE = 1;  // Outline button with white background
    public static final int STYLE_FILLED = 2;   // Filled button with colored background

    /**
     * Creates a button with consistent styling based on type and style
     */
    public static JButton createButton(String text, int type, int style,
                                       ActionListener actionListener,
                                       AtomicBoolean respondedFlag) {
        // Get color based on type
        Color color = getColorForType(type);

        // Create button using FactoryNotificationButton
        FactoryNotificationButton button = new FactoryNotificationButton(
                text, color, actionListener, respondedFlag);

        // Return appropriate button style
        if (style == STYLE_OUTLINE) {
            return button.createOutlineStyle();
        } else {
            return button.create();
        }
    }

    /**
     * Get color based on button type
     */
    private static Color getColorForType(int type) {
        return switch (type) {
            case BUTTON_PRIMARY -> new Color(0, 153, 51);     // Green
            case BUTTON_SECONDARY -> new Color(51, 122, 183); // Blue
            case BUTTON_DANGER -> new Color(204, 51, 0);      // Red
            case BUTTON_NEUTRAL -> new Color(108, 117, 125);  // Gray
            default -> new Color(51, 51, 51);                 // Dark gray (fallback)
        };
    }
}