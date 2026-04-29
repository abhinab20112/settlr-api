package com.settlr.settlr_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when trying to add a user who is already a member of the group.
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class UserAlreadyMemberException extends RuntimeException {

    public UserAlreadyMemberException(String email, String groupName) {
        super("User '" + email + "' is already a member of group '" + groupName + "'");
    }
}
