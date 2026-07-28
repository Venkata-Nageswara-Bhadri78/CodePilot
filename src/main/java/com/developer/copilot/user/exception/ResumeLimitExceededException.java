package com.developer.copilot.user.exception;

public class ResumeLimitExceededException extends RuntimeException {

    public ResumeLimitExceededException(int max) {
        super("Maximum resume limit reached : " + max);
    }

}