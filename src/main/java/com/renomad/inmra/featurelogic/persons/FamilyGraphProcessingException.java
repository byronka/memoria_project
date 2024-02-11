package com.renomad.inmra.featurelogic.persons;

public class FamilyGraphProcessingException extends RuntimeException {
    public FamilyGraphProcessingException(String message) {
        super(message);
    }

    public FamilyGraphProcessingException(String message, Exception ex) {
        super(message, ex);
    }
}
