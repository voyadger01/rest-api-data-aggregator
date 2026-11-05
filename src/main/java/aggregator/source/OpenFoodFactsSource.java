package aggregator.source;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpenFoodFactsSource implements ApiSource {
    @Override
    public String name() {
        return "food";
    }

    @Override
    public String baseUrl() {
        return "https://world.openfoodfacts.net/api/v2/search";
    }

    @Override
    public Map<String, String> defaultParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("categories_tags_en", "chocolates");
        parameters.put("page", "1");
        parameters.put("page_size", "5");
        parameters.put("fields", "code,product_name,brands,categories_tags_en,nutriments,ingredients_text,image_url");
        return parameters;
    }

    @Override
    public JsonNode extractData(JsonNode response) {
        return response;
    }
}
