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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void savesLoadsAndAppendsRecordsWithNestedJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        CsvStorage storage = new CsvStorage(mapper);
        Path file = tempDir.resolve("records.csv");
        ApiRecord first = new ApiRecord(
                "users",
                Instant.parse("2026-01-01T10:00:00Z"),
                Map.of("username", "Bret"),
                mapper.readTree("""
                        [{
                          "id": 1,
                          "name": "A, \\"B\\"",
                          "username": "Bret",
                          "email": "a@example.test",
                          "address": {"city": "Gwenborough", "geo": {"lat": "-37.3159", "lng": "81.1496"}},
                          "phone": "1-770-736-8031",
                          "website": "example.test",
                          "company": {"name": "Example Corp"}
                        }]
                        """));
        ApiRecord second = new ApiRecord(
                "comments",
                Instant.parse("2026-01-01T10:01:00Z"),
                Map.of("sortBy", "likes", "order", "desc"),
                mapper.readTree("""
                        {
                          "comments": [{
                            "id": 15,
                            "body": "A comment, with comma",
                            "postId": 6,
                            "likes": 2,
                            "user": {"id": 17, "username": "evelyns", "fullName": "Evelyn Sanchez"}
                          }],
                          "total": 1,
                          "skip": 0,
                          "limit": 1
                        }
                        """));

        storage.save(List.of(first), file, false);
        storage.save(List.of(second), file, true);

        List<ApiRecord> loaded = storage.load(file);
        assertEquals(2, loaded.size());
        assertEquals("A, \"B\"", loaded.get(0).node().get(0).path("name").asText());
        assertEquals("comments", loaded.get(1).source());
        assertEquals("desc", loaded.get(1).parameters().get("order"));
        assertEquals("evelyns", loaded.get(1).node().path("comments").get(0).path("user").path("username").asText());
        String csv = java.nio.file.Files.readString(file);
        assertTrue(csv.startsWith("id,source,timestamp,data."));
        assertTrue(csv.contains("data.0.address.geo.lat"));
        assertTrue(csv.contains("data.comments.0.user.username"));
    }
}
