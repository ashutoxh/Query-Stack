package edu.neu.info7255.exception;

public class ETagMismatchException extends RuntimeException {
    public ETagMismatchException(String message) {
        super(message);
    }
}