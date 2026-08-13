package com.developer.copilot.user.exception;

public class DuplicateUserProfileException extends RuntimeException {

    public DuplicateUserProfileException() {
        super("A profile already exists for this user.");
    }

}
