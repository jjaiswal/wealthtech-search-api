package com.neviswealth.searchapi.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API metadata and snake_case schema naming for Swagger/OpenAPI docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("WealthTech Search API")
                .version("1.0.0")
                .description("Search across clients (lexical) and documents (semantic vector "
                        + "search) for a WealthTech advisor platform."));
    }

    /** Ensures the OpenAPI schema uses snake_case property names matching the actual JSON output. */
    @Bean
    public ModelResolver snakeCaseModelResolver(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new ModelResolver(mapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE));
    }
}
