// ============================================================================
// GENERIC ENTITY WRAPPER - Universal merge support for ALL entity types
// ============================================================================

package com.ctgraphdep.merge.wrapper;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.merge.engine.UniversalMergeEngine;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Generic wrapper that enables ANY entity to work with Universal Merge Engine.
 * Normalizes all old/unknown statuses to USER_INPUT.
 * Works with any entity that has an adminSync field (or equivalent).
 */
public class GenericEntityWrapper<T> implements UniversalMergeEngine.UniversalMergeableEntity {

    @Getter
    private final T entity;
    private final Function<T, String> statusGetter;
    private final BiConsumer<T, String> statusSetter;
    private final Function<T, Object> identifierGetter;

    public GenericEntityWrapper(T entity,
                                Function<T, String> statusGetter,
                                BiConsumer<T, String> statusSetter,
                                Function<T, Object> identifierGetter) {
        this.entity = entity;
        this.statusGetter = statusGetter;
        this.statusSetter = statusSetter;
        this.identifierGetter = identifierGetter;
    }

    @Override
    public String getUniversalStatus() {
        String rawStatus = statusGetter.apply(entity);
        return normalizeStatus(rawStatus);
    }

    @Override
    public void setUniversalStatus(String status) {
        String normalizedStatus = normalizeStatus(status);
        statusSetter.accept(entity, normalizedStatus);
    }

    @Override
    public Object getIdentifier() {
        return identifierGetter.apply(entity);
    }

    /**
     * Normalize ANY status to valid merge status.
     * All old/unknown statuses become USER_INPUT.
     */
    private String normalizeStatus(String status) {
        if (status == null) {
            return MergingStatusConstants.USER_INPUT;
        }

        // If already valid new format, keep as-is
        if (isValidMergeStatus(status)) {
            return status;
        }

        // Log normalization for debugging
        LoggerUtil.debug(GenericEntityWrapper.class, String.format("Normalizing unknown status '%s' to USER_INPUT for entity %s", status, getIdentifier()));

        // All old/unknown statuses → USER_INPUT
        return MergingStatusConstants.USER_INPUT;
    }

    /**
     * Check if status is valid in new merge system
     */
    private boolean isValidMergeStatus(String status) {
        return MergingStatusConstants.USER_INPUT.equals(status) ||
                MergingStatusConstants.USER_IN_PROCESS.equals(status) ||
                MergingStatusConstants.ADMIN_INPUT.equals(status) ||
                MergingStatusConstants.TEAM_INPUT.equals(status) ||
                MergingStatusConstants.ADMIN_FINAL.equals(status) ||
                MergingStatusConstants.TEAM_FINAL.equals(status) ||
                MergingStatusConstants.DELETE.equals(status) ||
                MergingStatusConstants.isTimestampedEditStatus(status);
    }
}