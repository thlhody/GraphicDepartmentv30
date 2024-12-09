package com.ctgraphdep.model;

import lombok.Getter;
import java.nio.file.Path;

@Getter
public class DualPath {
    private final Path primary;
    private final Path secondary;

    public DualPath(Path primary, Path secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public boolean hasPrimary() {
        return primary != null;
    }

    public boolean hasSecondary() {
        return secondary != null;
    }

    public Path getPrimary() {
        return primary;
    }

    public Path getSecondary() {
        return secondary;
    }

    @Override
    public String toString() {
        return String.format("DualPath{primary=%s, secondary=%s}",
                primary != null ? primary.toString() : "null",
                secondary != null ? secondary.toString() : "null");
    }
}