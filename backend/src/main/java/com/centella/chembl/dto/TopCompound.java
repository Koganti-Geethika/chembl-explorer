package com.centella.chembl.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one of the top (strongest) compounds in the summary insights.
 * Ranked by lowest standard_value for the dominant activity type.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopCompound {
    private String moleculeChemblId;
    private String activityType;
    private Double activityValue;
    private String units;
    private String relation;
}
