package com.jobtracker.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.aiservice.dto.AnalyzeResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiAgentService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.api.url}")
    private String groqUrl;

    public AnalyzeResponseDTO analyze(String jdText, String resumeText) {
        boolean hasResume = resumeText != null && !resumeText.isBlank();

        String prompt = buildPrompt(jdText, resumeText, hasResume);

        try {
            String rawResponse = callGroq(prompt);
            return parseResponse(rawResponse, hasResume);
        } catch (Exception e) {
            log.error("Groq API call failed", e);
            return AnalyzeResponseDTO.builder()
                    .extractedSkills("AI analysis failed. Please try again.")
                    .fitScore(null)
                    .fitReason("Could not connect to AI service.")
                    .build();
        }
    }

    private String buildPrompt(String jdText, String resumeText, boolean hasResume) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a smart job application assistant. Analyze the following job description");
        if (hasResume) {
            prompt.append(" and resume");
        }
        prompt.append(".\n\n");

        prompt.append("JOB DESCRIPTION:\n").append(jdText).append("\n\n");

        if (hasResume) {
            prompt.append("RESUME:\n").append(resumeText).append("\n\n");
        }

        prompt.append("Respond ONLY in the following JSON format with no extra text, no markdown, no explanation:\n");
        prompt.append("{\n");
        prompt.append("  \"extractedSkills\": \"comma-separated list of required skills from the JD\",\n");

        if (hasResume) {
            prompt.append("  \"fitScore\": <integer 0-100 representing how well the resume matches the JD>,\n");
            prompt.append("  \"fitReason\": \"2-3 sentence explanation of the score — what matches and what is missing\"\n");
        } else {
            prompt.append("  \"fitScore\": null,\n");
            prompt.append("  \"fitReason\": null\n");
        }

        prompt.append("}");
        return prompt.toString();
    }

    private String callGroq(String prompt) {
        Map<String, Object> requestBody = Map.of(
            "model", "llama-3.3-70b-versatile",
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0.2,
            "max_tokens", 1024
        );

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = webClient.post()
                        .uri(groqUrl)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + groqApiKey)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.debug("Groq raw response: {}", response);
                return response;

            } catch (WebClientResponseException e) {
                int statusCode = e.getStatusCode().value();

                if (statusCode == 429 && attempt < maxRetries) {
                    long waitTime = 5000L * attempt;
                    log.warn("Groq rate limited (429). Attempt {}/{}. Retrying in {}ms...", attempt, maxRetries, waitTime);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                } else if (statusCode == 401) {
                    log.error("Groq API key invalid (401). Check your API key.");
                    throw e;
                } else if (statusCode == 400) {
                    log.error("Bad request to Groq (400). Body: {}", e.getResponseBodyAsString());
                    throw e;
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Groq API failed after " + maxRetries + " retries.");
    }

    private AnalyzeResponseDTO parseResponse(String rawResponse, boolean hasResume) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);

        // Groq uses OpenAI format: choices[0].message.content
        String text = root
                .path("choices").get(0)
                .path("message")
                .path("content")
                .asText();

        // Strip markdown code blocks if model wraps response
        text = text.replaceAll("```json", "").replaceAll("```", "").trim();

        log.debug("Parsed AI text response: {}", text);

        JsonNode parsed = objectMapper.readTree(text);

        AnalyzeResponseDTO response = new AnalyzeResponseDTO();
        response.setExtractedSkills(parsed.path("extractedSkills").asText(null));

        if (hasResume && !parsed.path("fitScore").isNull()) {
            response.setFitScore(parsed.path("fitScore").asInt());
            response.setFitReason(parsed.path("fitReason").asText(null));
        }

        return response;
    }
}
