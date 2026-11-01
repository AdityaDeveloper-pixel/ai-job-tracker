package com.jobtracker.jobservice.controller;

import com.jobtracker.jobservice.dto.EmailTemplateDTO;
import com.jobtracker.jobservice.entity.EmailTemplate;
import com.jobtracker.jobservice.service.EmailTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles both Thymeleaf UI routes and a REST endpoint for AJAX template loading.
 *
 * UI Routes:
 *   GET  /templates           ΓåÆ list all templates
 *   GET  /templates/new       ΓåÆ create form
 *   POST /templates           ΓåÆ save new template
 *   GET  /templates/{id}/edit ΓåÆ edit form
 *   POST /templates/{id}/edit ΓåÆ save edits
 *   POST /templates/{id}/delete ΓåÆ delete
 *
 * REST (used by campaign create form's "Load Template" dropdown):
 *   GET  /api/templates/{id}  ΓåÆ returns template JSON
 */
@Controller
@RequiredArgsConstructor
public class EmailTemplateController {

    private final EmailTemplateService templateService;

    // ---- Thymeleaf UI Routes ----

    @GetMapping("/templates")
    public String list(Model model) {
        model.addAttribute("templates", templateService.getAllTemplates());
        return "templates/list";
    }

    @GetMapping("/templates/new")
    public String showCreateForm(Model model) {
        model.addAttribute("templateDto", new EmailTemplateDTO());
        return "templates/create";
    }

    @PostMapping("/templates")
    public String create(@Valid @ModelAttribute("templateDto") EmailTemplateDTO dto,
                         BindingResult result,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "templates/create";
        }
        templateService.create(dto);
        redirectAttributes.addFlashAttribute("successMessage", "Template saved!");
        return "redirect:/templates";
    }

    @GetMapping("/templates/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        EmailTemplate template = templateService.getById(id);

        EmailTemplateDTO dto = new EmailTemplateDTO();
        dto.setName(template.getName());
        dto.setSubject(template.getSubject());
        dto.setBody(template.getBody());

        model.addAttribute("templateDto", dto);
        model.addAttribute("templateId", id);
        return "templates/edit";
    }

    @PostMapping("/templates/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("templateDto") EmailTemplateDTO dto,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("templateId", id);
            return "templates/edit";
        }
        templateService.update(id, dto);
        redirectAttributes.addFlashAttribute("successMessage", "Template updated!");
        return "redirect:/templates";
    }

    @PostMapping("/templates/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        templateService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Template deleted.");
        return "redirect:/templates";
    }

    // ---- REST API (for AJAX loading in campaign create form) ----

    @GetMapping("/api/templates/{id}")
    @ResponseBody
    public ResponseEntity<EmailTemplate> getTemplateJson(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getById(id));
    }
}
