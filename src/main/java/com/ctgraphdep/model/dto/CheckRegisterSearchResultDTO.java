package com.ctgraphdep.model.dto;

import com.ctgraphdep.model.RegisterCheckEntry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Data Transfer Object for check register search results
 * Similar to RegisterSearchResultDTO but for check entries
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckRegisterSearchResultDTO {
    private LocalDate date;
    private String orderId;
    private String productionId;
    private String omsId;
    private String designerName;
    private String checkType;
    private Integer articleNumbers;
    private Integer filesNumbers;
    private String errorDescription;
    private String approvalStatus;
    private Double orderValue;

    /**
     * Constructs a DTO from a RegisterCheckEntry entity
     */
    public CheckRegisterSearchResultDTO(RegisterCheckEntry entry) {
        this.date = entry.getDate();
        this.orderId = entry.getOrderId();
        this.productionId = entry.getProductionId();
        this.omsId = entry.getOmsId();
        this.designerName = entry.getDesignerName();
        this.checkType = entry.getCheckType();
        this.articleNumbers = entry.getArticleNumbers();
        this.filesNumbers = entry.getFilesNumbers();
        this.errorDescription = entry.getErrorDescription();
        this.approvalStatus = entry.getApprovalStatus();
        this.orderValue = entry.getOrderValue();
    }
}