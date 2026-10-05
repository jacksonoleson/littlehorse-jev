package io.littlehorse.shared.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * Sorts map keys in every JSON body, so a model request is byte-identical across restarts. Without it,
 * {@code Map.of} key order changes per JVM and Jev's answers can change with it (EXPERIMENTS.md #3).
 */
@Singleton
public class SortedJsonKeys implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }
}
