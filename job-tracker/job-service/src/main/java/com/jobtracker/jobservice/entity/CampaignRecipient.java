package com.jobtracker.jobservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_recipients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private EmailCampaign campaign;

    @Column(nullable = false)
    private String hrName;

    @Column(nullable = false)
    private String hrEmail;

    // Tracking fields
    @Builder.Default
    private Boolean sent = false;

    @Builder.Default
    private Boolean opened = false;

    @Builder.Default
    private Boolean replied = false;

    private LocalDateTime sentAt;
    private LocalDateTime openedAt;
    private LocalDateTime repliedAt;

    // PENDING / SENT / RETRYING / FAILED
    @Builder.Default
    private String deliveryStatus = "PENDING";

    private String failureReason;

    // Kafka retry count — tracks how many times email send was retried
    @Builder.Default
    private Integer retryCount = 0;
}
