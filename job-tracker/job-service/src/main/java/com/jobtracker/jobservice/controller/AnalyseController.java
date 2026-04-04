package com.jobtracker.jobservice.controller;

import com.jobtracker.jobservice.dto.AiResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AnalyseController {

    private final WebClient webClient;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    // Serve the analyse page
    @GetMapping("/analyse")
    public String analysePage() {
        return "analyse";
    }

    // REST proxy — called by the page via fetch()
    @PostMapping("/api/analyse")
    @ResponseBody
    public ResponseEntity<AiResponseDTO> analyse(@RequestBody Map<String, String> request) {
        log.info("Analyse request received — JD length: {}", 
                 request.getOrDefault("jdText", "").length());

        AiResponseDTO response = webClient.post()
                .uri(aiServiceUrl + "/api/ai/analyze")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiResponseDTO.class)
                .block();

        return ResponseEntity.ok(response);
    }
}
