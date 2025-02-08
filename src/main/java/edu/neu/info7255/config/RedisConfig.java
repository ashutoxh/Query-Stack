package edu.neu.info7255.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * The type Redis config.
 */
@Configuration
public class RedisConfig {

    /**
     * Reactive redis template reactive redis template.
     *
     * @param factory      the factory
     * @param objectMapper the object mapper
     * @return the reactive redis template
     */
    @Bean
    public ReactiveRedisTemplate<String, JsonNode> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory, ObjectMapper objectMapper) {

        // Configure serializers for keys and values
        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        RedisSerializer<JsonNode> valueSerializer = new JacksonJsonRedisSerializer(objectMapper);

        RedisSerializationContext<String, JsonNode> serializationContext = RedisSerializationContext
                .<String, JsonNode>newSerializationContext(keySerializer)
                .value(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }

    /**
     * Custom Redis serializer for JsonNode using Jackson.
     */
    public static class JacksonJsonRedisSerializer implements RedisSerializer<JsonNode> {

        private final ObjectMapper objectMapper;

        /**
         * Instantiates a new Jackson json redis serializer.
         *
         * @param objectMapper the object mapper
         */
        public JacksonJsonRedisSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public byte[] serialize(JsonNode jsonNode) {
            try {
                return jsonNode == null ? new byte[0] : objectMapper.writeValueAsBytes(jsonNode);
            } catch (Exception e) {
                throw new RuntimeException("Could not serialize JsonNode", e);
            }
        }

        @Override
        public JsonNode deserialize(byte[] bytes) {
            try {
                return (bytes == null || bytes.length == 0) ? null : objectMapper.readTree(bytes);
            } catch (Exception e) {
                throw new RuntimeException("Could not deserialize to JsonNode", e);
            }
        }
    }
}
