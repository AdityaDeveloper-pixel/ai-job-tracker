package com.jobtracker.jobservice.dto;

import lombok.Data;

@Data
public class AiResponseDTO {
    private String extractedSkills;
    private Integer fitScore;
    private String fitReason;
}
