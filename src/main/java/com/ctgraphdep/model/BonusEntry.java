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
public class BonusEntry {
    @JsonProperty("name")
    private String name;

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("entries")
    private Integer entries;

    @JsonProperty("articleNumbers")
    private Double articleNumbers;

    @JsonProperty("graphicComplexity")
    private Double graphicComplexity;

    @JsonProperty("misc")
    private Double misc;

    @JsonProperty("workedDays")
    private Integer workedDays;

    @JsonProperty("workedPercentage")
    private Double workedPercentage;

    @JsonProperty("bonusPercentage")
    private Double bonusPercentage;

    @JsonProperty("bonusAmount")
    private Double bonusAmount;

    @JsonProperty("previousMonths")
    private PreviousMonthsBonuses previousMonths;

    @JsonProperty("calculationDate")
    private String calculationDate;

    public static BonusEntry fromBonusCalculationResult(String username, Integer userId, BonusCalculationResult result) {
        return BonusEntry.builder()
                .name(username)
                .userId(userId)
                .entries(result.getEntries())
                .articleNumbers(result.getArticleNumbers())
                .graphicComplexity(result.getGraphicComplexity())
                .misc(result.getMisc())
                .workedDays(result.getWorkedDays())
                .workedPercentage(result.getWorkedPercentage())
                .bonusPercentage(result.getBonusPercentage())
                .bonusAmount(result.getBonusAmount())
                .calculationDate(java.time.LocalDateTime.now().toString())
                .build();
    }
}