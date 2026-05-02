package gitgalaxy.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gitGalaxyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("GitGalaxy API")
                        .description("""
                                GitHub 오픈소스 활동 시각화 플랫폼 API 명세

                                **인증이 필요한 API:** `/users/me/**` 엔드포인트는 GitHub OAuth 로그인 후 사용 가능

                                **GitHub 로그인:** 브라우저에서 `/oauth2/authorization/github` 접속
                                """)
                        .version("v1.0.0"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발 서버")
                ));
    }
}
