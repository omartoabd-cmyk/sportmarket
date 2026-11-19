package sportmarket;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import sportmarket.dto.RegisterRequest;
import sportmarket.entity.User;
import sportmarket.exception.DuplicateEmailException;
import sportmarket.repository.UserRepository;
import sportmarket.service.AuthService;
import sportmarket.service.TokenService;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTests {
    @Test
    void translatesConcurrentUniqueConflictEvenAfterExistenceCheckPasses() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(passwords.encode("SecurePass123!")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "Unique violation", new SQLException("Duplicate", "23505")));
        AuthService service = new AuthService(users, passwords, mock(AuthenticationManager.class), mock(TokenService.class));
        assertThatThrownBy(() -> service.register(new RegisterRequest("Sam", "sam@example.test", "SecurePass123!")))
                .isInstanceOf(DuplicateEmailException.class);
        verify(users).existsByEmail("sam@example.test");
    }

    @Test
    void doesNotMislabelOtherDatabaseFailuresAsDuplicateEmail() {
        UserRepository users = mock(UserRepository.class);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("Other constraint",
                new SQLException("Check constraint", "23514"));
        when(users.saveAndFlush(any(User.class))).thenThrow(failure);
        AuthService service = new AuthService(users, mock(PasswordEncoder.class),
                mock(AuthenticationManager.class), mock(TokenService.class));
        assertThatThrownBy(() -> service.register(new RegisterRequest("Sam", "sam@example.test", "SecurePass123!")))
                .isSameAs(failure);
    }
}
