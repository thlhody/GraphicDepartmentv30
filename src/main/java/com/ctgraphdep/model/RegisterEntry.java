package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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

    @JsonProperty("printPrepTypes")
    private List<String> printPrepTypes = new ArrayList<>();

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

    public List<String> getPrintPrepTypes() {
        // Return a new ArrayList to avoid modification of the internal list
        // Also ensure we don't have duplicates by using a LinkedHashSet
        return new ArrayList<>(new LinkedHashSet<>(
                printPrepTypes
        ));
    }
}