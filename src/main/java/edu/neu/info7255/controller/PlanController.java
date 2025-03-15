package edu.neu.info7255.controller;

import com.fasterxml.jackson.databind.JsonNode;
import edu.neu.info7255.dto.CustomResponse;
import edu.neu.info7255.exception.ETagMismatchException;
import edu.neu.info7255.exception.SchemaValidationException;
import edu.neu.info7255.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * The type Plan controller.
 */
@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Plan Management", description = "Endpoints for managing plans")
public class PlanController {

    private final PlanService planService;

    /**
     * Instantiates a new Plan controller.
     *
     * @param planService the plan service
     */
    public PlanController(PlanService planService) {
        this.planService = planService;  // Constructor injection allows easy testing
    }

    /**
     * Create plan mono.
     *
     * @param planData the plan data
     * @return the mono
     */
    @PostMapping
    @Operation(summary = "Create a new plan", description = "Allows you to create a new plan.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Plan created successfully"),
            @ApiResponse(responseCode = "200", description = "Plan already exists"),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<ResponseEntity<CustomResponse>> createPlan(@RequestBody JsonNode planData) {
        return planService.savePlan(planData.get("objectId").asText(), planData)
                .flatMap(response -> {
                    if (response.isNotModified()) {
                        // Plan already exists, return existing data and ETag
                        return Mono.just(ResponseEntity.status(HttpStatus.OK)
                                .eTag(response.getETag())
                                .body(new CustomResponse(HttpStatus.OK, "Plan already exists", response.getData())));
                    } else {
                        // New plan created, return success response
                        return Mono.just(ResponseEntity.status(HttpStatus.CREATED)
                                .eTag(response.getETag())
                                .body(new CustomResponse(HttpStatus.CREATED, "Plan created successfully", response.getData())));
                    }
                })
                .onErrorResume(SchemaValidationException.class, e -> {
                    // Handle validation errors
                    List<String> validationErrors = e.getValidationErrors();
                    return Mono.just(ResponseEntity.badRequest()
                            .body(new CustomResponse(HttpStatus.BAD_REQUEST, e.getMessage(), validationErrors)));
                })
                .onErrorResume(e -> {
                    // Handle generic errors
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new CustomResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save plan")));
                });
    }

    /**
     * Gets plan.
     *
     * @param id         the id
     * @param clientETag the client e tag
     * @return the plan
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a plan by ID", description = "Retrieves a plan by its unique ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "304", description = "Plan not modified", content = @Content),
            @ApiResponse(responseCode = "404", description = "Plan not found", content = @Content)
    })
    public Mono<ResponseEntity<CustomResponse>> getPlan(
            @PathVariable String id,
            @RequestHeader(value = "If-None-Match", required = false) String clientETag) {

        return planService.getPlanById(id, clientETag)
                .map(response -> {
                    if (response.isNotModified()) {
                        // Return a 304 Not Modified response
                        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                                .eTag(response.getETag())
                                .body(new CustomResponse(HttpStatus.NOT_MODIFIED, "Plan not modified"));
                    }

                    // Return 200 OK with the JSON body
                    return ResponseEntity.ok()
                            .eTag(response.getETag())
                            .body(new CustomResponse(HttpStatus.OK, "Plan found", response.getData()));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new CustomResponse(HttpStatus.NOT_FOUND, "Plan not found")));
    }

    /**
     * Delete plan mono.
     *
     * @param id the id
     * @return the mono
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a plan by ID",
            description = "Deletes the plan associated with the provided plan ID. If the plan does not exist, a 404 status is returned."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Plan deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    public Mono<ResponseEntity<CustomResponse>> deletePlan(@PathVariable String id) {
        return planService.deletePlanById(id)
                .map(deleted -> {
                    if (deleted) {
                        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                                .body(new CustomResponse(HttpStatus.NO_CONTENT, "Plan deleted successfully"));
                    } else {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(new CustomResponse(HttpStatus.NOT_FOUND, "Plan not found"));
                    }
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new CustomResponse(HttpStatus.NOT_FOUND, "Plan not found")));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a plan", description = "Updates specific fields of a plan if the provided ETag is valid.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan updated successfully"),
            @ApiResponse(responseCode = "304", description = "Nothing to modify"),
            @ApiResponse(responseCode = "400", description = "Invalid input or ETag mismatch"),
            @ApiResponse(responseCode = "404", description = "Plan not found"),
            @ApiResponse(responseCode = "412", description = "ETag required or invalid"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<ResponseEntity<CustomResponse>> patchPlan(
            @PathVariable String id,
            @RequestBody JsonNode patchData,
            @RequestHeader(value = "If-None-Match", required = true) String clientETag) {

        return planService.patchPlan(id, patchData, clientETag)
                .flatMap(response -> {
                    if (response.isNotModified()) {
                        // Return 304 Not Modified
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                                .eTag(response.getETag())
                                .body(new CustomResponse(HttpStatus.NOT_MODIFIED, "Nothing to modify")));
                    } else {
                        // Return 200 OK with the updated plan
                        return Mono.just(ResponseEntity.ok()
                                .eTag(response.getETag())
                                .body(new CustomResponse(HttpStatus.OK, "Plan updated successfully", response.getData())));
                    }
                })
                .onErrorResume(SchemaValidationException.class, e -> {
                    // Handle validation errors
                    List<String> validationErrors = e.getValidationErrors();
                    return Mono.just(ResponseEntity.badRequest()
                            .body(new CustomResponse(HttpStatus.BAD_REQUEST, e.getMessage(), validationErrors)));
                })
                .onErrorResume(ETagMismatchException.class, e -> {
                    // Handle ETag mismatch
                    return Mono.just(ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                            .body(new CustomResponse(HttpStatus.PRECONDITION_FAILED, e.getMessage())));
                })
                .onErrorResume(e -> {
                    // Handle generic errors
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new CustomResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update plan")));
                });
    }
}