package com.ctgraphdep.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationStatus {
    private boolean networkAvailable;
    private boolean offlineModeAvailable;
    private String mode;
}