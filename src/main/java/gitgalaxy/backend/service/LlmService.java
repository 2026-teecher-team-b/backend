package gitgalaxy.backend.service;

import gitgalaxy.backend.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final RagProperties props;
    private final RestTemplate restTemplate;

    public String chat(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getGroqApiKey());

        Map<String, Object> body = Map.of(
                "model", props.getGroqModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.2
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(GROQ_URL, request, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            throw new RuntimeException("Groq LLM 요청 실패: " + e.getMessage(), e);
        }
    }
}
