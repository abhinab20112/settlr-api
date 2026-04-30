package com.settlr.settlr_api.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlr.settlr_api.config.GroqProperties;
import com.settlr.settlr_api.exception.GroqException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class GroqService {

    private final WebClient groqWebClient;
    private final GroqProperties groqProperties;
    private final ObjectMapper objectMapper;

    /**
     * Sends a chat completion request to Groq API and returns the raw content string.
     */
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", groqProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "max_tokens", 1000,
                "temperature", 0.3
        );

        try {
            Map<?, ?> response = groqWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || !response.containsKey("choices")) {
                throw new GroqException("Invalid response format from Groq API");
            }

            List<?> choices = (List<?>) response.get("choices");
            if (choices.isEmpty()) {
                throw new GroqException("Groq API returned no choices");
            }

            Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
            
            if (message == null || !message.containsKey("content")) {
                throw new GroqException("Groq API returned choice without message content");
            }

            return (String) message.get("content");

        } catch (WebClientResponseException e) {
            log.error("Groq API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GroqException("Groq API request failed: " + e.getStatusCode(), e);
        } catch (Exception e) {
            if (e.getCause() instanceof TimeoutException || e instanceof TimeoutException) {
                log.error("Groq API request timed out after 10 seconds");
                throw new GroqException("Groq API request timed out", e);
            }
            if (e instanceof GroqException) {
                throw (GroqException) e;
            }
            log.error("Unexpected error calling Groq API", e);
            throw new GroqException("Unexpected error calling Groq API: " + e.getMessage(), e);
        }
    }

    /**
     * Calls chat(), strips any markdown backticks, and deserializes the response.
     */
    public <T> T chatAsJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        String rawResponse = chat(systemPrompt, userPrompt);
        
        // Strip markdown backticks if Groq wrapped the JSON
        String jsonContent = rawResponse.trim();
        if (jsonContent.startsWith("```json")) {
            jsonContent = jsonContent.substring(7);
        } else if (jsonContent.startsWith("```")) {
            jsonContent = jsonContent.substring(3);
        }
        
        if (jsonContent.endsWith("```")) {
            jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
        }
        
        jsonContent = jsonContent.trim();

        try {
            return objectMapper.readValue(jsonContent, responseType);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Groq response to JSON. Raw response: {}", rawResponse);
            throw new GroqException("Failed to parse JSON response. Raw response: " + rawResponse, e);
        }
    }
}
