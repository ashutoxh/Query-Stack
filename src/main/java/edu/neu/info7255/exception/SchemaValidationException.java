package edu.neu.info7255.exception;

import java.util.List;

/**
 * The type Schema validation exception.
 */
public class SchemaValidationException extends RuntimeException {
    private final List<String> validationErrors;

    /**
     * Instantiates a new Schema validation exception.
     *
     * @param message          the message
     * @param validationErrors the validation errors
     */
    public SchemaValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }

    /**
     * Gets validation errors.
     *
     * @return the validation errors
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
