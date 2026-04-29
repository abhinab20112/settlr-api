package com.settlr.settlr_api.service;

import com.settlr.settlr_api.dto.auth.LoginRequest;
import com.settlr.settlr_api.dto.auth.LoginResponse;

public interface AuthService {

    /**
     * Validates credentials and returns a signed JWT on success.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException if wrong password
     * @throws org.springframework.security.core.userdetails.UsernameNotFoundException if email not found
     */
    LoginResponse login(LoginRequest request);
}
