package com.ctgraphdep.enums;

import lombok.Getter;
import java.util.Arrays;
import java.util.List;

@Getter
public enum CheckType {
    GPT("GPT"),
    LAYOUT("LAYOUT"),
    KIPSTA_LAYOUT("KIPSTA LAYOUT"),
    LAYOUT_CHANGES("LAYOUT CHANGES"),
    PRODUCTION("PRODUCTION"),
    REORDER("REORDER"),
    SAMPLE("SAMPLE"),
    OMS_PRODUCTION("OMS PRODUCTION"),
    KIPSTA_PRODUCTION("KIPSTA PRODUCTION");
    private final String value;

    CheckType(String value) {
        this.value = value;
    }

    public static List<String> getValues() {
        return Arrays.stream(values()).map(CheckType::getValue).toList();
    }
}
