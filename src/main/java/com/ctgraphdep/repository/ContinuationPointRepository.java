package com.ctgraphdep.repository;

import com.ctgraphdep.model.db.ContinuationPointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for continuation point database operations.
 */
@Repository
public interface ContinuationPointRepository extends JpaRepository<ContinuationPointEntity, Long> {

    /**
     * Find all continuation points for a user on a specific date that are still active
     */
    List<ContinuationPointEntity> findByUsernameAndSessionDateAndActiveTrue(String username, LocalDate sessionDate);

    /**
     * Find all active continuation points for a user
     */
    List<ContinuationPointEntity> findByUsernameAndActiveTrue(String username);

    /**
     * Check if a user has an unresolved continuation point of a specific type
     */
    boolean existsByUsernameAndTypeAndActiveTrueAndResolvedFalse(String username, String type);

    /**
     * Find all continuation points for a date range that are still active
     */
    List<ContinuationPointEntity> findBySessionDateBetweenAndActiveTrue(LocalDate startDate, LocalDate endDate);

    /**
     * Find all continuation points of a specific type for a user
     */
    List<ContinuationPointEntity> findByUsernameAndType(String username, String type);

    /**
     * Count the number of active continuation points of a specific type for a user on a date
     */
    long countByUsernameAndSessionDateAndTypeAndActiveTrue(String username, LocalDate sessionDate, String type);

    /**
     * Delete all resolved continuation points older than a specific date
     */
    void deleteBySessionDateBeforeAndResolvedTrue(LocalDate cutoffDate);
}