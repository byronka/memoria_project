package com.renomad.inmra.featurelogic.persons;

public class CircularLoopException extends RuntimeException {

    public CircularLoopException(String message) {
        super(message);
    }
}
