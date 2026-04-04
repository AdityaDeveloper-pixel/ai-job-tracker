package com.jobtracker.jobservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class CampaignDTO {

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Email body is required")
    private String body;

    private String jobRole;
    private String company;

    // HR recipient lists — parallel arrays from form
    private List<String> hrNames;
    private List<String> hrEmails;
}
