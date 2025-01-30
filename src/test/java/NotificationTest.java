//
//import com.ctgraphdep.config.WorkCode;
//import com.ctgraphdep.utils.NotificationBackgroundUtility;
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.Test;
//
//import javax.imageio.ImageIO;
//import java.awt.image.BufferedImage;
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//public class NotificationTest {
//
//    private static final String TEST_OUTPUT_DIR = "D:/";
//
//    @BeforeAll
//    static void setup() throws IOException {
//        // Create output directory if it doesn't exist
//        Path outputPath = Paths.get(TEST_OUTPUT_DIR);
//        Files.createDirectories(outputPath);
//    }
//
//    @Test
//    void testCreateNotificationBackground() throws IOException {
//        // Arrange
//        String title = "END SCHEDULE NOTICE";
//        String message = WorkCode.SESSION_WARNING_MESSAGE;
//        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
//        String filename = String.format("notification_test_%s.png", timestamp);
//        Path outputPath = Paths.get(TEST_OUTPUT_DIR, filename);
//
//        // Act
//        BufferedImage notificationImage = NotificationBackgroundUtility.createNotificationBackground(title, message);
//
//        // Save the image
//        File outputFile = outputPath.toFile();
//        boolean saved = ImageIO.write(notificationImage, "PNG", outputFile);
//
//        // Assert
//        assertTrue(saved, "Image should be saved successfully");
//        assertTrue(outputFile.exists(), "Output file should exist");
//        assertTrue(outputFile.length() > 0, "Output file should not be empty");
//
//        // Verify image dimensions
//        assertEquals(600, notificationImage.getWidth(), "Image should be 600px wide");
//        assertEquals(400, notificationImage.getHeight(), "Image should be 400px high");
//
//        // Log the file location for manual inspection
//        System.out.println("Test notification image saved to: " + outputFile.getAbsolutePath());
//    }
//
//    @Test
//    void testCreateNotificationBackgroundWithCustomMessage() throws IOException {
//        // Arrange
//        String title = "CUSTOM NOTICE";
//        String message = "This is a custom test message\nwith multiple lines\nto test text wrapping";
//        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
//        String filename = String.format("notification_custom_%s.png", timestamp);
//        Path outputPath = Paths.get(TEST_OUTPUT_DIR, filename);
//
//        // Act
//        BufferedImage notificationImage = NotificationBackgroundUtility.createNotificationBackground(title, message);
//
//        // Save the image
//        File outputFile = outputPath.toFile();
//        boolean saved = ImageIO.write(notificationImage, "PNG", outputFile);
//
//        // Assert
//        assertTrue(saved, "Image should be saved successfully");
//        assertTrue(outputFile.exists(), "Output file should exist");
//        assertTrue(outputFile.length() > 0, "Output file should not be empty");
//
//        // Log the file location for manual inspection
//        System.out.println("Custom notification image saved to: " + outputFile.getAbsolutePath());
//    }
//
//    @Test
//    void testImageProperties() {
//        // Arrange
//        String title = "TEST NOTICE";
//        String message = "Test message";
//
//        // Act
//        BufferedImage notificationImage = NotificationBackgroundUtility.createNotificationBackground(title, message);
//
//        // Assert
//        assertNotNull(notificationImage, "Image should not be null");
//        assertEquals(BufferedImage.TYPE_INT_ARGB, notificationImage.getType(),
//                "Image should be ARGB type for transparency support");
//        assertEquals(600, notificationImage.getWidth(), "Image width should be 600px");
//        assertEquals(400, notificationImage.getHeight(), "Image height should be 400px");
//    }
//}