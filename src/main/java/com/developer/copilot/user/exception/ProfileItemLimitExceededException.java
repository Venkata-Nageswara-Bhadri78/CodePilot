package com.developer.copilot.user.exception;

public class ProfileItemLimitExceededException extends RuntimeException {

    public ProfileItemLimitExceededException(String itemName, int max) {
        super("Maximum of " + max + " " + itemName + " records allowed.");
    }
}
