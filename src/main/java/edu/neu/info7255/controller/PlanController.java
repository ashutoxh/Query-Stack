package edu.neu.info7255.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.neu.info7255.dto.ApiResponse;
import edu.neu.info7255.exception.SchemaValidationException;
import edu.neu.info7255.service.PlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * The type Plan controller.
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    @Autowired
    private PlanService planService;

    /**
     * Create plan mono.
     *
     * @param planData the plan data
     * @return the mono
     */
    @PostMapping
    public Mono<ResponseEntity<ApiResponse>> createPlan(@RequestBody JsonNode planData) {
        return planService.savePlan(planData.get("objectId").asText(), planData)
                .map(etag -> ResponseEntity.status(HttpStatus.CREATED)
                        .header("ETag", etag)
                        .body(new ApiResponse(HttpStatus.CREATED, "Plan created successfully", etag)))
                .onErrorResume(SchemaValidationException.class, e -> Mono.just(ResponseEntity.badRequest()
                        .body(new ApiResponse(HttpStatus.BAD_REQUEST, e.getMessage(), e.getValidationErrors()))))
                .onErrorResume(e ->
                                Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(new ApiResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save plan"))));
    }

    /**
     * Gets plan.
     *
     * @param id         the id
     * @param clientETag the client e tag
     * @return the plan
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<JsonNode>> getPlan(
            @PathVariable String id,
            @RequestHeader(value = "If-None-Match", required = false) String clientETag) {

        return planService.getPlanById(id, clientETag)
                .map(response -> {
                    if (response.isNotModified()) {
                        // Return a 304 Not Modified response with the correct type
                        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                                .eTag(response.getETag())
                                .<JsonNode>build();
                    }

                    // Return 200 OK with the JSON body
                    return ResponseEntity.ok()
                            .eTag(response.getETag())
                            .body(response.getData());
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Delete plan mono.
     *
     * @param id the id
     * @return the mono
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse>> deletePlan(@PathVariable String id) {
        return planService.deletePlanById(id)
                .map(deleted -> deleted
                        ? ResponseEntity.ok(new ApiResponse(HttpStatus.OK, "Plan deleted successfully"))
                        : ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(HttpStatus.NOT_FOUND, "Plan not found.")));
    }
}
