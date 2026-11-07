package aggregator.polling;

import java.util.List;
import java.util.Map;

public record PollingStartRequest(Integer n, Long t, String format, String file, Boolean append,
                                  Map<String, List<Map<String, String>>> sources) {
}
