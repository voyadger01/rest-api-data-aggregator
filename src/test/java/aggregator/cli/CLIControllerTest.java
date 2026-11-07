package aggregator.cli;

import aggregator.DataAggregator;
import aggregator.model.ApiRecord;
import aggregator.storage.DataStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CLIControllerTest {
    @Test
    void collectsAndSavesRecords() throws Exception {
        DataAggregator aggregator = mock(DataAggregator.class);
        DataStorage storage = mock(DataStorage.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ApiRecord record = new ApiRecord("weather", Instant.now(), Map.of(), mapper.createObjectNode());
        when(aggregator.collect(anyList(), anyMap())).thenReturn(List.of(record));
        CLIController controller = new CLIController(aggregator, Map.of("json", storage), mapper);
        CLIParser.Options options = new CLIParser.Options("collect", List.of("weather"), "json", false,
                Path.of("build/result.json"), null, Map.of());
        controller.execute(options);
        verify(storage).save(List.of(record), options.file(), false);
    }

    @Test
    void readsAndFiltersRecords() throws Exception {
        DataAggregator aggregator = mock(DataAggregator.class);
        DataStorage storage = mock(DataStorage.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(storage.load(any())).thenReturn(List.of(
                new ApiRecord("weather", Instant.now(), Map.of(), mapper.createObjectNode()),
                new ApiRecord("users", Instant.now(), Map.of(), mapper.createObjectNode())));
        CLIController controller = new CLIController(aggregator, Map.of("json", storage), mapper);
        controller.execute(new CLIParser.Options("read", List.of(), "json", false, Path.of("build/result.json"),
                "weather", Map.of()));
        verify(storage).load(Path.of("build/result.json"));
    }

    @Test
    void printsHelpWithoutAccessingStorage() throws Exception {
        CLIController controller = new CLIController(mock(DataAggregator.class), Map.of(), new ObjectMapper());
        assertDoesNotThrow(() -> controller.execute(new CLIParser.Options("help", List.of(), null, false,
                null, null, Map.of())));
    }
}
