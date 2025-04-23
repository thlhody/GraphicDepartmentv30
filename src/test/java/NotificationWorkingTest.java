//import com.ctgraphdep.config.WorkCode;
//import com.ctgraphdep.model.WorkUsersSessionsStates;
//import com.ctgraphdep.service.*;
//import com.ctgraphdep.fileOperations.config.PathConfig;
//import com.ctgraphdep.tray.CTTTSystemTray;
//import org.junit.jupiter.api.*;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//import static org.mockito.Mockito.*;
//import static org.junit.jupiter.api.Assertions.*;
//
//import javax.swing.*;
//import java.awt.TrayIcon;
//import java.time.LocalDateTime;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//import java.util.ArrayList;
//import java.util.List;
//
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//public class NotificationWorkingTest {
//
//    @Mock private CTTTSystemTray systemTray;
//    @Mock private SessionMonitorService sessionMonitorService;
//    @Mock private UserSessionService userSessionService;
//    @Mock private PathConfig pathConfig;
//    @Mock private TrayIcon trayIcon;
//
//    private TestNotificationService notificationService;
//    private List<String> displayedNotifications;
//    private volatile CountDownLatch notificationLatch;
//    private WorkUsersSessionsStates mockSession;
//
//    // Custom NotificationService for testing
//    private class TestNotificationService extends SystemNotificationService {
//        public TestNotificationService(CTTTSystemTray systemTray,
//                                       SessionMonitorService sessionMonitorService,
//                                       UserSessionService userSessionService,
//                                       PathConfig pathConfig) {
//            super(systemTray, sessionMonitorService, userSessionService, pathConfig);
//        }
//
//        @Override
//        public void showNotificationDialog(String username, Integer userId, Integer finalMinutes,
//                                              String title, String message, int timeoutPeriod,
//                                              boolean isHourly, boolean isTempStop) {
//            // Record the notification
//            displayedNotifications.add(title + ": " + message);
//
//            // Notify that a notification was displayed
//            if (notificationLatch != null) {
//                notificationLatch.countDown();
//            }
//
//            // Log the notification for debugging
//            System.out.println("Notification displayed - Title: " + title + ", Message: " + message);
//
//            // Simulate auto-response after a delay
//            Timer timer = new Timer(50, e -> {
//                if (isTempStop) {
//                    sessionMonitorService.continueTempStop(username, userId);
//                } else if (isHourly) {
//                    sessionMonitorService.markSessionContinued(username, userId);
//                }
//            });
//            timer.setRepeats(false);
//            timer.start();
//        }
//    }
//
//    @BeforeEach
//    void setUp() {
//        MockitoAnnotations.openMocks(this);
//        displayedNotifications = new ArrayList<>();
//
//        // Initialize mock session
//        mockSession = new WorkUsersSessionsStates();
//        mockSession.setUsername("tudor");
//        mockSession.setUserId(2);
//        mockSession.setSessionStatus(WorkCode.WORK_ONLINE);
//        mockSession.setDayStartTime(LocalDateTime.now());
//
//        // Setup mocks
//        when(systemTray.getTrayIcon()).thenReturn(trayIcon);
//        when(userSessionService.getCurrentSession(anyString(), anyInt())).thenReturn(mockSession);
//
//        // Initialize test notification service
//        notificationService = new TestNotificationService(
//                systemTray,
//                sessionMonitorService,
//                userSessionService,
//                pathConfig
//        );
//    }
//
//    @Test
//    @Order(1)
//    @DisplayName("Test 1: Display all three types of notification")
//    void testAllNotificationTypes() throws InterruptedException {
//        notificationLatch = new CountDownLatch(3);
//
//        // Test session warning
//        notificationService.showSessionWarning("tudor", 2, 510);
//        Thread.sleep(100);
//
//        // Test hourly warning
//        notificationService.showHourlyWarning("tudor", 2, 570);
//        Thread.sleep(100);
//
//        // Test temporary stop warning
//        LocalDateTime tempStopStart = LocalDateTime.now().minusHours(2);
//        notificationService.showLongTempStopWarning("tudor", 2, tempStopStart);
//        Thread.sleep(100);
//
//        assertTrue(notificationLatch.await(1, TimeUnit.SECONDS),
//                "All notification should be displayed");
//        assertEquals(3, displayedNotifications.size(),
//                "Should have shown 3 notification");
//
//        // Verify notification content
//        assertTrue(displayedNotifications.stream()
//                        .anyMatch(n -> n.contains(WorkCode.SESSION_WARNING_MESSAGE)),
//                "Should show session warning");
//        assertTrue(displayedNotifications.stream()
//                        .anyMatch(n -> n.contains(WorkCode.HOURLY_WARNING_MESSAGE)),
//                "Should show hourly warning");
//        assertTrue(displayedNotifications.stream()
//                        .anyMatch(n -> n.contains("temporary stop")),
//                "Should show temp stop warning");
//    }
//
//    @Test
//    @Order(2)
//    @DisplayName("Test 2: Verify notification timers")
//    void testNotificationTimers() {
//        assertEquals(5000, WorkCode.ON_FOR_FIVE_MINUTES,
//                "5-minute warning should be 5000ms");
//        assertEquals(10000, WorkCode.ON_FOR_TEN_MINUTES,
//                "10-minute warning should be 10000ms");
//        assertEquals(2, WorkCode.CHECK_INTERVAL,
//                "Regular check interval should be 2 minutes");
//        assertEquals(5, WorkCode.HOURLY_CHECK_INTERVAL,
//                "Hourly check interval should be 5 minutes");
//    }
//
//    @Test
//    @Order(3)
//    @DisplayName("Test 3: Simulate full work day with temporary stop")
//    void testFullWorkDay() throws InterruptedException {
//        notificationLatch = new CountDownLatch(4);
//
//        // Work for 50ms (simulating 50 seconds)
//        Thread.sleep(50);
//
//        // Start temporary stop
//        mockSession.setSessionStatus(WorkCode.WORK_TEMPORARY_STOP);
//        mockSession.setLastTemporaryStopTime(LocalDateTime.now());
//
//        // First temp stop message
//        notificationService.showLongTempStopWarning("tudor", 2, mockSession.getLastTemporaryStopTime());
//        Thread.sleep(1000);
//
//        // Second temp stop message
//        notificationService.showLongTempStopWarning("tudor", 2, mockSession.getLastTemporaryStopTime());
//        Thread.sleep(1000);
//
//        // Resume work
//        mockSession.setSessionStatus(WorkCode.WORK_ONLINE);
//        mockSession.setTotalTemporaryStopMinutes(20);
//
//        // Show full day warning
//        notificationService.showSessionWarning("tudor", 2, 510);
//        Thread.sleep(1000);
//
//        // Show overtime warning
//        notificationService.showHourlyWarning("tudor", 2, 570);
//        Thread.sleep(1000);
//
//        assertTrue(notificationLatch.await(1, TimeUnit.SECONDS),
//                "All notification should be displayed");
//        assertEquals(4, displayedNotifications.size(),
//                "Should have shown 4 notification");
//
//        // Verify the notification sequence
//        verify(sessionMonitorService, times(2)).continueTempStop("tudor", 2);
//        verify(sessionMonitorService, times(1)).markSessionContinued("tudor", 2);
//    }
//}