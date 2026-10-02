package com.jobtracker.jobservice.controller;

import com.jobtracker.jobservice.dto.JobApplicationDTO;
import com.jobtracker.jobservice.entity.JobApplication;
import com.jobtracker.jobservice.enums.ApplicationStatus;
import com.jobtracker.jobservice.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Pure JSON REST API for the Angular frontend.
 * Sits alongside JobApplicationController (which still serves the Thymeleaf UI at /jobs).
 * Reuses the exact same service + repository — no duplicated business logic.
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobApplicationRestController {

    private final JobApplicationService service;

    // GET /api/jobs - list all applications
    @GetMapping
    public List<JobApplication> getAll() {
        return service.getAllApplications();
    }

    // GET /api/jobs/{id} - single application
    @GetMapping("/{id}")
    public ResponseEntity<JobApplication> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // POST /api/jobs - create new application (AI analysis runs automatically if jdText present)
    @PostMapping
    public ResponseEntity<JobApplication> create(@Valid @RequestBody JobApplicationDTO dto) {
        JobApplication saved = service.save(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // PUT /api/jobs/{id}/status - update status
    @PutMapping("/{id}/status")
    public ResponseEntity<JobApplication> updateStatus(@PathVariable Long id,
                                                       @RequestParam ApplicationStatus status) {
        return ResponseEntity.ok(service.updateStatus(id, status));
    }

    // PUT /api/jobs/{id}/followup - mark follow-up done
    @PutMapping("/{id}/followup")
    public ResponseEntity<JobApplication> markFollowUpDone(@PathVariable Long id) {
        return ResponseEntity.ok(service.markFollowUpDone(id));
    }

    // DELETE /api/jobs/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // GET /api/jobs/pending-followups
    @GetMapping("/pending-followups")
    public List<JobApplication> getPendingFollowUps() {
        return service.getPendingFollowUps();
    }

    // GET /api/jobs/stats - dashboard stats
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return service.getDashboardStats();
    }
}