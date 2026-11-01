package com.jobtracker.jobservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Reusable email template for cold mail campaigns.
 * Users create templates once, then pick from a dropdown when creating campaigns.
 */
@Entity
@Table(name = "email_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;           // e.g. "SDE Cold Mail", "Fresher Intro"

    @Column(nullable = false)
    private String subject;        // e.g. "Application for {jobRole} ΓÇö Java Developer"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;           // Supports placeholders: {hrName}, {company}, {jobRole}

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private User user;
}
