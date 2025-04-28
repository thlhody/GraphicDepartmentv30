package com.ctgraphdep.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents check value entries for a specific user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsersCheckValueEntry {

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("name")
    private String name;

    @JsonProperty("latestEntry")
    private String latestEntry;

    @JsonProperty("checkValuesEntry")
    private CheckValuesEntry checkValuesEntry;

    /**
     * Updates the check values entry
     * @param entry The new entry to set
     */
    public void updateCheckValueEntry(CheckValuesEntry entry) {
        this.checkValuesEntry = entry;
        this.latestEntry = entry.getCreatedAt().toString();
    }
}