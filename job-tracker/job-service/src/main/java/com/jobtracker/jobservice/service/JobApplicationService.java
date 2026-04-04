package com.jobtracker.jobservice.service;

import com.jobtracker.jobservice.dto.AiResponseDTO;
import com.jobtracker.jobservice.dto.JobApplicationDTO;
import com.jobtracker.jobservice.entity.JobApplication;
import com.jobtracker.jobservice.enums.ApplicationSource;
import com.jobtracker.jobservice.enums.ApplicationStatus;
import com.jobtracker.jobservice.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobApplicationService {

    private final JobApplicationRepository repository;
    private final WebClient webClient;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public List<JobApplication> getAllApplications() {
        return repository.findAll();
    }

    public Optional<JobApplication> getById(Long id) {
        return repository.findById(id);
    }

    public JobApplication save(JobApplicationDTO dto) {
        JobApplication job = JobApplication.builder()
                .company(dto.getCompany())
                .role(dto.getRole())
                .jdText(dto.getJdText())
                .resumeText(dto.getResumeText())
                .status(dto.getStatus() != null ? dto.getStatus() : ApplicationStatus.APPLIED)
                .source(dto.getSource() != null ? dto.getSource() : ApplicationSource.JOB_PORTAL)
                .hrName(dto.getHrName())
                .hrEmail(dto.getHrEmail())
                .mailSentDate(dto.getMailSentDate())
                .followUpDate(dto.getFollowUpDate())
                .followUpDone(dto.getFollowUpDone() != null ? dto.getFollowUpDone() : false)
                .notes(dto.getNotes())
                .build();

        JobApplication saved = repository.save(job);

        // If JD text is provided, call AI service to parse and score
        if (dto.getJdText() != null && !dto.getJdText().isBlank()) {
            try {
                AiResponseDTO aiResponse = callAiService(dto.getJdText(), dto.getResumeText());
                saved.setExtractedSkills(aiResponse.getExtractedSkills());
                saved.setFitScore(aiResponse.getFitScore());
                saved.setFitReason(aiResponse.getFitReason());
                saved = repository.save(saved);
            } catch (Exception e) {
                log.error("AI service call failed for job id: {}", saved.getId(), e);
            }
        }

        return saved;
    }

    public JobApplication updateStatus(Long id, ApplicationStatus status) {
        JobApplication job = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job application not found: " + id));
        job.setStatus(status);
        return repository.save(job);
    }

    public JobApplication markFollowUpDone(Long id) {
        JobApplication job = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job application not found: " + id));
        job.setFollowUpDone(true);
        return repository.save(job);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<JobApplication> getPendingFollowUps() {
        return repository.findPendingFollowUps(LocalDate.now());
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", repository.count());
        stats.put("applied", repository.countByStatus(ApplicationStatus.APPLIED));
        stats.put("interview", repository.countByStatus(ApplicationStatus.INTERVIEW));
        stats.put("offer", repository.countByStatus(ApplicationStatus.OFFER));
        stats.put("rejected", repository.countByStatus(ApplicationStatus.REJECTED));
        stats.put("avgFitScore", repository.findAverageFitScore());
        stats.put("pendingFollowUps", repository.findPendingFollowUps(LocalDate.now()).size());
        return stats;
    }

    private AiResponseDTO callAiService(String jdText, String resumeText) {
        Map<String, String> request = new HashMap<>();
        request.put("jdText", jdText);
        request.put("resumeText", resumeText != null ? resumeText : "");

        return webClient.post()
                .uri(aiServiceUrl + "/api/ai/analyze")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiResponseDTO.class)
                .block();
    }
}
