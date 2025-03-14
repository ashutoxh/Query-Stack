package edu.neu.info7255.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * The type Api response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomResponse {
    private final HttpStatus status;
    private final String message;
    private final Object data;
    private final List<String> errors; // Changed to List<String>

    // Constructor for success responses without data
    public CustomResponse(HttpStatus status, String message) {
        this(status, message, null, null);
    }

    // Constructor for success responses with data
    public CustomResponse(HttpStatus status, String message, Object data) {
        this(status, message, data, null);
    }

    // Constructor for error responses with validation errors
    public CustomResponse(HttpStatus status, String message, List<String> errors) {
        this(status, message, null, errors);
    }

    // Full constructor for all fields
    public CustomResponse(HttpStatus status, String message, Object data, List<String> errors) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }

    // Getters
    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    public List<String> getErrors() {
        return errors;
    }
}