package com.developer.copilot.user.exception;

public class UserProfileNotFoundException extends RuntimeException {

    public UserProfileNotFoundException() {
        super("User profile not found.");
    }

}
