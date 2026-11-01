package com.jobtracker.jobservice.controller;

import com.jobtracker.jobservice.dto.JobApplicationDTO;
import com.jobtracker.jobservice.entity.JobApplication;
import com.jobtracker.jobservice.enums.ApplicationSource;
import com.jobtracker.jobservice.enums.ApplicationStatus;
import com.jobtracker.jobservice.exception.ResourceNotFoundException;
import com.jobtracker.jobservice.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService service;

    // Dashboard - list all applications
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("jobs", service.getAllApplications());
        model.addAttribute("pendingFollowUps", service.getPendingFollowUps());
        Map<String, Object> stats = service.getDashboardStats();
        model.addAttribute("stats", stats);
        return "jobs/dashboard";
    }

    // Show add form
    @GetMapping("/new")
    public String showAddForm(Model model) {
        model.addAttribute("jobDto", new JobApplicationDTO());
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("sources", ApplicationSource.values());
        return "jobs/add";
    }

    // Save new application
    @PostMapping
    public String save(@Valid @ModelAttribute("jobDto") JobApplicationDTO dto,
                       BindingResult result,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("statuses", ApplicationStatus.values());
            model.addAttribute("sources", ApplicationSource.values());
            return "jobs/add";
        }
        service.save(dto);
        redirectAttributes.addFlashAttribute("successMessage", "Application saved successfully!");
        return "redirect:/jobs";
    }

    // View single application detail
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        JobApplication job = service.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job application", id));
        model.addAttribute("job", job);
        model.addAttribute("statuses", ApplicationStatus.values());
        return "jobs/detail";
    }

    // Show edit form ΓÇö pre-fills DTO from existing entity
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        JobApplication job = service.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job application", id));

        // Map entity ΓåÆ DTO so the form binds correctly
        JobApplicationDTO dto = new JobApplicationDTO();
        dto.setCompany(job.getCompany());
        dto.setRole(job.getRole());
        dto.setJdText(job.getJdText());
        dto.setResumeText(job.getResumeText());
        dto.setStatus(job.getStatus());
        dto.setSource(job.getSource());
        dto.setHrName(job.getHrName());
        dto.setHrEmail(job.getHrEmail());
        dto.setMailSentDate(job.getMailSentDate());
        dto.setFollowUpDate(job.getFollowUpDate());
        dto.setFollowUpDone(job.getFollowUpDone());
        dto.setNotes(job.getNotes());

        model.addAttribute("jobDto", dto);
        model.addAttribute("jobId", id);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("sources", ApplicationSource.values());
        return "jobs/edit";
    }

    // Save edited application
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("jobDto") JobApplicationDTO dto,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("jobId", id);
            model.addAttribute("statuses", ApplicationStatus.values());
            model.addAttribute("sources", ApplicationSource.values());
            return "jobs/edit";
        }
        service.update(id, dto);
        redirectAttributes.addFlashAttribute("successMessage", "Application updated successfully!");
        return "redirect:/jobs/" + id;
    }

    // Re-trigger AI analysis
    @PostMapping("/{id}/reanalyse")
    public String reAnalyse(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            service.reAnalyse(id);
            redirectAttributes.addFlashAttribute("successMessage", "AI re-analysis complete!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "AI analysis failed: " + e.getMessage());
        }
        return "redirect:/jobs/" + id;
    }

    // Update status
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam ApplicationStatus status,
                               RedirectAttributes redirectAttributes) {
        service.updateStatus(id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Status updated!");
        return "redirect:/jobs/" + id;
    }

    // Mark follow-up done
    @PostMapping("/{id}/followup")
    public String markFollowUp(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        service.markFollowUpDone(id);
        redirectAttributes.addFlashAttribute("successMessage", "Follow-up marked as done!");
        return "redirect:/jobs";
    }

    // Delete
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        service.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Application deleted.");
        return "redirect:/jobs";
    }

    // Redirect root to dashboard
    @GetMapping("/")
    public String root() {
        return "redirect:/jobs";
    }
}
