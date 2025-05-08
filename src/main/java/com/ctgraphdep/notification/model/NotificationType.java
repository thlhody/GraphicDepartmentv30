package com.ctgraphdep.notification.model;

import com.ctgraphdep.config.WorkCode;
import lombok.Getter;

/**
 * Enum of notification types
 */
@Getter
public enum NotificationType {
    SCHEDULE_END(WorkCode.SCHEDULE_END_TYPE, 7),
    HOURLY_WARNING(WorkCode.HOURLY_TYPE, 6),
    TEMP_STOP(WorkCode.TEMP_STOP_TYPE, 7),
    START_DAY(WorkCode.START_DAY_TYPE, 9),
    RESOLUTION(WorkCode.RESOLUTION_REMINDER_TYPE, 8),
    TEST(WorkCode.TEST_TYPE, 10);

    /**
     * -- GETTER --
     *  Gets the type identifier
     */
    private final String typeId;
    /**
     * -- GETTER --
     *  Gets the default priority for this notification type
     */
    private final int defaultPriority;

    NotificationType(String typeId, int defaultPriority) {
        this.typeId = typeId;
        this.defaultPriority = defaultPriority;
    }

}