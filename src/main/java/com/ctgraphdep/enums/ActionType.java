// src/main/java/com/ctgraphdep/enums/ActionType.java
package com.ctgraphdep.enums;

import lombok.Getter;
import java.util.Arrays;
import java.util.List;

@Getter
public enum ActionType {
    ORDIN("ORDIN"),
    CAMPION("CAMPION"),
    PROBA_CULOARE("PROBA CULOARE"),
    PROBA_STAMPA("PROBA STAMPA"),
    REORDIN("REORDIN"),
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
        return Arrays.stream(values())
                .map(ActionType::getValue)
                .toList();
    }
}