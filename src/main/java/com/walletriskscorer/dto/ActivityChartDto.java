package com.walletriskscorer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityChartDto {
    private String label;
    private int value;
    private String height;
    private boolean active;
}
