package aggregator.web;

import aggregator.model.ApiRecord;
import aggregator.polling.PollingScheduler;
import aggregator.polling.PollingStartRequest;
import aggregator.polling.PollingStatus;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PollingControllerTest {
    @Test
    void startsPollingWithDefaultRequestWhenBodyIsMissing() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        when(scheduler.status()).thenReturn(new PollingStatus(true, 3, 30, List.of("weather"), 2));
        MockMvc mvc = mvc(scheduler);

        mvc.perform(post("/polling/start").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.tasks").value(2));

        verify(scheduler).start(new PollingStartRequest(null, null, null, null, null, null));
    }

    @Test
    void passesExplicitOverridesToScheduler() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        when(scheduler.status()).thenReturn(new PollingStatus(true, 2, 15, List.of("weather"), 2));

        mvc(scheduler).perform(post("/polling/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "n": 2,
                                  "t": 15,
                                  "format": "csv",
                                  "file": "build/output.csv",
                                  "append": false,
                                  "sources": {
                                    "weather": [
                                      {"latitude": "48.8", "longitude": "2.3"},
                                      {"latitude": "52.5", "longitude": "13.4"}
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").value(2))
                .andExpect(jsonPath("$.t").value(15));

        verify(scheduler).start(new PollingStartRequest(
                2,
                15L,
                "csv",
                "build/output.csv",
                false,
                Map.of("weather", List.of(
                        Map.of("latitude", "48.8", "longitude", "2.3"),
                        Map.of("latitude", "52.5", "longitude", "13.4")
                ))
        ));
    }

    @Test
    void mapsAlreadyRunningToConflict() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        doThrow(new IllegalStateException("Опрос уже запущен")).when(scheduler).start(any());

        mvc(scheduler).perform(post("/polling/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void mapsInvalidStartSettingsToBadRequest() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        doThrow(new IllegalArgumentException("invalid")).when(scheduler).start(any());

        mvc(scheduler).perform(post("/polling/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsMalformedJsonToBadRequest() throws Exception {
        mvc(mock(PollingScheduler.class)).perform(post("/polling/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stopsPollingAndReturnsUpdatedStatus() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        when(scheduler.status()).thenReturn(new PollingStatus(false, 3, 30, List.of(), 0));

        mvc(scheduler).perform(post("/polling/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));

        verify(scheduler).stop();
    }

    @Test
    void mapsStopWithoutActivePollingToConflict() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        doThrow(new IllegalStateException("Опрос не запущен")).when(scheduler).stop();

        mvc(scheduler).perform(post("/polling/stop"))
                .andExpect(status().isConflict());
    }

    @Test
    void exposesStatus() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        when(scheduler.status()).thenReturn(new PollingStatus(false, 3, 30, List.of(), 0));

        mvc(scheduler).perform(get("/polling/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.n").value(3))
                .andExpect(jsonPath("$.t").value(30));
    }

    @Test
    void returnsDataAndForwardsOptionalSourceFilter() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        ApiRecord record = new ApiRecord("weather", Instant.parse("2026-01-01T00:00:00Z"), Map.of(), TextNode.valueOf("sunny"));
        when(scheduler.readData("weather")).thenReturn(List.of(record));

        mvc(scheduler).perform(get("/data").param("source", "weather"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("weather"))
                .andExpect(jsonPath("$[0].node").value("sunny"));

        verify(scheduler).readData("weather");
    }

    @Test
    void mapsStorageErrorsToInternalServerError() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        when(scheduler.readData(null)).thenThrow(new IOException("file unavailable"));

        mvc(scheduler).perform(get("/data"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("file unavailable"));
    }

    @Test
    void exceptionHandlerUsesFallbackWhenIOExceptionHasNoMessage() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        var response = handler.handleIOException(new IOException());

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Ошибка чтения файла", response.getBody().get("error"));
    }

    private MockMvc mvc(PollingScheduler scheduler) {
        return MockMvcBuilders.standaloneSetup(new PollingController(scheduler))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
