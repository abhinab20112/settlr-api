package com.settlr.settlr_api.service.impl;

import com.settlr.settlr_api.dto.auth.RegisterRequest;
import com.settlr.settlr_api.entity.User;
import com.settlr.settlr_api.exception.EmailAlreadyExistsException;
import com.settlr.settlr_api.repository.UserRepository;
import com.settlr.settlr_api.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User register(RegisterRequest request) {
        log.info("[REGISTER] Attempting registration | email={}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            log.warn("[REGISTER] Duplicate email rejected | email={}", request.email());
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        User saved = userRepository.save(user);

        log.info("[REGISTER] User created successfully | id={} | email={}", saved.getId(), saved.getEmail());
        return saved;
    }
}

