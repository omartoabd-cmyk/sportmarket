package sportmarket.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sportmarket.dto.UserResponse;
import sportmarket.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) { this.users = users; }

    public UserResponse getCurrentUser(String subject) {
        Long id;
        try {
            id = Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException("Invalid authentication.");
        }
        var user = users.findById(id)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication."));
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail());
    }
}
