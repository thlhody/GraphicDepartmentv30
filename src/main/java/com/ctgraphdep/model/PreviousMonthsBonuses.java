package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviousMonthsBonuses {
    @JsonProperty("month1")
    private BonusEntry month1;

    @JsonProperty("month2")
    private BonusEntry month2;

    @JsonProperty("month3")
    private BonusEntry month3;
}