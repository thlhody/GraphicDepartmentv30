// ============================================================================
// REGISTER WRAPPER FACTORY - Creates GenericEntityWrapper for Register entities
// ============================================================================

package com.ctgraphdep.register.util;

import com.ctgraphdep.merge.wrapper.GenericEntityWrapper;
import com.ctgraphdep.model.RegisterEntry;

/**
 * Factory for creating GenericEntityWrapper instances for RegisterEntry entities.
 * Enables RegisterEntry to work with the Universal Merge Engine.
 */
public class RegisterWrapperFactory {

    /**
     * Create wrapper for RegisterEntry
     * Identifier format: userId_entryId_date
     */
    public static GenericEntityWrapper<RegisterEntry> createWrapper(RegisterEntry registerEntry) {
        return new GenericEntityWrapper<>(
                registerEntry,
                RegisterEntry::getAdminSync,
                re -> re.getUserId() + "_" + re.getEntryId() + "_" + re.getDate()
        );
    }

    /**
     * Create wrapper only if entity is not null
     * Returns null if input is null
     */
    public static GenericEntityWrapper<RegisterEntry> createWrapperSafe(RegisterEntry registerEntry) {
        return registerEntry != null ? createWrapper(registerEntry) : null;
    }
}