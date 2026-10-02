package com.jobtracker.jobservice.controller;

import com.jobtracker.jobservice.dto.CampaignDTO;
import com.jobtracker.jobservice.service.EmailCampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Base64;

@Controller
@RequiredArgsConstructor
public class EmailCampaignController {

    private final EmailCampaignService campaignService;

    // List all campaigns
    @GetMapping("/campaigns")
    public String list(Model model) {
        model.addAttribute("campaigns", campaignService.getAllCampaigns());
        return "campaigns/list";
    }

    // Show create form
    @GetMapping("/campaigns/new")
    public String showForm(Model model) {
        model.addAttribute("campaignDto", new CampaignDTO());
        return "campaigns/create";
    }

    /**
     * Create campaign and send emails.
     * Uses multipart/form-data so the resume PDF can be uploaded alongside the form fields.
     */
    @PostMapping(value = "/campaigns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String create(@Valid @ModelAttribute("campaignDto") CampaignDTO dto,
                         BindingResult result,
                         @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "campaigns/create";
        }

        // Validate at least one email entered
        if (dto.getHrEmails() == null || dto.getHrEmails().stream().allMatch(String::isBlank)) {
            model.addAttribute("error", "Please add at least one HR email.");
            return "campaigns/create";
        }

        // Validate file type if provided (only PDF / Word)
        if (resumeFile != null && !resumeFile.isEmpty()) {
            String ct = resumeFile.getContentType();
            if (ct == null || (!ct.equals("application/pdf")
                    && !ct.equals("application/msword")
                    && !ct.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
                model.addAttribute("error", "Resume must be a PDF or Word document (.pdf / .doc / .docx).");
                return "campaigns/create";
            }
            // Attach to DTO so service can read it
            dto.setResumeFile(resumeFile);
        }

        try {
            campaignService.createAndSend(dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Campaign created! Emails are being sent asynchronously via Kafka. Refresh this page to see delivery status updates.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to queue emails: " + e.getMessage());
        }

        return "redirect:/campaigns";
    }

    // Campaign detail — per HR tracking
    @GetMapping("/campaigns/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("campaign", campaignService.getById(id));
        return "campaigns/detail";
    }

    // Mark replied manually
    @PostMapping("/campaigns/recipients/{id}/replied")
    public String markReplied(@PathVariable Long id,
                               @RequestParam Long campaignId,
                               RedirectAttributes redirectAttributes) {
        campaignService.markReplied(id);
        redirectAttributes.addFlashAttribute("successMessage", "Marked as replied!");
        return "redirect:/campaigns/" + campaignId;
    }

    // Delete campaign
    @PostMapping("/campaigns/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        campaignService.deleteCampaign(id);
        redirectAttributes.addFlashAttribute("successMessage", "Campaign deleted.");
        return "redirect:/campaigns";
    }

    // ---- OPEN TRACKING PIXEL ----
    // When HR opens email, their client loads this URL
    @GetMapping("/track/open/{recipientId}")
    public ResponseEntity<byte[]> trackOpen(@PathVariable Long recipientId) {
        campaignService.markOpened(recipientId);

        // Return a 1x1 transparent GIF
        byte[] pixel = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
        );

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_GIF)
                .body(pixel);
    }
}
