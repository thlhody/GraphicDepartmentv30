// ============================================================================
// CHECK REGISTER WRAPPER FACTORY - Creates GenericEntityWrapper for CheckRegister entities
// ============================================================================

package com.ctgraphdep.checkregister.util;

import com.ctgraphdep.merge.wrapper.GenericEntityWrapper;
import com.ctgraphdep.model.RegisterCheckEntry;

/**
 * Factory for creating GenericEntityWrapper instances for RegisterCheckEntry entities.
 * Enables RegisterCheckEntry to work with the Universal Merge Engine.
 */
public class CheckRegisterWrapperFactory {

    /**
     * Create wrapper for RegisterCheckEntry
     * Identifier format: entryId_date
     */
    public static GenericEntityWrapper<RegisterCheckEntry> createWrapper(RegisterCheckEntry checkEntry) {
        return new GenericEntityWrapper<>(
                checkEntry,
                RegisterCheckEntry::getAdminSync,
                RegisterCheckEntry::setAdminSync,
                ce -> ce.getEntryId() + "_" + ce.getDate()
        );
    }

    /**
     * Create wrapper only if entity is not null
     * Returns null if input is null
     */
    public static GenericEntityWrapper<RegisterCheckEntry> createWrapperSafe(RegisterCheckEntry checkEntry) {
        return checkEntry != null ? createWrapper(checkEntry) : null;
    }
}