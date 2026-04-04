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

    // Create campaign and send emails
    @PostMapping("/campaigns")
    public String create(@Valid @ModelAttribute("campaignDto") CampaignDTO dto,
                         BindingResult result,
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

        try {
            campaignService.createAndSend(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Campaign created and emails sent!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Some emails may have failed: " + e.getMessage());
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
