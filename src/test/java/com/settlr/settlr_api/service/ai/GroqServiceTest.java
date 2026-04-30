package com.settlr.settlr_api.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.settlr.settlr_api.config.GroqProperties;
import com.settlr.settlr_api.exception.GroqException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes", "null"})
class GroqServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private GroqProperties groqProperties;

    @Mock
    private ObjectMapper objectMapper;

    private GroqService groqService;

    @BeforeEach
    void setUp() {
        groqService = new GroqService(webClient, groqProperties, objectMapper);

        // Setup the fluent WebClient mock chain (basic)
        lenient().when(webClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void chat_Success() {
        when(groqProperties.getModel()).thenReturn("llama3-8b-8192");
        
        Map<String, Object> mockResponse = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", "Hello from Groq!"))
                )
        );

        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(mockResponse));

        String result = groqService.chat("System", "User");

        assertEquals("Hello from Groq!", result);
    }

    @Test
    void chat_TimeoutHandling() {
        when(groqProperties.getModel()).thenReturn("llama3-8b-8192");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(new TimeoutException("Timeout")));

        GroqException exception = assertThrows(GroqException.class, () -> 
                groqService.chat("System", "User")
        );

        assertTrue(exception.getMessage().contains("timed out"));
    }

    @Test
    void chatAsJson_ParseFailure() throws Exception {
        when(groqProperties.getModel()).thenReturn("llama3-8b-8192");
        
        Map<String, Object> mockResponse = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", "Invalid JSON data"))
                )
        );
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(mockResponse));

        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenThrow(new JsonProcessingException("Parse error") {});

        GroqException exception = assertThrows(GroqException.class, () -> 
                groqService.chatAsJson("System", "User", Map.class)
        );

        assertTrue(exception.getMessage().contains("Failed to parse JSON response"));
        assertTrue(exception.getMessage().contains("Invalid JSON data"));
    }
}
