package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import com.decentralized.degree.vault.decentralizeddegreevault.dto.User;
import com.decentralized.degree.vault.decentralizeddegreevault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Custom implementation of Spring Security's {@link UserDetailsService}.
 * <p>
 * Loads a {@link User} from the database by email and converts it into a
 * Spring Security {@link UserDetails} object for authentication.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Locates the user by email address (used as the "username" in this system).
     *
     * @param email the email identifying the user
     * @return a fully populated {@link UserDetails} instance
     * @throws UsernameNotFoundException if no user is found with the given email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email
                ));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole()))
        );
    }
}
