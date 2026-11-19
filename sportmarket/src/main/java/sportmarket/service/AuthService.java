package sportmarket.service;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sportmarket.dto.*;
import sportmarket.entity.User;
import sportmarket.exception.DuplicateEmailException;
import sportmarket.repository.UserRepository;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }
        User user = new User(request.fullName(), request.email(), passwordEncoder.encode(request.password()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // A concurrent registration can win after the existence check.
            // SQLSTATE 23505 is unique_violation in PostgreSQL (and H2).
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new DuplicateEmailException();
                }
            }
            throw exception;
        }
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        return tokens.issueToken(authentication.getName());
    }
}
