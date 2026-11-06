package aggregator.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSourceConfigurationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonPlaceholderReturnsFullUserProfileAndFiltersByUsername() throws Exception {
        JsonPlaceholderUsersSource source = new JsonPlaceholderUsersSource();
        JsonNode user = mapper.readTree("""
                {
                  "id": 1,
                  "name": "Leanne Graham",
                  "username": "Bret",
                  "email": "bret@example.com",
                  "address": {
                    "street": "Kulas Light",
                    "suite": "Apt. 556",
                    "city": "Gwenborough",
                    "zipcode": "92998-3874",
                    "geo": {"lat": "-37.3159", "lng": "81.1496"}
                  },
                  "phone": "1-770-736-8031",
                  "website": "hildegard.org",
                  "company": {
                    "name": "Romaguera-Crona",
                    "catchPhrase": "Multi-layered client-server neural-net",
                    "bs": "harness real-time e-markets"
                  }
                }
                """);

        assertEquals("users", source.name());
        assertEquals("https://jsonplaceholder.typicode.com/users", source.baseUrl());
        assertEquals("Bret", source.defaultParameters().get("username"));
        assertTrue(source.buildUrl(source.defaultParameters()).endsWith("?username=Bret"));
        assertTrue(user.size() > 5);
        assertTrue(user.path("address").isObject());
        assertTrue(user.path("company").isObject());
        assertTrue(user.path("address").path("geo").isObject());
    }

    @Test
    void dummyJsonReturnsFullCommentsWithNestedUserAndSortingParameters() throws Exception {
        DummyJsonCommentsSource source = new DummyJsonCommentsSource();
        JsonNode response = mapper.readTree("""
                {
                  "comments": [
                    {
                      "id": 15,
                      "body": "You've shown so much growth!",
                      "postId": 6,
                      "likes": 2,
                      "user": {
                        "id": 17,
                        "username": "evelyns",
                        "fullName": "Evelyn Sanchez"
                      }
                    }
                  ],
                  "total": 1,
                  "skip": 0,
                  "limit": 1
                }
                """);
        JsonNode comment = response.path("comments").get(0);

        assertEquals("comments", source.name());
        assertEquals("likes", source.defaultParameters().get("sortBy"));
        assertEquals("desc", source.defaultParameters().get("order"));
        assertTrue(source.buildUrl(source.defaultParameters()).contains("sortBy=likes"));
        assertTrue(source.buildUrl(source.defaultParameters()).contains("order=desc"));
        assertEquals(5, comment.size());
        assertTrue(comment.path("user").isObject());
        assertTrue(comment.size() + comment.path("user").size() > 5);
        assertFalse(source.defaultParameters().containsKey("select"));
        assertFalse(source.defaultParameters().containsKey("limit"));
    }

    @Test
    void weatherRequestsCurrentMeasurements() {
        OpenMeteoSource source = new OpenMeteoSource();

        assertEquals(4, source.defaultParameters().get("current").split(",").length);
        assertFalse(source.defaultParameters().containsKey("hourly"));
    }
}
