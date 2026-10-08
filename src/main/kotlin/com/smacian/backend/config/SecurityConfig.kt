/*
 * SecurityConfig - The central security configuration of the application.
 *
 * Tells Spring Security:
 *   1. Which endpoints are PUBLIC (anyone: register, login, OTP...)
 *   2. Which are PROTECTED (must be logged in)
 *   3. How authentication works (stateless JWT, no server sessions)
 *   4. What happens when not authenticated (AuthEntryPointJwt)
 *
 * Modern Spring Security 6 style (no more WebSecurityConfigurerAdapter):
 * we return a @Bean SecurityFilterChain from a @Configuration class.
 */
package com.smacian.backend.config

import com.smacian.backend.security.AuthEntryPointJwt
import com.smacian.backend.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val unauthorizedHandler: AuthEntryPointJwt
) {

    /*
     * The main security recipe for how every HTTP request is handled.
     */
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // 1. Disable CSRF (we use stateless JWT, no browser cookies - not needed)
            .csrf { it.disable() }

            // 2. Enable CORS so mobile apps can call our API
            .cors { it.configurationSource(corsConfigurationSource()) }

            // 3. Return a clean 401 JSON when not authenticated
            .exceptionHandling { it.authenticationEntryPoint(unauthorizedHandler) }

            // 4. Stateless sessions - the JWT carries all auth info
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

            // 5. Authorization rules
            .authorizeHttpRequests { auth ->
                auth
                    // Change-password needs a JWT even though it lives under
                    // /api/auth/**. FIRST match wins, so this must stay ABOVE
                    // the public POST /api/auth/** rule below.
                    .requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()
                    // PUBLIC endpoints (no login required)
                    .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                    .requestMatchers("/api/terms", "/api/privacy").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()

                    // Photo BYTEA streams - the mobile <Image> component loads
                    // these WITHOUT a JWT header, so they must be public. Only
                    // these two exact GET paths are opened; everything else
                    // under /api/user stays authenticated.
                    .requestMatchers(HttpMethod.GET, "/api/user/profile/photo/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/user/cover/photo/**").permitAll()
                    // Newsfeed post images - same deal: mobile <Image> loads
                    // GET /api/feed/images/{id} without a JWT header.
                    .requestMatchers(HttpMethod.GET, "/api/feed/images/**").permitAll()

                    // PUBLIC API documentation (Swagger UI + OpenAPI json)
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                    // EVERYTHING ELSE requires login
                    .anyRequest().authenticated()
            }

            // 6. Run our JWT filter BEFORE Spring's login filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    /*
     * PasswordEncoder - how passwords are hashed.
     * BCrypt = industry standard. One-way, automatic salt, strength 10.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /*
     * Declared so Spring's AuthenticationManager is available if needed.
     */
    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    /*
     * CORS config. Mobile apps don't do CORS, so credentials are OFF and
     * any origin may call the public API. If a browser web app consumes
     * this API later, replace "*" with its exact https origin(s) here.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
            .apply {
                allowedOriginPatterns = listOf("*")
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type", "Accept")
                // NEVER true together with "*" - browsers reject that combo.
                allowCredentials = false
            }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}