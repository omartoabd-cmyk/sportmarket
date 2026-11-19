package sportmarket.service;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import sportmarket.dto.AuthResponse;

@Service
public class TokenService {
    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private final JwtEncoder encoder;
    private final String issuer;

    public TokenService(JwtEncoder encoder, @Value("${app.jwt.issuer}") String issuer) {
        this.encoder = encoder;
        this.issuer = issuer;
    }

    public AuthResponse issueToken(String userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId).issuer(issuer).issuedAt(now)
                .expiresAt(now.plusSeconds(TOKEN_LIFETIME_SECONDS)).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", TOKEN_LIFETIME_SECONDS);
    }
}
