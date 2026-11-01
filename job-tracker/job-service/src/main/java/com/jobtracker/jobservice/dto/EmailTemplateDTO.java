package com.jobtracker.jobservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailTemplateDTO {

    @NotBlank(message = "Template name is required")
    private String name;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Email body is required")
    private String body;
}
