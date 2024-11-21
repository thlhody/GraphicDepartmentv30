package com.ctgraphdep.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyncFolderStatus {
    private boolean network;
    private boolean local;
    private String syncError;
    private int retryCount;
    private Long nextRetry;


}