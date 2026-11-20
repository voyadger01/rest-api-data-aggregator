package aggregator.config;

import aggregator.DataAggregator;
import aggregator.http.HttpClientService;
import aggregator.source.ApiSource;
import aggregator.storage.CsvStorage;
import aggregator.storage.DataStorage;
import aggregator.storage.JsonStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Configuration
public class AggregatorConfiguration {
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    Map<String, ApiSource> apiSources(List<ApiSource> sources) {
        Map<String, ApiSource> registry = new LinkedHashMap<>();
        for (ApiSource source : sources) {
            String name = source.name().toLowerCase(Locale.ROOT);
            if (name.isBlank()) {
                throw new IllegalStateException("Имя API не может быть пустым");
            }
            if (registry.putIfAbsent(name, source) != null) {
                throw new IllegalStateException("Повторяющееся имя API: " + name);
            }
        }
        return Map.copyOf(registry);
    }

    @Bean
    HttpClientService httpClientService() {
        return new HttpClientService();
    }

    @Bean
    DataAggregator dataAggregator(
            @Qualifier("apiSources") Map<String, ApiSource> apiSources,
            HttpClientService client,
            ObjectMapper mapper
    ) {
        return new DataAggregator(apiSources, client, mapper);
    }

    @Bean
    Map<String, DataStorage> dataStorages(ObjectMapper mapper) {
        return Map.of("json", new JsonStorage(mapper), "csv", new CsvStorage(mapper));
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledThreadPoolExecutor pollingExecutor(PollingProperties properties) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(Math.max(1, properties.getN()));
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }
}
