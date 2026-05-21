package com.centella.chembl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a single activity record from the ChEMBL API.
 * Fields are mapped from ChEMBL's JSON response schema.
 * Missing/null fields are handled gracefully — ChEMBL data is often incomplete.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityRecord {

    @JsonProperty("activity_id")
    private Long activityId;

    @JsonProperty("molecule_chembl_id")
    private String moleculeChemblId;

    @JsonProperty("target_chembl_id")
    private String targetChemblId;

    @JsonProperty("assay_chembl_id")
    private String assayChemblId;

    @JsonProperty("assay_type")
    private String assayType;

    @JsonProperty("assay_description")
    private String assayDescription;

    @JsonProperty("standard_type")
    private String standardType;

    @JsonProperty("standard_value")
    private String standardValue;

    @JsonProperty("standard_units")
    private String standardUnits;

    @JsonProperty("standard_relation")
    private String standardRelation;

    @JsonProperty("target_organism")
    private String targetOrganism;

    @JsonProperty("document_chembl_id")
    private String documentChemblId;

    @JsonProperty("pchembl_value")
    private String pchemblValue;

    @JsonProperty("data_validity_comment")
    private String dataValidityComment;

    @JsonProperty("potential_duplicate")
    private Boolean potentialDuplicate;

    /**
     * Safely parse standard_value as a Double.
     * Returns null if the value is missing, blank, or non-numeric.
     */
    public Double getStandardValueAsDouble() {
        if (standardValue == null || standardValue.isBlank()) return null;
        try {
            return Double.parseDouble(standardValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns true if this record has a valid numeric activity value.
     */
    public boolean hasValidValue() {
        return getStandardValueAsDouble() != null;
    }
}
