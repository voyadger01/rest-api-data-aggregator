package aggregator.web;

import aggregator.polling.PollingScheduler;
import aggregator.polling.PollingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PollingControllerTest {
    @Test
    void exposesStatus() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        when(scheduler.status()).thenReturn(new PollingStatus(false, 3, 30, List.of(), 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PollingController(scheduler)).build();
        mvc.perform(get("/polling/status")).andExpect(status().isOk()).andExpect(jsonPath("$.running").value(false));
    }

    @Test
    void returnsConflictWhenAlreadyRunning() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        doThrow(new IllegalStateException("Опрос уже запущен")).when(scheduler).start(any());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PollingController(scheduler)).build();
        mvc.perform(post("/polling/start").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsBadRequestForInvalidSettings() throws Exception {
        PollingScheduler scheduler = mock(PollingScheduler.class);
        doThrow(new IllegalArgumentException("invalid")).when(scheduler).start(any());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PollingController(scheduler)).build();
        mvc.perform(post("/polling/start").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
