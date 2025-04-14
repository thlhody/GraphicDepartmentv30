package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RegisterCheckEntry {

    @JsonProperty("entryId")
    private Integer entryId;

    @JsonProperty("date")
    private LocalDate date;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("designerName")
    private String designerName;

    @JsonProperty("omsId")
    private String omsId;

    @JsonProperty("productionId")
    private String productionId;

    @JsonProperty("checkType")
    private String checkType;

    @JsonProperty("articleNumbers")
    private Integer articleNumbers;

    @JsonProperty("filesNumbers")
    private Integer filesNumbers;

    @JsonProperty("errorDescription")
    private String errorDescription;

    @JsonProperty("approvalStatus")
    private String approvalStatus;

    @JsonProperty("orderValue")
    private Double orderValue;

    @JsonProperty("adminSync")
    private String adminSync;
}
