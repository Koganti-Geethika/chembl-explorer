package com.centella.chembl.service;

import com.centella.chembl.client.ChemblApiClient;
import com.centella.chembl.dto.ActivityRecord;
import com.centella.chembl.dto.ActivityResponse;
import com.centella.chembl.exception.InvalidTargetIdException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ChemblApiClient chemblApiClient;

    @InjectMocks
    private ActivityService activityService;

    private List<ActivityRecord> mockRecords;

    @BeforeEach
    void setUp() {
        ActivityRecord r1 = new ActivityRecord();
        r1.setActivityId(1L);
        r1.setMoleculeChemblId("CHEMBL1");
        r1.setStandardType("IC50");
        r1.setStandardValue("10.5");
        r1.setStandardUnits("nM");

        ActivityRecord r2 = new ActivityRecord();
        r2.setActivityId(2L);
        r2.setMoleculeChemblId("CHEMBL2");
        r2.setStandardType("IC50");
        r2.setStandardValue("250.0");
        r2.setStandardUnits("nM");

        ActivityRecord r3 = new ActivityRecord();
        r3.setActivityId(3L);
        r3.setMoleculeChemblId("CHEMBL3");
        r3.setStandardType("Ki");
        r3.setStandardValue("5.0");
        r3.setStandardUnits("nM");

        ActivityRecord r4 = new ActivityRecord();
        r4.setActivityId(4L);
        r4.setMoleculeChemblId("CHEMBL1"); // duplicate compound
        r4.setStandardType("IC50");
        r4.setStandardValue("8.0");        // better value for CHEMBL1
        r4.setStandardUnits("nM");

        mockRecords = Arrays.asList(r1, r2, r3, r4);
    }

    @Test
    void shouldReturnActivitiesSuccessfully() {
        when(chemblApiClient.fetchAllActivities(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(mockRecords);

        ActivityResponse response = activityService.getActivities(
                "CHEMBL203", null, null, null, null, null, 0, 25);

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getTotalRecords()).isEqualTo(4);
        assertThat(response.getSummary()).isNotNull();
    }

    @Test
    void shouldComputeCorrectUniqueCompounds() {
        when(chemblApiClient.fetchAllActivities(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(mockRecords);

        ActivityResponse response = activityService.getActivities(
                "CHEMBL203", null, null, null, null, null, 0, 25);

        // CHEMBL1, CHEMBL2, CHEMBL3 = 3 unique compounds
        assertThat(response.getSummary().getUniqueCompounds()).isEqualTo(3);
    }

    @Test
    void shouldIdentifyMostCommonActivityType() {
        when(chemblApiClient.fetchAllActivities(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(mockRecords);

        ActivityResponse response = activityService.getActivities(
                "CHEMBL203", null, null, null, null, null, 0, 25);

        // IC50 appears 3 times, Ki appears 1 time
        assertThat(response.getSummary().getMostCommonActivityType()).isEqualTo("IC50");
    }

    @Test
    void shouldRankTop5ByLowestValue() {
        when(chemblApiClient.fetchAllActivities(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(mockRecords);

        ActivityResponse response = activityService.getActivities(
                "CHEMBL203", null, null, null, null, null, 0, 25);

        // Top should be CHEMBL3 (5.0), then CHEMBL1 (8.0 — best of 10.5 and 8.0), then CHEMBL2 (250.0)
        var top = response.getSummary().getTop5Compounds();
        assertThat(top).isNotEmpty();
        assertThat(top.get(0).getMoleculeChemblId()).isEqualTo("CHEMBL3");
        assertThat(top.get(1).getMoleculeChemblId()).isEqualTo("CHEMBL1");
        assertThat(top.get(1).getActivityValue()).isEqualTo(8.0);
    }

    @Test
    void shouldPaginateCorrectly() {
        when(chemblApiClient.fetchAllActivities(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(mockRecords);

        ActivityResponse response = activityService.getActivities(
                "CHEMBL203", null, null, null, null, null, 0, 2);

        assertThat(response.getActivities()).hasSize(2);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.getTotalRecords()).isEqualTo(4);
    }

    @Test
    void shouldReturnEmptyResponseForNoResults() {
        when(chemblApiClient.fetchAllActivities(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ActivityResponse response = activityService.getActivities(
                "CHEMBL203", "IC50", null, null, null, null, 0, 25);

        assertThat(response.getTotalRecords()).isEqualTo(0);
        assertThat(response.getActivities()).isEmpty();
        assertThat(response.getSummary().getMostCommonActivityType()).isEqualTo("N/A");
    }

    @Test
    void shouldRejectInvalidTargetIdFormat() {
        assertThatThrownBy(() ->
                activityService.getActivities("INVALID123", null, null, null, null, null, 0, 25)
        ).isInstanceOf(InvalidTargetIdException.class);
    }

    @Test
    void shouldRejectBlankTargetId() {
        assertThatThrownBy(() ->
                activityService.getActivities("   ", null, null, null, null, null, 0, 25)
        ).isInstanceOf(InvalidTargetIdException.class);
    }
}
