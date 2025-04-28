package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents check values for different check types
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckValuesEntry {

    @JsonProperty("createdAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;

    @NotNull
    @DecimalMin("1.0")
    @DecimalMax("10.0")
    @JsonProperty("workUnitsPerHour")
    @Builder.Default
    private Double workUnitsPerHour = 4.5;

    @NotNull
    @DecimalMin("1.0")
    @DecimalMax("5.0")
    @JsonProperty("layoutValue")
    @Builder.Default
    private Double layoutValue = 1.0;

    @NotNull
    @DecimalMin("0.25")
    @DecimalMax("2.0")
    @JsonProperty("kipstaLayoutValue")
    @Builder.Default
    private Double kipstaLayoutValue = 0.25;

    @NotNull
    @DecimalMin("0.25")
    @DecimalMax("2.0")
    @JsonProperty("layoutChangesValue")
    @Builder.Default
    private Double layoutChangesValue = 0.25;

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("1.0")
    @JsonProperty("gptArticlesValue")
    @Builder.Default
    private Double gptArticlesValue = 0.1;

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("1.0")
    @JsonProperty("gptFilesValue")
    @Builder.Default
    private Double gptFilesValue = 0.1;

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("1.0")
    @JsonProperty("productionValue")
    @Builder.Default
    private Double productionValue = 0.1;

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("1.0")
    @JsonProperty("reorderValue")
    @Builder.Default
    private Double reorderValue = 0.1;

    @NotNull
    @DecimalMin("0.3")
    @DecimalMax("3.0")
    @JsonProperty("sampleValue")
    @Builder.Default
    private Double sampleValue = 0.3;

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("1.0")
    @JsonProperty("omsProductionValue")
    @Builder.Default
    private Double omsProductionValue = 0.1;

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("1.0")
    @JsonProperty("kipstaProductionValue")
    @Builder.Default
    private Double kipstaProductionValue = 0.1;

    /**
     * Creates a default check values entry
     * @return A new check values entry with default values
     */
    public static CheckValuesEntry createDefault() {
        return CheckValuesEntry.builder()
                .createdAt(LocalDateTime.now())
                .workUnitsPerHour(4.0)
                .layoutValue(1.0)
                .kipstaLayoutValue(0.25)
                .layoutChangesValue(0.25)
                .gptArticlesValue(0.1)
                .gptFilesValue(0.1)
                .productionValue(0.1)
                .reorderValue(0.1)
                .sampleValue(0.3)
                .omsProductionValue(0.1)
                .kipstaProductionValue(0.1)
                .build();
    }
}