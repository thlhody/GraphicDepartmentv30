package com.ctgraphdep.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum ApprovalStatusType {
    APPROVED("APPROVED"),
    CORRECTION("CORRECTION"),
    PARTIALLY_APPROVED("PARTIALLY APPROVED");

    private final String value;

    ApprovalStatusType(String value) {
        this.value = value;
    }

    public static List<String> getValues() {
        return Arrays.stream(values()).map(ApprovalStatusType::getValue).toList();
    }
}
