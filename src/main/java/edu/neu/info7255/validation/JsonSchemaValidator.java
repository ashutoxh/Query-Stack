package edu.neu.info7255.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.neu.info7255.config.QueryStackProperties;
import edu.neu.info7255.exception.SchemaValidationException;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * The type Json schema validator.
 */
@Component
public class JsonSchemaValidator {

    private final Schema schema;
    private final ObjectMapper objectMapper;

    /**
     * Instantiates a new Json schema validator.
     *
     * @param properties     the properties
     * @param objectMapper   the object mapper
     * @param resourceLoader the resource loader
     */
    public JsonSchemaValidator(QueryStackProperties properties, ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;

        String schemaPath = properties.getSchemaPath();
        if (schemaPath == null || schemaPath.isBlank()) {
            throw new IllegalArgumentException("Schema path is not configured or is empty.");
        }

        try (InputStream schemaStream = resourceLoader.getResource("classpath:" + schemaPath).getInputStream()) {
            // Load and parse the schema using Everit
            String schemaString = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject rawSchema = new JSONObject(schemaString);
            this.schema = SchemaLoader.load(rawSchema);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JSON schema from path: " + schemaPath, e);
        }
    }

    /**
     * Validate.
     *
     * @param jsonData the json data
     */
    public void validate(JsonNode jsonData) {
        try {
            // Convert JsonNode to JSONObject for validation
            JSONObject jsonObject = new JSONObject(objectMapper.writeValueAsString(jsonData));
            schema.validate(jsonObject);  // Throws exception if validation fails
        } catch (org.everit.json.schema.ValidationException e) {
            throw new SchemaValidationException("Schema validation failed", e.getAllMessages());
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error during schema validation", e);
        }
    }

    /**
     * Validate partial JSON data against the schema.
     * Only the provided fields are validated, and missing fields are ignored.
     *
     * @param partialJsonData the partial json data
     */
    public void validatePartial(JsonNode partialJsonData) {
        try {
            // Convert the partial JSON to a JSONObject
            JSONObject partialJsonObject = new JSONObject(objectMapper.writeValueAsString(partialJsonData));

            // Create a modified schema for partial validation
            JSONObject modifiedSchema = createModifiedSchemaForPartialValidation();

            // Load the modified schema
            Schema partialSchema = SchemaLoader.load(modifiedSchema);

            // Validate the partial JSON against the modified schema
            partialSchema.validate(partialJsonObject);
        } catch (org.everit.json.schema.ValidationException e) {
            throw new SchemaValidationException("Schema validation failed", e.getAllMessages());
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error during schema validation", e);
        }
    }

    /**
     * Create a modified schema for partial validation.
     * Removes the "required" constraints from the schema.
     *
     * @return the modified schema as a JSONObject
     */
    private JSONObject createModifiedSchemaForPartialValidation() {
        // Clone the original schema
        JSONObject modifiedSchema = new JSONObject(schema.toString());

        // Remove the "required" field from the schema
        modifiedSchema.remove("required");

        // If the schema contains nested objects, recursively remove "required" fields
        removeRequiredFieldsRecursively(modifiedSchema);

        return modifiedSchema;
    }

    /**
     * Recursively remove "required" fields from the schema.
     *
     * @param schema the schema to modify
     */
    private void removeRequiredFieldsRecursively(JSONObject schema) {
        if (schema.has("properties")) {
            JSONObject properties = schema.getJSONObject("properties");
            for (String key : properties.keySet()) {
                Object value = properties.get(key);
                if (value instanceof JSONObject) {
                    JSONObject propertySchema = (JSONObject) value;
                    propertySchema.remove("required");
                    removeRequiredFieldsRecursively(propertySchema);
                }
            }
        }
    }
}