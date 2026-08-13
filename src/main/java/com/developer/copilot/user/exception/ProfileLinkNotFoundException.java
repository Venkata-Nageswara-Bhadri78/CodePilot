package com.developer.copilot.user.exception;

public class ProfileLinkNotFoundException extends RuntimeException {

    public ProfileLinkNotFoundException() {
        super("Profile link not found.");
    }

}
