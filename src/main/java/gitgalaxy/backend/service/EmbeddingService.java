package gitgalaxy.backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import gitgalaxy.backend.config.VertexAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmbeddingService {

    static final int DIMS = 768;

    private final VertexAiProperties props;
    private final RestTemplate restTemplate;
    private GoogleCredentials credentials;

    public EmbeddingService(VertexAiProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
        try {
            String keyPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (keyPath != null) {
                credentials = ServiceAccountCredentials
                        .fromStream(new FileInputStream(keyPath))
                        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
                log.info("Vertex AI 인증 초기화 완료");
            } else {
                log.warn("GOOGLE_APPLICATION_CREDENTIALS 미설정 → 임베딩 비활성화");
            }
        } catch (Exception e) {
            log.warn("Vertex AI 인증 초기화 실패: {}", e.getMessage());
        }
    }

    public boolean isConfigured() {
        return credentials != null;
    }

    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        String input = text.length() > 8000 ? text.substring(0, 8000) : text;
        try {
            credentials.refreshIfExpired();
            String token = credentials.getAccessToken().getTokenValue();

            String url = String.format(
                    "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:predict",
                    props.getLocation(), props.getProject(), props.getLocation(), props.getEmbeddingModel()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Content-Type", "application/json");

            Map<String, Object> body = Map.of("instances", List.of(Map.of("content", input)));
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            Map<String, Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class).getBody();
            List<Map<String, Object>> predictions = (List<Map<String, Object>>) response.get("predictions");
            Map<String, Object> embeddings = (Map<String, Object>) predictions.get(0).get("embeddings");
            List<Number> vec = (List<Number>) embeddings.get("values");

            float[] result = new float[vec.size()];
            for (int i = 0; i < vec.size(); i++) {
                result[i] = vec.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Vertex AI 임베딩 실패: " + e.getMessage(), e);
        }
    }

    public static String toVectorString(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').append("::vector").toString();
    }
}
