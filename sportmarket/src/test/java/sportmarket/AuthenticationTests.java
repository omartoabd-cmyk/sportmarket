package sportmarket;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import sportmarket.entity.User;
import sportmarket.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Value("${app.jwt.issuer}") String issuer;
    private User user;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(new User("Alex Seller", "alex@example.test", passwords.encode("SecurePass123!")));
    }

    @Test
    void registersNormalizesAndHashesPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "  Sam Buyer  ",
                                "email", "  SAM@Example.test  ", "password", "SecurePass123!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fullName").value("Sam Buyer"))
                .andExpect(jsonPath("$.email").value("sam@example.test"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        var saved = users.findByEmail("sam@example.test").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("SecurePass123!").startsWith("$2");
        assertThat(passwords.matches("SecurePass123!", saved.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateNormalizedEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Another Person",
                                "email", "  ALEX@EXAMPLE.TEST ", "password", "SecurePass123!"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409));
        assertThat(users.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "1234567", "                                                                        ",
            "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstu",
            "\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c\u754c"})
    void rejectsInvalidPasswordsWithoutEchoingValues(String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Sam", "email", "sam@example.test",
                                "password", password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("password")).andReturn();
        if (!password.isEmpty()) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(password);
        }
    }

    @Test
    void rejectsInvalidEmailBlankNameOversizedFieldsAndMissingInput() throws Exception {
        for (Map<String, String> request : java.util.List.of(
                Map.of("fullName", "Sam", "email", "invalid-email", "password", "SecurePass123!"),
                Map.of("fullName", "   ", "email", "sam@example.test", "password", "SecurePass123!"),
                Map.of("fullName", "s".repeat(256), "email", "sam@example.test", "password", "SecurePass123!"),
                Map.of("fullName", "Sam", "email", "s".repeat(250) + "@example.test", "password", "SecurePass123!"),
                Map.<String, String>of())) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(request)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void loginIssuesSignedTokenWithRequiredClaimsAndNoSecrets() throws Exception {
        String token = login();
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(issuer);
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(ChronoUnit.SECONDS.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(3600);
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("sub", "iss", "iat", "exp");
    }

    @Test
    void unknownEmailAndWrongPasswordHaveIdentical401Responses() throws Exception {
        String wrongPassword = invalidLogin("alex@example.test", "Incorrect123!");
        String unknownEmail = invalidLogin("missing@example.test", "Incorrect123!");
        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void allowsPublicEndpointsWithoutToken() throws Exception {
        mvc.perform(get("/api/listings")).andExpect(status().isOk());
        mvc.perform(get("/api/listings/9223372036854775807")).andExpect(status().isNotFound());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test
    void requiresTokenForCurrentUser() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/users/me"));
    }

    @Test
    void currentUserUsesValidatedSubjectAndExcludesHash() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("alex@example.test"))
                .andExpect(jsonPath("$.fullName").value("Alex Seller"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void deletedAccountReturns401() throws Exception {
        String token = login();
        users.delete(user);
        users.flush();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsMalformedTamperedExpiredWrongIssuerAndMissingClaims() throws Exception {
        Instant now = Instant.now();
        String token = login();
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
        for (String invalid : java.util.List.of("not-a-jwt", tampered,
                sign(user.getId().toString(), issuer, now.minusSeconds(3601), now.minusSeconds(1)),
                sign(user.getId().toString(), "wrong-issuer", now, now.plusSeconds(3600)),
                sign("not-a-user-id", issuer, now, now.plusSeconds(3600)),
                sign("0", issuer, now, now.plusSeconds(3600)),
                sign(user.getId().toString(), issuer, now, null))) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("Authentication required or invalid."));
        }
    }

    @Test
    void deniesNonAllowlistedRoutesAndMethods() throws Exception {
        String token = login();
        mvc.perform(get("/api/not-allowed").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/auth/login").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/not-allowed")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsOnlyConfiguredOriginsAndHeaders() throws Exception {
        mvc.perform(options("/api/users/me").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/users/me").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private String login() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "  ALEX@EXAMPLE.TEST  ",
                                "password", "SecurePass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private String invalidLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andReturn().getResponse().getContentAsString();
    }

    private String sign(String subject, String tokenIssuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject(subject).issuer(tokenIssuer).issuedAt(issuedAt);
        if (expiresAt != null) { claims.expiresAt(expiresAt); }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
    }
}
