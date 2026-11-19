package sportmarket.config;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jwt.JWT;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

        //     @Value retrieves the JWT secret from your application configuration.

        // Your code then:
        // 1. Decodes the Base64-encoded secret.
        // 2. Verifies that it contains at least 32 bytes.
        // 3. Creates a SecretKey for HMAC-SHA256 signing.
    @Bean
    SecretKey jwtSigningKey(@Value("${app.jwt.secret}") String encodedKey) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64.");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 random decoded bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }


    // The JwtEncoder is responsible for signing and generating JWTs.
    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    //  A JWT has three parts:
    // Header

    // .
    // Payload

    // .
    // Signature


    // The header identifies the signing algorithm, the payload contains claims such as the user's ID and expiration,
    // and the signature helps prevent undetected tampering.
    @Bean
    JwtDecoder jwtDecoder(SecretKey key, @Value("${app.jwt.issuer}") String issuer) {
        if (issuer.isBlank()) { throw new IllegalStateException("JWT_ISSUER must not be blank."); }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            try {
                if (Long.parseLong(jwt.getSubject()) > 0 && jwt.getExpiresAt() != null
                        && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(Instant.now())
                        && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid subjects are rejected without exposing claim values.
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required token claims are invalid.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(issuer), requiredClaims));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors, Environment environment) throws Exception {
        if (environment.matchesProfiles("production")) {
            // Only the packaged React routes/assets are public. API rules stay below.
            http.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET,
                    "/", "/index.html", "/assets/**", "/favicon.svg", "/login", "/register",
                    "/my-listings", "/listings/new", "/listings/{id}", "/listings/{id}/edit").permitAll());
        }
        return http
                // Bearer tokens are sent explicitly in Authorization, never in cookies.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/listings", "/api/listings/{id}", "/api/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/listings/{id}/images", "/api/listing-images/{imageId}/content").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/listings/{id}/images").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/listings/{listingId}/images/{imageId}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/me", "/api/users/me/listings").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/listings").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/listings/{id}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/listings/{id}").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/listings/{id}/status").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(resource -> resource
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        List<String> allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).toList();
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || origin.contains("*") || uri.getUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain exact HTTP(S) origins without paths.");
            }
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
