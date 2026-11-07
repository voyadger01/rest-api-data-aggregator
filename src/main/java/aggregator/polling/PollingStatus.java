package aggregator.polling;

import java.util.List;

public record PollingStatus(boolean running, int n, long t, List<String> sources, int tasks) {
}
