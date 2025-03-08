package com.ctgraphdep.model.db;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity to track session continuation points and their resolution.
 * This is used to handle cases where users continue working beyond their schedule
 * and to resolve past sessions the next day.
 */
@Entity
@Table(name = "continuation_points")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContinuationPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    /**
     * Type of continuation point:
     * - SCHEDULE_END - From schedule end notification
     * - HOURLY - From hourly warning notification
     * - TEMP_STOP - From temporary stop continuation
     * - MIDNIGHT_END - Automatic end at midnight
     */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * Whether this continuation point is still active
     * Set to false when resolved or superseded by a newer point
     */
    @Column(name = "active", nullable = false)
    private boolean active;

    /**
     * Whether this continuation point has been resolved
     */
    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    /**
     * Username of the user who resolved this continuation point
     */
    @Column(name = "resolved_by")
    private String resolvedBy;

    /**
     * When this continuation point was resolved
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Overtime minutes that were applied when resolving
     */
    @Column(name = "overtime_applied")
    private Integer overtimeApplied;
}