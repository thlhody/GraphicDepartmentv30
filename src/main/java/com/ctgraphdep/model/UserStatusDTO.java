package com.ctgraphdep.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class UserStatusDTO {
    private String username;
    private Integer userId;
    private String name;
    private String status;
    private String lastActive;
}