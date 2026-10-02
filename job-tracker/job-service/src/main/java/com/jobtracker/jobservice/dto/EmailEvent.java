package com.jobtracker.jobservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Kafka message payload for the email-send topic.
 * Each event represents a single email to be sent to one recipient.
 *
 * Resume bytes are Base64-encoded (if present) so the event is JSON-safe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailEvent implements Serializable {

    private Long recipientId;
    private Long campaignId;

    private String hrName;
    private String hrEmail;
    private String subject;
    private String body;           // Raw body template (contains {hrName} placeholder)

    // Resume attachment — Base64-encoded bytes (null if no attachment)
    private String resumeBase64;
    private String resumeFilename;
    private String resumeContentType;

    // Retry metadata
    @Builder.Default
    private int retryCount = 0;
}
