package com.jobtracker.jobservice.service;

import com.jobtracker.jobservice.dto.AiResponseDTO;
import com.jobtracker.jobservice.dto.JobApplicationDTO;
import com.jobtracker.jobservice.entity.JobApplication;
import com.jobtracker.jobservice.entity.User;
import com.jobtracker.jobservice.enums.ApplicationSource;
import com.jobtracker.jobservice.enums.ApplicationStatus;
import com.jobtracker.jobservice.exception.AiServiceException;
import com.jobtracker.jobservice.exception.ResourceNotFoundException;
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
    private final CurrentUserService currentUser;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    /** Only the logged-in user's own applications. */
    public List<JobApplication> getAllApplications() {
        return repository.findByUser(currentUser.get());
    }

    public Optional<JobApplication> getById(Long id) {
        return repository.findByIdAndUser(id, currentUser.get());
    }

    /** Finds an application only if it belongs to the logged-in user, otherwise 404. */
    private JobApplication getOwned(Long id) {
        return repository.findByIdAndUser(id, currentUser.get())
                .orElseThrow(() -> new ResourceNotFoundException("Job application", id));
    }

    public JobApplication save(JobApplicationDTO dto) {
        JobApplication job = JobApplication.builder()
                .user(currentUser.get())
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

    public JobApplication update(Long id, JobApplicationDTO dto) {
        JobApplication job = getOwned(id);

        job.setCompany(dto.getCompany());
        job.setRole(dto.getRole());
        job.setJdText(dto.getJdText());
        job.setResumeText(dto.getResumeText());
        job.setStatus(dto.getStatus() != null ? dto.getStatus() : job.getStatus());
        job.setSource(dto.getSource() != null ? dto.getSource() : job.getSource());
        job.setHrName(dto.getHrName());
        job.setHrEmail(dto.getHrEmail());
        job.setMailSentDate(dto.getMailSentDate());
        job.setFollowUpDate(dto.getFollowUpDate());
        job.setFollowUpDone(dto.getFollowUpDone() != null ? dto.getFollowUpDone() : job.getFollowUpDone());
        job.setNotes(dto.getNotes());

        return repository.save(job);
    }

    public JobApplication reAnalyse(Long id) {
        JobApplication job = getOwned(id);

        if (job.getJdText() == null || job.getJdText().isBlank()) {
            log.warn("Cannot re-analyse - no JD text for job id: {}", id);
            return job;
        }

        try {
            AiResponseDTO aiResponse = callAiService(job.getJdText(), job.getResumeText());
            job.setExtractedSkills(aiResponse.getExtractedSkills());
            job.setFitScore(aiResponse.getFitScore());
            job.setFitReason(aiResponse.getFitReason());
            job = repository.save(job);
            log.info("Re-analysis complete for job id: {} - fitScore={}", id, aiResponse.getFitScore());
        } catch (Exception e) {
            log.error("AI re-analysis failed for job id: {}", id, e);
            throw new AiServiceException("AI analysis failed: " + e.getMessage(), e);
        }

        return job;
    }

    public JobApplication updateStatus(Long id, ApplicationStatus status) {
        JobApplication job = getOwned(id);
        job.setStatus(status);
        return repository.save(job);
    }

    public JobApplication markFollowUpDone(Long id) {
        JobApplication job = getOwned(id);
        job.setFollowUpDone(true);
        return repository.save(job);
    }

    public void delete(Long id) {
        repository.delete(getOwned(id));
    }

    public List<JobApplication> getPendingFollowUps() {
        return repository.findPendingFollowUpsByUser(currentUser.get(), LocalDate.now());
    }

    public Map<String, Object> getDashboardStats() {
        User user = currentUser.get();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", repository.countByUser(user));
        stats.put("applied", repository.countByUserAndStatus(user, ApplicationStatus.APPLIED));
        stats.put("interview", repository.countByUserAndStatus(user, ApplicationStatus.INTERVIEW));
        stats.put("offer", repository.countByUserAndStatus(user, ApplicationStatus.OFFER));
        stats.put("rejected", repository.countByUserAndStatus(user, ApplicationStatus.REJECTED));
        stats.put("avgFitScore", repository.findAverageFitScoreByUser(user));
        stats.put("pendingFollowUps",
                repository.findPendingFollowUpsByUser(user, LocalDate.now()).size());
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