package com.ctgraphdep.enums;

import lombok.Getter;
import java.util.Arrays;
import java.util.List;

@Getter
public enum ActionType {
    ORDIN("ORDIN"),
    REORDIN("REORDIN"),
    CAMPION("CAMPION"),
    PROBA_STAMPA("PROBA STAMPA"),
    ORD_SPIZED("ORD SPIZED"),
    PROBA_CULOARE("PROBA CULOARE"),
    CARTELA_CULORI("CARTELA CULORI"),
    DESIGN("DESIGN"),
    DESIGN_3D("DESIGN 3D"),
    PATTERN_PREP("PATTERN PREP"),
    IMPOSTARE("IMPOSTARE"),
    OTHER("OTHER");

    private final String value;

    ActionType(String value) {
        this.value = value;
    }

    public static List<String> getValues() {
        return Arrays.stream(values()).map(ActionType::getValue).toList();
    }

    public static List<String> getBonusEligibleValues() {
        return Arrays.stream(values()).filter(type -> type != IMPOSTARE).map(ActionType::getValue).toList();
    }
}