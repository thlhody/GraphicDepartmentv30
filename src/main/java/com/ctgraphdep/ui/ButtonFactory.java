package com.ctgraphdep.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Factory for creating consistently styled buttons throughout the application
 */
public class ButtonFactory {

    // Button types
    public static final int BUTTON_PRIMARY = 1;  // Green - for positive actions (save, start, continue)
    public static final int BUTTON_SECONDARY = 2; // Blue - for neutral actions (open, view)
    public static final int BUTTON_DANGER = 3;   // Red - for negative actions (cancel, skip, end)
    public static final int BUTTON_NEUTRAL = 4;  // Gray - for dismissive actions

    // Button styles (though we'll make filled the default now)
    public static final int STYLE_OUTLINE = 1;  // Outline button with white background
    public static final int STYLE_FILLED = 2;   // Filled button with colored background

    /**
     * Creates a button with consistent styling based on type and style
     */
    public static JButton createButton(String text, int type, int style,
                                       ActionListener actionListener,
                                       AtomicBoolean respondedFlag) {

        // Create a custom JButton with rounded corners
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Set background color based on button state
                if (getModel().isPressed()) {
                    g2.setColor(getBackground().darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(getBackground().brighter());
                } else {
                    g2.setColor(getBackground());
                }

                // Fill button with rounded rectangle
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));

                // Draw text
                g2.setColor(getForeground());
                g2.setFont(getFont());

                // This is the key to properly centered text
                FontMetrics fm = g2.getFontMetrics();
                Rectangle2D stringBounds = fm.getStringBounds(getText(), g2);

                int textX = (int) ((getWidth() - stringBounds.getWidth()) / 2);
                int textY = (int) ((getHeight() - stringBounds.getHeight()) / 2 + fm.getAscent());

                g2.drawString(getText(), textX, textY);
                g2.dispose();
            }

            @Override
            public boolean contains(int x, int y) {
                return new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12).contains(x, y);
            }
        };

        // Common button settings
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(140, 40));
        button.setMinimumSize(new Dimension(140, 40));
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setMargin(new Insets(10, 20, 10, 20));
        button.setBorder(new EmptyBorder(5, 15, 5, 15));

        // Get color based on type
        Color color = getColorForType(type);

        // Apply style
        if (style == STYLE_OUTLINE) {
            // This is the style you don't want anymore
            button.setBackground(Color.WHITE);
            button.setForeground(color);
        } else { // STYLE_FILLED - the style you want
            button.setBackground(color);
            button.setForeground(Color.WHITE);
        }

        // Add action listener with response flag handling
        button.addActionListener(e -> {
            if (respondedFlag != null) {
                respondedFlag.set(true);
            }
            if (actionListener != null) {
                actionListener.actionPerformed(e);
            }
        });

        return button;
    }

    /**
     * Simplified version without response flag
     */
    public static JButton createButton(String text, int type, int style,
                                       ActionListener actionListener) {
        return createButton(text, type, style, actionListener, null);
    }

    /**
     * Further simplified version defaulting to filled style
     */
    public static JButton createButton(String text, int type,
                                       ActionListener actionListener) {
        return createButton(text, type, STYLE_FILLED, actionListener, null);
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