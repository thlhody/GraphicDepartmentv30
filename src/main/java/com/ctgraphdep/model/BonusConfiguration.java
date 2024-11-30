package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonusConfiguration {
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @JsonProperty("entriesPercentage")
    private Double entriesPercentage;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @JsonProperty("articlesPercentage")
    private Double articlesPercentage;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @JsonProperty("complexityPercentage")
    private Double complexityPercentage;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @JsonProperty("miscPercentage")
    private Double miscPercentage;

    @NotNull
    @DecimalMin("0.0")
    @JsonProperty("normValue")
    private Double normValue;

    @NotNull
    @DecimalMin("0.0")
    @JsonProperty("sumValue")
    private Double sumValue;

    @NotNull
    @DecimalMin("0.0")
    @JsonProperty("miscValue")
    private Double miscValue;

    // Static factory method for default configuration
    public static BonusConfiguration getDefaultConfig() {
        return BonusConfiguration.builder()
                .entriesPercentage(0.20)
                .articlesPercentage(0.15)
                .complexityPercentage(0.40)
                .miscPercentage(0.25)
                .normValue(1.20)
                .sumValue(1343.0)
                .miscValue(1.5)
                .build();
    }

    // Validate that percentages sum to 1.0
    public boolean isValid() {
        double sum = entriesPercentage + articlesPercentage + complexityPercentage + miscPercentage;
        return Math.abs(sum - 1.0) < 0.0001;
    }
}