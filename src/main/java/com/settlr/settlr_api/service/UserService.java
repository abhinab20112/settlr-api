package com.settlr.settlr_api.service;

import com.settlr.settlr_api.dto.auth.RegisterRequest;
import com.settlr.settlr_api.entity.User;

public interface UserService {

    /**
     * Registers a new user with a BCrypt-hashed password.
     *
     * @param request validated registration payload
     * @return the persisted User entity
     * @throws com.settlr.settlr_api.exception.EmailAlreadyExistsException if email is taken
     */
    User register(RegisterRequest request);
}
