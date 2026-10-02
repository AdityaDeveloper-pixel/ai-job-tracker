package com.jobtracker.jobservice.service;

import com.jobtracker.jobservice.dto.CampaignDTO;
import com.jobtracker.jobservice.dto.EmailEvent;
import com.jobtracker.jobservice.entity.CampaignRecipient;
import com.jobtracker.jobservice.entity.EmailCampaign;
import com.jobtracker.jobservice.kafka.KafkaEmailProducer;
import com.jobtracker.jobservice.repository.CampaignRecipientRepository;
import com.jobtracker.jobservice.repository.EmailCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Manages email campaigns.
 *
 * Email sending is now EVENT-DRIVEN via Apache Kafka:
 *   1. Campaign + recipients are saved to DB
 *   2. One EmailEvent per recipient is published to the "email-send" Kafka topic
 *   3. KafkaEmailConsumer picks up events and sends emails via SMTP
 *   4. Failed emails are retried up to 3 times, then moved to DLQ
 *
 * This means createAndSend() returns IMMEDIATELY — no more blocking the HTTP thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailCampaignService {

    private final EmailCampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final KafkaEmailProducer kafkaEmailProducer;

    public List<EmailCampaign> getAllCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }

    public EmailCampaign getById(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found: " + id));
    }

    /**
     * Creates a campaign, saves to DB, then publishes Kafka events for each recipient.
     * Returns immediately — email delivery happens asynchronously via Kafka consumer.
     */
    public EmailCampaign createAndSend(CampaignDTO dto) throws Exception {

        // --- Resolve optional resume attachment ---
        String resumeBase64 = null;
        String resumeFilename = null;
        String resumeContentType = "application/octet-stream";

        MultipartFile file = dto.getResumeFile();
        if (file != null && !file.isEmpty()) {
            resumeBase64 = Base64.getEncoder().encodeToString(file.getBytes());
            resumeFilename = file.getOriginalFilename();
            String ct = file.getContentType();
            if (ct != null && !ct.isBlank()) {
                resumeContentType = ct;
            }
            log.info("Resume attachment received: {} ({} bytes)", resumeFilename, file.getSize());
        }

        // Build campaign entity
        EmailCampaign campaign = EmailCampaign.builder()
                .subject(dto.getSubject())
                .body(dto.getBody())
                .jobRole(dto.getJobRole())
                .company(dto.getCompany())
                .build();

        // Build recipients
        List<CampaignRecipient> recipients = new ArrayList<>();
        List<String> names = dto.getHrNames();
        List<String> emails = dto.getHrEmails();

        for (int i = 0; i < emails.size(); i++) {
            String email = emails.get(i).trim();
            String name = (names != null && i < names.size()) ? names.get(i).trim() : "HR";
            if (!email.isBlank()) {
                CampaignRecipient recipient = CampaignRecipient.builder()
                        .campaign(campaign)
                        .hrName(name)
                        .hrEmail(email)
                        .deliveryStatus("QUEUED")
                        .build();
                recipients.add(recipient);
            }
        }

        campaign.setRecipients(recipients);
        EmailCampaign saved = campaignRepository.save(campaign);

        // ---- Publish Kafka events (one per recipient) ----
        for (CampaignRecipient recipient : saved.getRecipients()) {
            EmailEvent event = EmailEvent.builder()
                    .recipientId(recipient.getId())
                    .campaignId(saved.getId())
                    .hrName(recipient.getHrName())
                    .hrEmail(recipient.getHrEmail())
                    .subject(dto.getSubject())
                    .body(dto.getBody())
                    .resumeBase64(resumeBase64)
                    .resumeFilename(resumeFilename)
                    .resumeContentType(resumeContentType)
                    .retryCount(0)
                    .build();

            kafkaEmailProducer.publish(event);
        }

        log.info("Campaign {} created — {} email events published to Kafka",
                saved.getId(), saved.getRecipients().size());

        return saved;
    }

    // Called when tracking pixel is loaded — marks email as opened
    public void markOpened(Long recipientId) {
        recipientRepository.findById(recipientId).ifPresent(r -> {
            if (!r.getOpened()) {
                r.setOpened(true);
                r.setOpenedAt(LocalDateTime.now());
                recipientRepository.save(r);
                log.info("Email opened by: {}", r.getHrEmail());
            }
        });
    }

    // Manually mark replied from UI
    public void markReplied(Long recipientId) {
        recipientRepository.findById(recipientId).ifPresent(r -> {
            r.setReplied(true);
            r.setRepliedAt(LocalDateTime.now());
            recipientRepository.save(r);
        });
    }

    public void deleteCampaign(Long id) {
        campaignRepository.deleteById(id);
    }
}
