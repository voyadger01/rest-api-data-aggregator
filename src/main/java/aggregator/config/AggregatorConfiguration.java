package aggregator.config;

import aggregator.DataAggregator;
import aggregator.http.HttpClientService;
import aggregator.source.ApiSource;
import aggregator.source.DummyJsonCommentsSource;
import aggregator.source.JsonPlaceholderUsersSource;
import aggregator.source.OpenMeteoSource;
import aggregator.storage.CsvStorage;
import aggregator.storage.DataStorage;
import aggregator.storage.JsonStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Configuration
public class AggregatorConfiguration {
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean ApiSource weatherSource() { return new OpenMeteoSource(); }
    @Bean ApiSource usersSource() { return new JsonPlaceholderUsersSource(); }
    @Bean ApiSource commentsSource() { return new DummyJsonCommentsSource(); }

    @Bean
    Map<String, ApiSource> apiSources(@org.springframework.beans.factory.annotation.Qualifier("weatherSource") ApiSource weatherSource, @org.springframework.beans.factory.annotation.Qualifier("usersSource") ApiSource usersSource, @org.springframework.beans.factory.annotation.Qualifier("commentsSource") ApiSource commentsSource) {
        return Map.of("weather", weatherSource, "users", usersSource, "comments", commentsSource);
    }

    @Bean
    HttpClientService httpClientService() { return new HttpClientService(); }

    @Bean
    DataAggregator dataAggregator(@org.springframework.beans.factory.annotation.Qualifier("apiSources") Map<String, ApiSource> apiSources, HttpClientService client, ObjectMapper mapper) {
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
