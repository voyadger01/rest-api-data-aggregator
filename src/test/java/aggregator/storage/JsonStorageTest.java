package aggregator.storage;

import aggregator.model.ApiRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void savesLoadsAndAppendsRecords() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JsonStorage storage = new JsonStorage(mapper);
        Path file = tempDir.resolve("records.json");
        ApiRecord first = new ApiRecord("weather", Instant.parse("2026-01-01T10:00:00Z"), Map.of("city", "Tirana"), mapper.readTree("{\"current\":{\"temperature\":20}}"));
        ApiRecord second = new ApiRecord("github", Instant.parse("2026-01-01T10:01:00Z"), Map.of("page", "2"), mapper.readTree("{\"commits\":[{\"message\":\"Commit\"}]}"));

        storage.save(List.of(first), file, false);
        storage.save(List.of(second), file, true);

        List<ApiRecord> loaded = storage.load(file);
        assertEquals(2, loaded.size());
        assertEquals("weather", loaded.get(0).source());
        assertEquals("github", loaded.get(1).source());
        assertEquals("Commit", loaded.get(1).node().path("commits").get(0).path("message").asText());
    }
}
