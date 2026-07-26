package com.developer.copilot.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {

        final String securitySchemeName = "Bearer Authentication";

        return new OpenAPI()

                .info(new Info()
                        .title("Copilot REST API")
                        .description("Backend APIs for Copilot Application")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Bhadri Venkata Nageswara")
                                .email("your-email@example.com"))
                        .license(new License()
                                .name("Apache 2.0")))

                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(securitySchemeName))

                .schemaRequirement(
                        securitySchemeName,

                        new SecurityScheme()

                                .name(securitySchemeName)

                                .type(SecurityScheme.Type.HTTP)

                                .scheme("bearer")

                                .bearerFormat("JWT"));

    }

}