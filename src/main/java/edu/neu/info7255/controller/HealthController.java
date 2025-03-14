package edu.neu.info7255.controller;

import edu.neu.info7255.dto.CustomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The type Health controller.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "API Health", description = "Can be used to check health of the API")
public class HealthController {

    /**
     * Check health response entity.
     *
     * @return the response entity
     */
    @GetMapping("/health")
    @Operation(summary = "Check health", description = "Allows you to check the health of the API")
    @ApiResponse()
    public ResponseEntity<CustomResponse> checkHealth() {
        return ResponseEntity.status(HttpStatus.OK).body(new CustomResponse(HttpStatus.OK, "I'm alive"));
    }
}
