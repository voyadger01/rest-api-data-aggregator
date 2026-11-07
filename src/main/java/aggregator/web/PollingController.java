package aggregator.web;

import aggregator.model.ApiRecord;
import aggregator.polling.PollingScheduler;
import aggregator.polling.PollingStartRequest;
import aggregator.polling.PollingStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestController
public class PollingController {
    private final PollingScheduler scheduler;

    public PollingController(PollingScheduler scheduler) { this.scheduler = scheduler; }

    @PostMapping("/polling/start")
    public PollingStatus start(@RequestBody(required = false) PollingStartRequest request) {
        try {
            scheduler.start(request == null ? new PollingStartRequest(null, null, null, null, null, null) : request);
            return scheduler.status();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/polling/stop")
    public PollingStatus stop() {
        try {
            scheduler.stop();
            return scheduler.status();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @GetMapping("/polling/status")
    public PollingStatus status() { return scheduler.status(); }

    @GetMapping("/data")
    public java.util.List<ApiRecord> data(@RequestParam(required = false) String source) throws IOException {
        return scheduler.readData(source);
    }
}
