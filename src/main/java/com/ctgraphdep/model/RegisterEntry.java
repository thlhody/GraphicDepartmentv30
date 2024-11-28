package com.ctgraphdep.model;

import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RegisterEntry {
    @JsonProperty("entryId")
    private Integer entryId;

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("date")
    private LocalDate date;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("productionId")
    private String productionId;

    @JsonProperty("omsId")
    private String omsId;

    @JsonProperty("clientName")
    private String clientName;

    @JsonProperty("actionType")
    private String actionType;

    @JsonProperty("printPrepType")
    private String printPrepType;

    @JsonProperty("colorsProfile")
    private String colorsProfile;

    @JsonProperty("articleNumbers")
    private Integer articleNumbers;

    @JsonProperty("graphicComplexity")
    private Double graphicComplexity;

    @JsonProperty("observations")
    private String observations;

    @JsonProperty("adminSync")
    private String adminSync;

}