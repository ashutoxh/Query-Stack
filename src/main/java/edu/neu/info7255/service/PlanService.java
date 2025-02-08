package edu.neu.info7255.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.neu.info7255.exception.SchemaValidationException;
import edu.neu.info7255.validation.JsonSchemaValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * The type Plan service.
 */
@Service
public class PlanService {

    private final ReactiveHashOperations<String, String, String> hashOperations;
    private final JsonSchemaValidator jsonSchemaValidator;
    private final ObjectMapper objectMapper;

    /**
     * Instantiates a new Plan service.
     *
     * @param reactiveRedisTemplate the reactive redis template
     * @param jsonSchemaValidator   the json schema validator
     * @param objectMapper          the object mapper
     */
    @Autowired
    public PlanService(ReactiveRedisTemplate<String, String> reactiveRedisTemplate,
                       JsonSchemaValidator jsonSchemaValidator,
                       ObjectMapper objectMapper) {
        this.hashOperations = reactiveRedisTemplate.opsForHash();
        this.jsonSchemaValidator = jsonSchemaValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * Save a plan after validating the JSON data.
     *
     * @param planId   The unique ID of the plan.
     * @param planData The JSON data of the plan.
     * @return A Mono containing the ID if saved successfully, or an error if validation fails.
     */
    public Mono<String> savePlan(String planId, JsonNode planData) {
        try {
            // Validate the JSON data
            jsonSchemaValidator.validate(planData);
        }
        catch (SchemaValidationException e) {
            return Mono.error(new SchemaValidationException(e.getMessage(), e.getValidationErrors()));
        }

        // Generate a stable ETag based on the JSON data
        String etag = generateETag(planData);

        try {
            // Serialize JSON data to a string for Redis storage
            String serializedData = objectMapper.writeValueAsString(planData);

            // Store both data and ETag in Redis
            return hashOperations.putAll(planId, Map.of("data", serializedData, "etag", etag))
                    .map(success -> success ? etag : null);

        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to save plan", e));
        }
    }


    /**
     * Retrieve a plan by ID, using ETag for conditional read support.
     *
     * @param planId     The unique ID of the plan.
     * @param clientETag The client's ETag for conditional read.
     * @return A Mono containing the response with the plan data and ETag.
     */
    public Mono<ResponseWithETag> getPlanById(String planId, String clientETag) {
        return hashOperations.entries(planId)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(entries -> {
                    String data = entries.get("data");
                    String storedETag = entries.get("etag");

                    if (data == null || storedETag == null) {
                        return Mono.empty();  // Plan not found
                    }

                    // Check for ETag match
                    if (storedETag.equals(clientETag)) {
                        return Mono.just(new ResponseWithETag(null, storedETag, true));
                    }

                    // Deserialize JSON data and return it with the ETag
                    JsonNode planData = null;
                    try {
                        planData = objectMapper.readTree(data);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    return Mono.just(new ResponseWithETag(planData, storedETag, false));
                });
    }

    /**
     * Delete a plan by ID.
     *
     * @param planId The unique ID of the plan.
     * @return A Mono indicating if the deletion was successful.
     */
    public Mono<Boolean> deletePlanById(String planId) {
        return hashOperations.delete(planId);  // Directly return the boolean
    }


    /**
     * Generate a stable ETag from the JSON content.
     * @param planData The JSON data.
     * @return A unique ETag string.
     */
    /**
     * Generates a stable SHA-256-based ETag for the given JSON data.
     */
    private String generateETag(JsonNode planData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(planData.toString().getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate ETag", e);
        }
    }

    /**
     * Wrapper for a response with ETag support.
     */
    public static class ResponseWithETag {
        private final JsonNode data;
        private final String eTag;
        private final boolean notModified;

        /**
         * Instantiates a new Response with e tag.
         *
         * @param data        the data
         * @param eTag        the e tag
         * @param notModified the not modified
         */
        public ResponseWithETag(JsonNode data, String eTag, boolean notModified) {
            this.data = data;
            this.eTag = eTag;
            this.notModified = notModified;
        }

        /**
         * Gets data.
         *
         * @return the data
         */
        public JsonNode getData() {
            return data;
        }

        /**
         * Gets e tag.
         *
         * @return the e tag
         */
        public String getETag() {
            return eTag;
        }

        /**
         * Is not modified boolean.
         *
         * @return the boolean
         */
        public boolean isNotModified() {
            return notModified;
        }
    }
}
