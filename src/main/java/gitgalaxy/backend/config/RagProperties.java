package gitgalaxy.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag")
@Data
public class RagProperties {

    private String groqApiKey;
    private String groqModel = "llama-3.3-70b-versatile";
    private String embeddingUrl = "http://localhost:8100/embed";
}
