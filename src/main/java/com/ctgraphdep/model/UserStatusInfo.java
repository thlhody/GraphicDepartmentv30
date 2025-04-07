package com.ctgraphdep.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class UserStatusInfo {
    private String username;
    private Integer userId;
    private String name;
    private String status;
    private LocalDateTime lastActive;
}