package com.developer.copilot.user.exception;

public class AdditionalProfileInformationNotFoundException extends RuntimeException {

    public AdditionalProfileInformationNotFoundException() {
        super("Additional profile information not found.");
    }

}
