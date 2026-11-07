package aggregator.config;

import aggregator.source.ApiSource;
import aggregator.storage.DataStorage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AggregatorConfigurationTest {
    @Test
    void createsSourcesAndStorages() {
        AggregatorConfiguration configuration = new AggregatorConfiguration();
        ApiSource weather = configuration.weatherSource();
        ApiSource users = configuration.usersSource();
        ApiSource comments = configuration.commentsSource();
        Map<String, ApiSource> sources = configuration.apiSources(weather, users, comments);
        Map<String, DataStorage> storages = configuration.dataStorages(configuration.objectMapper());
        assertEquals(3, sources.size());
        assertTrue(sources.containsKey("weather"));
        assertTrue(storages.containsKey("json"));
        assertTrue(storages.containsKey("csv"));
    }
}
