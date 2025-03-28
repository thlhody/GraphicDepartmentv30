package com.ctgraphdep.enums;

public enum SyncStatusWorktime {
    ADMIN_EDITED,           // when the admin changes an entry
    USER_INPUT,            // this is what user saves user does not have the ability to edit these
    USER_DONE,            // when admin consolidates all user data
    ADMIN_BLANK,         // this should be added to the entries the admin removed so that when user checks it should remove that entry from their worktime
    USER_IN_PROCESS,    // lets admin know that this is not available for sync
    USER_EDITED
}