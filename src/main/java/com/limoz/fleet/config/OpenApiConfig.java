package com.limoz.fleet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI fleetOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LIMOZ Rwanda - Fleet Operations API")
                        .description("""
                                REST API for the LIMOZ Rwanda Fleet Operations Management System.

                                Authenticate with `POST /api/v1/auth/login`, then send the access token as
                                `Authorization: Bearer <token>`. Every endpoint is protected by permission-based
                                authorization enforced on the server. Error responses share a single structure
                                (see `ApiError`). List endpoints are paginated: `page`, `size`, `sort=field,asc|desc`.
                                """)
                        .version("v1")
                        .contact(new Contact().name("LIMOZ Rwanda Ltd - IT").email("it@limoz.rw")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
