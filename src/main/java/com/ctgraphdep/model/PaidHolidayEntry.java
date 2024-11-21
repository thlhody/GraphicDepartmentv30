package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaidHolidayEntry {
    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("employeeId")
    private Integer employeeId;

    @JsonProperty("schedule")
    private Integer schedule;

    @JsonProperty("paidHolidayDays")
    private Integer paidHolidayDays;

    // Factory method to create from User
    public static PaidHolidayEntry fromUser(User user) {
        return PaidHolidayEntry.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .employeeId(user.getEmployeeId())
                .schedule(user.getSchedule())
                .paidHolidayDays(0) // Default to 0 days
                .build();
    }
}