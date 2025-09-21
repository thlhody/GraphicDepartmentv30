package com.ctgraphdep.notification.ui;

import com.ctgraphdep.utils.LoggerUtil;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

public class NotificationBackgroundFactory {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;
    private static final int CORNER_RADIUS = 20;
    private static final int CONTENT_PADDING = 20;

    public static BufferedImage createNotificationBackground(String title, String message) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Enable antialiasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Create background
        createBackgroundGradient(g2d);

        // Add logo and content
        addProgramLogo(g2d);
        addTitle(g2d, title);
        addContentArea(g2d, message);

        g2d.dispose();
        return image;
    }

    private static void createBackgroundGradient(Graphics2D g2d) {
        RoundRectangle2D roundedRectangle = new RoundRectangle2D.Double(
                0, 0, WIDTH, HEIGHT, CORNER_RADIUS * 2, CORNER_RADIUS * 2
        );

        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(20, 70, 130),
                (float) WIDTH / 2, (float) HEIGHT / 2, new Color(100, 160, 220),
                true
        );

        g2d.setPaint(gradient);
        g2d.fill(roundedRectangle);
    }

    private static void addProgramLogo(Graphics2D g2d) {
        try {
            URL logoUrl = NotificationBackgroundFactory.class.getResource("/static/icons/ct3logoicon.png");
            if (logoUrl != null) {
                BufferedImage logo = ImageIO.read(logoUrl);
                int logoWidth = 40;
                int logoHeight = 40;
                Image scaledLogo = logo.getScaledInstance(logoWidth, logoHeight, Image.SCALE_SMOOTH);
                // Move logo to top-right corner to avoid conflict with close button (now on left)
                int logoX = WIDTH - logoWidth - 15; // 15px margin from right edge
                int logoY = 15; // 15px margin from top edge
                g2d.drawImage(scaledLogo, logoX, logoY, null);
            }
        } catch (IOException e) {
            LoggerUtil.error(NotificationBackgroundFactory.class, "Failed to load program logo", e);
        }
    }

    private static void addTitle(Graphics2D g2d, String title) {
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));

        FontMetrics fm = g2d.getFontMetrics();
        int titleWidth = fm.stringWidth(title);
        g2d.drawString(title, (WIDTH - titleWidth) / 2, 40);
    }

    private static void addContentArea(Graphics2D g2d, String message) {
        int contentX = 30;
        int contentY = 70;
        int contentWidth = WIDTH - (2 * contentX);
        int contentHeight = 230;

        // Draw content background
        RoundRectangle2D contentRectangle = new RoundRectangle2D.Double(
                contentX, contentY, contentWidth, contentHeight,
                CORNER_RADIUS, CORNER_RADIUS
        );
        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.fill(contentRectangle);

        // Add warning icon
        try {
            URL warningUrl = NotificationBackgroundFactory.class.getResource("/static/icons/warning_sign.png");
            if (warningUrl != null) {
                BufferedImage warningSign = ImageIO.read(warningUrl);
                int signWidth = 50;
                int signHeight = 50;
                Image scaledWarningSign = warningSign.getScaledInstance(
                        signWidth, signHeight, Image.SCALE_SMOOTH
                );

                // Position warning sign at bottom right
                g2d.drawImage(
                        scaledWarningSign,
                        contentX + contentWidth - signWidth - CONTENT_PADDING,
                        contentY + contentHeight - signHeight - CONTENT_PADDING,
                        null
                );

                // Draw message text
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Arial", Font.PLAIN, 14));
                drawCenteredMultilineText(g2d, message, contentX, contentY, contentWidth, contentHeight);
            }
        } catch (IOException e) {
            LoggerUtil.error(NotificationBackgroundFactory.class, "Failed to load warning sign", e);
        }
    }

    private static void drawCenteredMultilineText(Graphics2D g2d, String text, int x, int y, int width, int height) {
        FontMetrics fm = g2d.getFontMetrics();
        int lineHeight = fm.getHeight();

        // Split text into lines (respecting explicit line breaks)
        String[] paragraphs = text.split("\n");
        List<String> lines = new ArrayList<>();

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                lines.add(""); // Preserve empty lines
                continue;
            }

            // Word wrap each paragraph
            String[] words = paragraph.trim().split("\\s+");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String testLine = currentLine + word + " ";
                if (fm.stringWidth(testLine) > width - (2 * CONTENT_PADDING)) {
                    lines.add(currentLine.toString().trim());
                    currentLine = new StringBuilder(word + " ");
                } else {
                    currentLine.append(word).append(" ");
                }
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString().trim());
            }
        }

        // Calculate starting Y position to center text block vertically
        int totalTextHeight = lines.size() * lineHeight;
        int startY = y + ((height - totalTextHeight) / 2) + fm.getAscent();

        // Draw each line centered horizontally
        for (String line : lines) {
            if (line.isEmpty()) {
                startY += lineHeight; // Add space for empty lines
                continue;
            }
            int lineWidth = fm.stringWidth(line);
            int startX = x + ((width - lineWidth) / 2);
            g2d.drawString(line, startX, startY);
            startY += lineHeight;
        }
    }
}