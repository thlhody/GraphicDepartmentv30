package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed previous months bonus data for comparison.
 * Contains full BonusEntry objects for display on register manager page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviousMonthsDetails {
    @JsonProperty("month1")
    private BonusEntry month1;

    @JsonProperty("month2")
    private BonusEntry month2;

    @JsonProperty("month3")
    private BonusEntry month3;
}
