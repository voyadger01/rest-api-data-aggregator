package aggregator.storage;

import aggregator.model.ApiRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void savesLoadsAndAppendsRecordsWithNestedJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        CsvStorage storage = new CsvStorage(mapper);
        Path file = tempDir.resolve("records.csv");
        ApiRecord first = new ApiRecord("food", Instant.parse("2026-01-01T10:00:00Z"), Map.of("page_size", "5"), mapper.readTree("{\"products\":[{\"title\":\"A, \\\"B\\\"\"}]}"));
        ApiRecord second = new ApiRecord("github", Instant.parse("2026-01-01T10:01:00Z"), Map.of("page", "2"), mapper.readTree("{\"commits\":[{\"message\":\"Commit\"}]}"));

        storage.save(List.of(first), file, false);
        storage.save(List.of(second), file, true);

        List<ApiRecord> loaded = storage.load(file);
        assertEquals(2, loaded.size());
        assertEquals("A, \"B\"", loaded.get(0).node().path("products").get(0).path("title").asText());
        assertEquals("github", loaded.get(1).source());
    }
}
