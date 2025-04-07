package com.ctgraphdep.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FlagInfo {
    private final String username;
    private final String status;
    private final LocalDateTime timestamp;

    public FlagInfo(String username, String status, LocalDateTime timestamp) {
        this.username = username;
        this.status = status;
        this.timestamp = timestamp;
    }
}
