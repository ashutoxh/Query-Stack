package edu.neu.info7255.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.neu.info7255.dto.CustomResponse;
import edu.neu.info7255.exception.ETagMismatchException;
import edu.neu.info7255.exception.SchemaValidationException;
import edu.neu.info7255.validation.JsonSchemaValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Iterator;
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
    public Mono<ResponseWithETag> savePlan(String planId, JsonNode planData) {
        try {
            // Validate the JSON data
            jsonSchemaValidator.validate(planData);
        } catch (SchemaValidationException e) {
            return Mono.error(new SchemaValidationException(e.getMessage(), e.getValidationErrors()));
        }

        // Generate a stable ETag based on the JSON data
        String etag = generateETag(planData);

        String serializedData;

        try {
            // Serialize JSON data to a string for Redis storage
            serializedData = objectMapper.writeValueAsString(planData);
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to serialize plan data", e));
        }

        return hashOperations.entries(planId)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(existingFields -> {
                    if (existingFields.isEmpty()) {
                        // No existing data, save the new plan
                        return saveNewData(planId, serializedData, etag)
                                .map(savedEtag -> new ResponseWithETag(planData, savedEtag, false));
                    }

                    String existingData = existingFields.get("data");
                    String existingEtag = existingFields.get("etag");
                    if (existingData != null && existingData.equals(serializedData)) {
                        // Data matches, return the existing ETag and data
                        return Mono.just(new ResponseWithETag(planData, existingEtag, true));
                    }

                    // Data is different, update and return new ETag and data
                    return saveNewData(planId, serializedData, etag)
                            .map(savedEtag -> new ResponseWithETag(planData, savedEtag, false));
                });
    }

    private Mono<String> saveNewData(String planId, String serializedData, String etag) {
        return hashOperations.putAll(planId, Map.of("data", serializedData, "etag", etag))
                .thenReturn(etag);
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

    public Mono<ResponseWithETag> patchPlan(String planId, JsonNode patchData, String clientETag) {
        return hashOperations.entries(planId)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(existingFields -> {
                    String existingData = existingFields.get("data");
                    String existingEtag = existingFields.get("etag");

                    if (existingData == null || existingEtag == null) {
                        // Plan not found
                        return Mono.error(new RuntimeException("Plan not found"));
                    }

                    // Check if the provided ETag matches the current ETag
                    if (!existingEtag.equals(clientETag)) {
                        return Mono.error(new ETagMismatchException("ETag mismatch"));
                    }

                    // Deserialize the existing plan data
                    JsonNode existingPlanData;
                    try {
                        existingPlanData = objectMapper.readTree(existingData);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to deserialize existing plan data", e));
                    }

                    // Validate the partial JSON data
                    try {
                        jsonSchemaValidator.validatePartial(patchData);
                    } catch (SchemaValidationException e) {
                        return Mono.error(e);
                    }

                    // Merge the patch data with the existing data
                    JsonNode updatedPlanData = mergePatch(existingPlanData, patchData);

                    if(updatedPlanData.equals(existingPlanData))
                        return Mono.just(new ResponseWithETag(existingPlanData, clientETag, true));
                    // Generate a new ETag for the updated plan
                    String newEtag = generateETag(updatedPlanData);

                    // Serialize the updated plan data
                    String serializedUpdatedData;
                    try {
                        serializedUpdatedData = objectMapper.writeValueAsString(updatedPlanData);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to serialize updated plan data", e));
                    }

                    // Save the updated plan and return the response
                    return saveNewData(planId, serializedUpdatedData, newEtag)
                            .map(savedEtag -> new ResponseWithETag(updatedPlanData, savedEtag, false));
                });
    }

    /**
     * Merges the patch data with the existing data.
     */
    private JsonNode mergePatch(JsonNode existingData, JsonNode patchData) {
        // Create a deep copy of the existing data
        ObjectNode mergedData = existingData.deepCopy();

        // Iterate over the fields in the patch data and merge them into the copied data
        Iterator<Map.Entry<String, JsonNode>> fields = patchData.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            mergedData.set(field.getKey(), field.getValue());
        }

        return mergedData;
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
