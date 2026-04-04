package com.jobtracker.jobservice.service;

import com.jobtracker.jobservice.dto.CampaignDTO;
import com.jobtracker.jobservice.entity.CampaignRecipient;
import com.jobtracker.jobservice.entity.EmailCampaign;
import com.jobtracker.jobservice.repository.CampaignRecipientRepository;
import com.jobtracker.jobservice.repository.EmailCampaignRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailCampaignService {

    private final EmailCampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final JavaMailSender mailSender;

    @Value("${app.base.url}")
    private String baseUrl;

    public List<EmailCampaign> getAllCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc();
    }

    public EmailCampaign getById(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found: " + id));
    }

    public EmailCampaign createAndSend(CampaignDTO dto) {
        // Build campaign
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
                        .build();
                recipients.add(recipient);
            }
        }

        campaign.setRecipients(recipients);
        EmailCampaign saved = campaignRepository.save(campaign);

        // Send emails to each recipient
        for (CampaignRecipient recipient : saved.getRecipients()) {
            sendEmail(recipient, dto.getSubject(), dto.getBody());
        }

        return saved;
    }

    private void sendEmail(CampaignRecipient recipient, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(recipient.getHrEmail());
            helper.setSubject(subject);

            // Personalise body — replace {hrName} placeholder
            String personalizedBody = body.replace("{hrName}", recipient.getHrName());

            // Build tracking pixel URL
            String trackingPixelUrl = baseUrl + "/track/open/" + recipient.getId();
            String trackingPixel = "<img src=\"" + trackingPixelUrl + "\" width=\"1\" height=\"1\" style=\"display:none\"/>";

            // Wrap in basic HTML with tracking pixel at bottom
            String htmlBody = "<html><body>"
                    + personalizedBody.replace("\n", "<br/>")
                    + "<br/><br/>"
                    + trackingPixel
                    + "</body></html>";

            helper.setText(htmlBody, true);

            mailSender.send(message);

            // Mark as sent
            recipient.setSent(true);
            recipient.setSentAt(LocalDateTime.now());
            recipient.setDeliveryStatus("SENT");
            recipientRepository.save(recipient);

            log.info("Email sent to: {}", recipient.getHrEmail());

            // Small delay between emails to avoid spam filters
            Thread.sleep(1500);

        } catch (Exception e) {
            log.error("Failed to send email to: {}", recipient.getHrEmail(), e);
            recipient.setDeliveryStatus("FAILED");
            recipient.setFailureReason(e.getMessage());
            recipientRepository.save(recipient);
        }
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
