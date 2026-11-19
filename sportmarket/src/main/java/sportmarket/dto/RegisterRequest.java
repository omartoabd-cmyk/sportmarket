package sportmarket.dto;

import java.util.Locale;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sportmarket.dto.validation.PasswordByteLimit;

public record RegisterRequest(
        @NotBlank @Size(max = 255)
         String fullName,
        @NotBlank @Email @Size(max = 255)
         String email,
        @NotBlank @Size(min = 8, max = 72) @PasswordByteLimit
         String password)
          {

    public RegisterRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() { return "RegisterRequest[redacted]"; }
}
