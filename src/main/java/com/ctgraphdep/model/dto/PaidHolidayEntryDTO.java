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

    @JsonProperty("username")
    private String username;

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
                .username(user.getUsername())
                .name(user.getName())
                .employeeId(user.getEmployeeId())
                .schedule(user.getSchedule())
                .paidHolidayDays(user.getPaidHolidayDays())
                .build();
    }
}