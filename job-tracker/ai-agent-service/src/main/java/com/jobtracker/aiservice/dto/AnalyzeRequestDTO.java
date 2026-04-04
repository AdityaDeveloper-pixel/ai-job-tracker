package com.jobtracker.aiservice.dto;

import lombok.Data;

@Data
public class AnalyzeRequestDTO {
    private String jdText;
    private String resumeText;
}
