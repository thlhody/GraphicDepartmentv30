package com.ctgraphdep.fileOperations.model;

/**
 * Direction of a file synchronization operation.
 * Indicates which way data was transferred between local and network storage.
 */
public enum SyncDirection {
    /**
     * Data was synchronized from local to network storage
     */
    LOCAL_TO_NETWORK,

    /**
     * Data was synchronized from network to local storage
     */
    NETWORK_TO_LOCAL,

    /**
     * No synchronization occurred (e.g., both locations identical or both missing)
     */
    NONE,

    /**
     * Synchronization failed with an error
     */
    ERROR
}