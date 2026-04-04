package com.jobtracker.jobservice.dto;

import com.jobtracker.jobservice.enums.ApplicationSource;
import com.jobtracker.jobservice.enums.ApplicationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class JobApplicationDTO {

    @NotBlank(message = "Company name is required")
    private String company;

    @NotBlank(message = "Role is required")
    private String role;

    private String jdText;
    private String resumeText;
    private ApplicationStatus status;
    private ApplicationSource source;

    // Cold mail fields
    private String hrName;

    @Email(message = "Enter a valid HR email")
    private String hrEmail;

    private LocalDate mailSentDate;
    private LocalDate followUpDate;
    private Boolean followUpDone;
    private String notes;
}
