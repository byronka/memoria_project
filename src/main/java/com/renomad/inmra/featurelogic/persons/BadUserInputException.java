package com.renomad.inmra.featurelogic.persons;

/**
 * This is an exception used to quickly bubble up to code
 * closer to the endpoint method about complaints happening
 * further down in the guts.
 */
public class BadUserInputException extends RuntimeException {

    public String htmlMessage;

    public BadUserInputException(String message) {
        this(message, null);
    }

    public BadUserInputException(String message, String htmlMessage) {
        super(message);
        this.htmlMessage = htmlMessage;
    }
}
