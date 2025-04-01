package com.ctgraphdep.model.dto;

import com.ctgraphdep.model.RegisterEntry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterSearchResultDTO {
    private LocalDate date;
    private String orderId;
    private String productionId;
    private String omsId;
    private String clientName;
    private String actionType;
    private List<String> printPrepTypes;
    private String colorsProfile;
    private Integer articleNumbers;
    private Double graphicComplexity;
    private String observations;

    public RegisterSearchResultDTO(RegisterEntry entry) {
        this.date = entry.getDate();
        this.orderId = entry.getOrderId();
        this.productionId = entry.getProductionId();
        this.omsId = entry.getOmsId();
        this.clientName = entry.getClientName();
        this.actionType = entry.getActionType();
        this.printPrepTypes = entry.getPrintPrepTypes();
        this.colorsProfile = entry.getColorsProfile();
        this.articleNumbers = entry.getArticleNumbers();
        this.graphicComplexity = entry.getGraphicComplexity();
        this.observations = entry.getObservations();
    }
}