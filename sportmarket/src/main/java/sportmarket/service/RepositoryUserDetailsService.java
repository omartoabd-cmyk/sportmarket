package sportmarket.service;

import java.util.Locale;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sportmarket.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class RepositoryUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public RepositoryUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        var user = users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
        // Use the database ID as the authenticated principal's username.
        return User.withUsername(user.getId().toString())
                .password(user.getPasswordHash()).authorities(java.util.List.of()).build();
    }
}
