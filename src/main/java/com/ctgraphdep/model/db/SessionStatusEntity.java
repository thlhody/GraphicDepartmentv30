package com.ctgraphdep.model.db;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing a user's session status in the database.
 * This is used specifically for the status page to avoid file concurrency issues.
 */
@Entity
@Table(name = "session_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionStatusEntity {

    @Id
    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    private String status;

    @Column(name = "last_active")
    private LocalDateTime lastActive;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}