package com.jobtracker.aiservice.controller;

import com.jobtracker.aiservice.dto.AnalyzeRequestDTO;
import com.jobtracker.aiservice.dto.AnalyzeResponseDTO;
import com.jobtracker.aiservice.service.GeminiAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiAgentController {

    private final GeminiAgentService geminiAgentService;

    /**
     * Main endpoint called by job-service.
     * Accepts JD text and optional resume text.
     * Returns extracted skills + fit score + fit reason.
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponseDTO> analyze(@RequestBody AnalyzeRequestDTO request) {
        log.info("Received analyze request for JD of length: {}", 
                 request.getJdText() != null ? request.getJdText().length() : 0);

        AnalyzeResponseDTO response = geminiAgentService.analyze(
                request.getJdText(),
                request.getResumeText()
        );

        return ResponseEntity.ok(response);
    }

    // Health check
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Agent Service is running");
    }
}
