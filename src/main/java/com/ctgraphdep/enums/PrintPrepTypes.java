package com.ctgraphdep.enums;

import lombok.Getter;
import java.util.Arrays;
import java.util.List;

@Getter
public enum PrintPrepTypes {
    DIGITAL("DIGITAL"),
    SBS("SBS"),
    GPT("GPT"),
    NN("NN"),
    NAME("NAME"),
    NUMBER("NUMBER"),
    FLEX("FLEX"),
    BRODERIE("BRODERIE"),
    FILM("FILM"),
    OTHER("OTHER");

    private final String value;

    PrintPrepTypes(String value) {
        this.value = value;
    }

    public static List<String> getValues() {
        return Arrays.stream(values()).map(PrintPrepTypes::getValue).toList();
    }
}