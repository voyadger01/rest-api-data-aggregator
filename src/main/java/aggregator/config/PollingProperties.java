package aggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "aggregator.polling")
public class PollingProperties {
    private int n = 3;
    private long t = 30;
    private String format = "json";
    private String file = "src/main/resources/results.json";
    private boolean append = true;
    private Map<String, List<Map<String, String>>> sources = new LinkedHashMap<>();

    public int getN() { return n; }
    public void setN(int n) { this.n = n; }
    public long getT() { return t; }
    public void setT(long t) { this.t = t; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
    public boolean isAppend() { return append; }
    public void setAppend(boolean append) { this.append = append; }
    public Map<String, List<Map<String, String>>> getSources() { return sources; }
    public void setSources(Map<String, List<Map<String, String>>> sources) { this.sources = sources; }
}
