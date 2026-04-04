package com.jobtracker.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeResponseDTO {
    private String extractedSkills;
    private Integer fitScore;
    private String fitReason;
}
