package sportmarket.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() { return "AuthResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]"; }
}
