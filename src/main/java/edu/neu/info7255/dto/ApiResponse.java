package edu.neu.info7255.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * The type Api response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    private HttpStatus status;   // HttpStatus code (e.g., 201 CREATED, 400 BAD_REQUEST)
    private String message;      // Additional info or error messages
    private String etag;         // Optional, used when creating or modifying resources
    private List<String> errors; // List of validation errors

    /**
     * Instantiates a new Api response.
     *
     * @param data the data
     */
    public ApiResponse(String data){
        this.message = data;
    }

    /**
     * Instantiates a new Api response.
     *
     * @param status  the status
     * @param message the message
     */
    public ApiResponse(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * Instantiates a new Api response.
     *
     * @param status  the status
     * @param message the message
     * @param etag    the etag
     */
    public ApiResponse(HttpStatus status, String message, String etag) {
        this.status = status;
        this.message = message;
        this.etag = etag;
    }

    /**
     * Instantiates a new Api response.
     *
     * @param status  the status
     * @param message the message
     * @param errors  the errors
     */
    public ApiResponse(HttpStatus status, String message, List<String> errors) {
        this.status = status;
        this.message = message;
        this.errors = errors;
    }

    /**
     * Gets status.
     *
     * @return the status
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Gets message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets etag.
     *
     * @return the etag
     */
    public String getEtag() {
        return etag;
    }

    /**
     * Gets errors.
     *
     * @return the errors
     */
    public List<String> getErrors() {
        return errors;
    }
}
