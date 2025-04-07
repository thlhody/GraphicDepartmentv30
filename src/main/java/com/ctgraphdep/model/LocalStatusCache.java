package com.ctgraphdep.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class LocalStatusCache {
    private LocalDateTime lastUpdated;
    private Map<String, UserStatusInfo> userStatuses = new HashMap<>();
}