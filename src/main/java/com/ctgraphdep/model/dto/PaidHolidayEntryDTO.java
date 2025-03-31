package com.ctgraphdep.model.dto;

import com.ctgraphdep.model.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaidHolidayEntryDTO {
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
    public static PaidHolidayEntryDTO fromUser(User user) {
        return PaidHolidayEntryDTO.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .employeeId(user.getEmployeeId())
                .schedule(user.getSchedule())
                .paidHolidayDays(0) // Default to 0 days
                .build();
    }
}