package edu.neu.info7255.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.*;

/**
 * The type Plan service.
 */
@Service
public class PlanService {

    private final ReactiveHashOperations<String, String, String> hashOperations;
    private final JsonSchemaValidator jsonSchemaValidator;
    private final ObjectMapper objectMapper;

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
     * @return A Mono containing the response with the plan data and ETag.
     */
    public Mono<ResponseWithETag> savePlan(String planId, JsonNode planData) {
        try {
            // Validate the JSON data
            jsonSchemaValidator.validate(planData);
        } catch (SchemaValidationException e) {
            return Mono.error(new SchemaValidationException(e.getMessage(), e.getValidationErrors()));
        }

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
                        return saveNewData(planId, serializedData)
                                .thenReturn(new ResponseWithETag(planData, generateETag(planData), false));
                    }

                    String existingData = existingFields.get("data");
                    if (existingData != null && existingData.equals(serializedData)) {
                        // Data matches, return the existing data and dynamically generated ETag
                        return Mono.just(new ResponseWithETag(planData, generateETag(planData), true));
                    }

                    // Data is different, update and return new data and dynamically generated ETag
                    return saveNewData(planId, serializedData)
                            .thenReturn(new ResponseWithETag(planData, generateETag(planData), false));
                });
    }

    private Mono<Void> saveNewData(String planId, String serializedData) {
        return hashOperations.put(planId, "data", serializedData).then();
    }

    /**
     * Retrieve a plan by ID, using ETag for conditional read support.
     *
     * @param planId     The unique ID of the plan.
     * @param clientETag The client's ETag for conditional read.
     * @return A Mono containing the response with the plan data and ETag.
     */
    public Mono<ResponseWithETag> getPlanById(String planId, String clientETag) {
        return hashOperations.get(planId, "data")
                .flatMap(data -> {
                    if (data == null) {
                        return Mono.empty();  // Plan not found
                    }

                    // Deserialize JSON data
                    JsonNode planData;
                    try {
                        planData = objectMapper.readTree(data);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to deserialize plan data", e));
                    }

                    // Generate the current ETag
                    String currentETag = generateETag(planData);

                    // Check for ETag match
                    if (currentETag.equals(clientETag)) {
                        return Mono.just(new ResponseWithETag(null, currentETag, true)); // Not Modified
                    }

                    // Return the plan data and current ETag
                    return Mono.just(new ResponseWithETag(planData, currentETag, false));
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
     * Partially update a plan by ID.
     *
     * @param planId     The unique ID of the plan.
     * @param patchData  The partial JSON data to update.
     * @param clientETag The client's ETag for conditional update.
     * @return A Mono containing the response with the updated plan data and ETag.
     */
    public Mono<ResponseWithETag> patchPlan(String planId, JsonNode patchData, String clientETag) {
        return hashOperations.get(planId, "data")
                .flatMap(existingData -> {
                    if (existingData == null) {
                        return Mono.error(new RuntimeException("Plan not found"));
                    }

                    // Deserialize the existing plan data
                    JsonNode existingPlanData;
                    try {
                        existingPlanData = objectMapper.readTree(existingData);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to deserialize existing plan data", e));
                    }

                    // Generate the current ETag
                    String currentETag = generateETag(existingPlanData);

                    // Check if the provided ETag matches the current ETag
                    if (!currentETag.equals(clientETag)) {
                        return Mono.error(new ETagMismatchException("ETag mismatch"));
                    }

                    // Validate the partial JSON data
                    try {
                        jsonSchemaValidator.validatePartial(patchData);
                    } catch (SchemaValidationException e) {
                        return Mono.error(e);
                    }

                    // Merge the patch data with the existing data
                    JsonNode updatedPlanData = mergePatch(existingPlanData, patchData);

                    // Check if the data was modified
                    if (updatedPlanData.equals(existingPlanData)) {
                        return Mono.just(new ResponseWithETag(existingPlanData, currentETag, true)); // Not Modified
                    }

                    // Generate a new ETag for the updated plan
                    String newETag = generateETag(updatedPlanData);

                    // Serialize the updated plan data
                    String serializedUpdatedData;
                    try {
                        serializedUpdatedData = objectMapper.writeValueAsString(updatedPlanData);
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Failed to serialize updated plan data", e));
                    }

                    // Save the updated plan and return the response
                    return saveNewData(planId, serializedUpdatedData)
                            .thenReturn(new ResponseWithETag(updatedPlanData, newETag, false));
                });
    }

    private JsonNode mergePatch(JsonNode existingData, JsonNode patchData) {
        // Create a deep copy of the existing data
        ObjectNode mergedData = existingData.deepCopy();

        // Merge the patch data into the existing data
        mergeJsonNodes(mergedData, patchData);

        return mergedData;
    }

    private void mergeJsonNodes(ObjectNode target, JsonNode patchNode) {
        // Iterate over the fields in the patch data
        patchNode.fields().forEachRemaining(field -> {
            String fieldName = field.getKey();
            JsonNode patchValue = field.getValue();

            // Handle objects recursively
            if (patchValue.isObject()) {
                if (target.has(fieldName)) {
                    // If the target has the field and it's also an object, merge recursively
                    if (target.get(fieldName).isObject()) {
                        mergeJsonNodes((ObjectNode) target.get(fieldName), patchValue);
                    } else {
                        // If the target field is not an object, replace it
                        target.set(fieldName, patchValue.deepCopy());
                    }
                } else {
                    // If the target doesn't have the field, add it
                    target.set(fieldName, patchValue.deepCopy());
                }
            }
            // Handle arrays by appending new items
            else if (patchValue.isArray()) {
                if (target.has(fieldName)) {
                    // If the target has the field and it's also an array, merge arrays
                    if (target.get(fieldName).isArray()) {
                        ArrayNode targetArray = (ArrayNode) target.get(fieldName);
                        ArrayNode patchArray = (ArrayNode) patchValue;
                        mergeArrays(targetArray, patchArray);
                    } else {
                        // If the target field is not an array, replace it
                        target.set(fieldName, patchValue.deepCopy());
                    }
                } else {
                    // If the target doesn't have the field, add it
                    target.set(fieldName, patchValue.deepCopy());
                }
            }
            // Handle non-object and non-array values (replace existing value)
            else {
                target.set(fieldName, patchValue);
            }
        });
    }

    private void mergeArrays(ArrayNode target, ArrayNode patchArray) {
        // Convert the target array to a Set for quick lookup
        Set<JsonNode> targetSet = new HashSet<>();
        target.forEach(targetSet::add);

        // Append items from the patch array that don't already exist in the target array
        patchArray.forEach(patchItem -> {
            if (!targetSet.contains(patchItem)) {
                target.add(patchItem.deepCopy());
            }
        });
    }

    /**
     * Generate a stable ETag from the JSON content.
     *
     * @param planData The JSON data.
     * @return A unique ETag string.
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