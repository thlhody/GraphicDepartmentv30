package com.ctgraphdep.merge.enums;

/**
 * Entity types supported by the merge system
 */
public enum EntityType {
    WORKTIME,           // WorkTimeTable - has USER_IN_PROCESS
    REGISTER,           // RegisterEntry - direct to USER_INPUT
    CHECK_REGISTER     // RegisterCheckEntry - direct to USER_INPUT

}
