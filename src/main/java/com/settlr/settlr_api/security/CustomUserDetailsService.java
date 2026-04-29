package com.settlr.settlr_api.security;

import com.settlr.settlr_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads a User from the database by email so that Spring Security's
 * DaoAuthenticationProvider can verify the BCrypt password during login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("[AUTH] Loading user by email={}", email);

        return userRepository.findByEmail(email)
                .map(user -> org.springframework.security.core.userdetails.User.builder()
                        .username(user.getEmail())
                        .password(user.getPasswordHash())
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                        .build())
                .orElseThrow(() -> {
                    log.warn("[AUTH] No user found for email={}", email);
                    return new UsernameNotFoundException("No user found with email: " + email);
                });
    }
}
