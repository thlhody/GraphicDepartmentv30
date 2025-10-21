// ============================================================================
// WORKTIME WRAPPER FACTORY - Creates GenericEntityWrapper for WorkTime entities
// ============================================================================

package com.ctgraphdep.worktime.util;

import com.ctgraphdep.merge.wrapper.GenericEntityWrapper;
import com.ctgraphdep.model.WorkTimeTable;

public class WorktimeWrapperFactory {

    // Create wrapper for WorkTimeTable
    public static GenericEntityWrapper<WorkTimeTable> createWrapper(WorkTimeTable workTime) {
        return new GenericEntityWrapper<>(
                workTime,
                WorkTimeTable::getAdminSync,
                wt -> wt.getUserId() + "_" + wt.getWorkDate()
        );
    }

    // Create wrapper only if entity is not null
    public static GenericEntityWrapper<WorkTimeTable> createWrapperSafe(WorkTimeTable workTime) {
        return workTime != null ? createWrapper(workTime) : null;
    }
}