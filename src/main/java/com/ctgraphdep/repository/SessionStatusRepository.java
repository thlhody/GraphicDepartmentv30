package com.ctgraphdep.repository;

import com.ctgraphdep.model.db.SessionStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for database operations on session status entities.
 */
@Repository
public interface SessionStatusRepository extends JpaRepository<SessionStatusEntity, String> {

    /**
     * Find session by username and userId
     */
    Optional<SessionStatusEntity> findByUsername(String username);

    /**
     * Find all sessions with a specific status
     */
    List<SessionStatusEntity> findByStatusIn(List<String> statuses);

    /**
     * Find all sessions ordered by status and name
     */
    List<SessionStatusEntity> findAllByOrderByStatusAscNameAsc();
}