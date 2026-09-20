/*
 * OpenApiConfiguration - Gives Swagger UI an "Authorize" button.
 *
 * The backend uses stateless JWT auth, so protected endpoints in the docs
 * need a place to paste the token. Registering a `bearerAuth` security
 * scheme makes Swagger UI render an Authorize dialog; the token you paste
 * is then attached as `Authorization: Bearer <token>` to every request.
 *
 * How to get a token (for testing):
 *   POST /api/auth/login  ->  { "token": "...", ... }
 *   POST /api/auth/register (new account) -> also returns a token.
 */
package com.smacian.backend.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    @Bean
    fun customOpenAPI(): OpenAPI {
        // The "Authorize" button uses scheme name "bearerAuth".
        val bearerScheme = SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")

        return OpenAPI()
            .info(
                Info()
                    .title("SMACian Backend API")
                    .version("1.0.0")
                    .description(
                        """
                        SMACian social app backend.

                        Public endpoints: /api/auth/** (OTP, register, login, password reset),
                        /api/terms, /api/privacy.

                        Everything under /api/user** and /api/users** requires a valid JWT.
                        Click the Authorize button and paste a token from
                        `POST /api/auth/login` or `POST /api/auth/register`.
                        """.trimIndent()
                    )
            )
            .components(
                Components().addSecuritySchemes(
                    "bearerAuth",
                    bearerScheme
                )
            )
            // Apply the lock icon / bearer requirement to every operation.
            .addSecurityItem(
                SecurityRequirement().addList("bearerAuth")
            )
    }
}