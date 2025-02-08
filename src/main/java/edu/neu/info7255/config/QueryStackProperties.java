package edu.neu.info7255.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The type Query stack properties.
 */
@Configuration
@ConfigurationProperties(prefix = "query-stack")
public class QueryStackProperties {

    private String schemaPath;

    /**
     * Gets schema path.
     *
     * @return the schema path
     */
    public String getSchemaPath() {
        return schemaPath;
    }

    /**
     * Sets schema path.
     *
     * @param schemaPath the schema path
     */
    public void setSchemaPath(String schemaPath) {
        this.schemaPath = schemaPath;
    }
}

