package aggregator.source;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpenMeteoSource implements ApiSource {
    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String baseUrl() {
        return "https://api.open-meteo.com/v1/forecast";
    }

    @Override
    public Map<String, String> defaultParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("latitude", "41.3275");
        parameters.put("longitude", "19.8187");
        parameters.put("current", "temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m");
        parameters.put("timezone", "auto");
        return parameters;
    }

    @Override
    public JsonNode extractData(JsonNode response) {
        return response;
    }
}
