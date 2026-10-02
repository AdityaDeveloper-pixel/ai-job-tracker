package com.jobtracker.jobservice.kafka;

import com.jobtracker.jobservice.config.KafkaConfig;
import com.jobtracker.jobservice.dto.EmailEvent;
import com.jobtracker.jobservice.entity.CampaignRecipient;
import com.jobtracker.jobservice.repository.CampaignRecipientRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Kafka consumer that listens on the "email-send" topic and sends emails via SMTP.
 *
 * Flow:
 *   1. Consume EmailEvent from Kafka
 *   2. Send personalised HTML email via Gmail SMTP
 *   3. Update CampaignRecipient status in DB (SENT / FAILED)
 *   4. On permanent failure (after retries), forward to DLQ topic
 *
 * Concurrency is controlled by KafkaConfig (3 consumers for 3 partitions).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEmailConsumer {

    private static final int MAX_RETRIES = 3;

    private final JavaMailSender mailSender;
    private final CampaignRecipientRepository recipientRepository;
    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;

    @Value("${app.base.url}")
    private String baseUrl;

    @KafkaListener(
            topics = KafkaConfig.EMAIL_TOPIC,
            groupId = "email-sender-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(EmailEvent event) {
        log.info("Consumed email event — recipientId={}, hrEmail={} ,hrName={} , retry={}",
                event.getRecipientId(), event.getHrEmail(), event.getHrName(),event.getRetryCount());

        try {
            sendEmail(event);
            markAsSent(event.getRecipientId());
            log.info("Email sent successfully — recipientId={}, hrEmail={},hrName={}",
                    event.getRecipientId(), event.getHrEmail(),event.getHrName());

        } catch (Exception e) {
            log.error("Email sending failed — recipientId={}, hrEmail={}, attempt={}: {}",
                    event.getRecipientId(), event.getHrEmail(), event.getRetryCount() + 1, e.getMessage());

            if (event.getRetryCount() < MAX_RETRIES) {
                // Retry — re-publish with incremented retry count
                event.setRetryCount(event.getRetryCount() + 1);
                kafkaTemplate.send(KafkaConfig.EMAIL_TOPIC,
                        String.valueOf(event.getRecipientId()), event);
                log.warn("Re-queued for retry — recipientId={}, nextRetry={}",
                        event.getRecipientId(), event.getRetryCount());

                // Update status in DB
                updateRetryStatus(event.getRecipientId(), event.getRetryCount());
            } else {
                // Max retries exhausted → send to Dead Letter Queue
                kafkaTemplate.send(KafkaConfig.EMAIL_DLQ_TOPIC,
                        String.valueOf(event.getRecipientId()), event);
                markAsFailed(event.getRecipientId(), e.getMessage());
                log.error("Moved to DLQ — recipientId={}, hrEmail={}",
                        event.getRecipientId(), event.getHrEmail());
            }
        }
    }

    /**
     * DLQ consumer — logs permanently failed emails.
     * In production, this could trigger alerts, admin notifications, etc.
     */
    @KafkaListener(
            topics = KafkaConfig.EMAIL_DLQ_TOPIC,
            groupId = "email-dlq-group"
    )
    public void consumeDlq(EmailEvent event) {
        log.error("DLQ — Permanently failed email: recipientId={}, hrEmail={}, retries={}",
                event.getRecipientId(), event.getHrEmail(), event.getRetryCount());
    }

    // ---- Internal email sending logic ----

    private void sendEmail(EmailEvent event) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(event.getHrEmail());
        helper.setSubject(event.getSubject());

        // Personalise body — replace {hrName} placeholder
        String personalizedBody = event.getBody().replace("{hrName}", event.getHrName());

        // Build tracking pixel URL
        String trackingPixelUrl = baseUrl + "/track/open/" + event.getRecipientId();
        String trackingPixel = "<img src=\"" + trackingPixelUrl
                + "\" width=\"1\" height=\"1\" style=\"display:none\"/>";

        // Wrap in HTML with tracking pixel at bottom
        String htmlBody = "<html><body>"
                + personalizedBody.replace("\n", "<br/>")
                + "<br/><br/>"
                + trackingPixel
                + "</body></html>";

        helper.setText(htmlBody, true);

        // Attach resume if present
        if (event.getResumeBase64() != null && !event.getResumeBase64().isEmpty()
                && event.getResumeFilename() != null) {
            byte[] resumeBytes = Base64.getDecoder().decode(event.getResumeBase64());
            String contentType = event.getResumeContentType() != null
                    ? event.getResumeContentType() : "application/octet-stream";
            ByteArrayDataSource dataSource = new ByteArrayDataSource(resumeBytes, contentType);
            helper.addAttachment(event.getResumeFilename(), dataSource);
            log.info("Attached resume '{}' for recipientId={}", event.getResumeFilename(), event.getRecipientId());
        }

        mailSender.send(message);
    }

    // ---- DB status updates ----

    private void markAsSent(Long recipientId) {
        recipientRepository.findById(recipientId).ifPresent(r -> {
            r.setSent(true);
            r.setSentAt(LocalDateTime.now());
            r.setDeliveryStatus("SENT");
            recipientRepository.save(r);
        });
    }

    private void markAsFailed(Long recipientId, String reason) {
        recipientRepository.findById(recipientId).ifPresent(r -> {
            r.setDeliveryStatus("FAILED");
            r.setFailureReason(reason);
            recipientRepository.save(r);
        });
    }

    private void updateRetryStatus(Long recipientId, int retryCount) {
        recipientRepository.findById(recipientId).ifPresent(r -> {
            r.setDeliveryStatus("RETRYING");
            r.setRetryCount(retryCount);
            recipientRepository.save(r);
        });
    }
}
