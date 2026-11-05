package aggregator;

import aggregator.cli.CLIController;
import aggregator.cli.CLIParser;
import aggregator.http.HttpClientService;
import aggregator.source.ApiSource;
import aggregator.source.OpenFoodFactsSource;
import aggregator.source.GitHubCommitsSource;
import aggregator.source.OpenMeteoSource;
import aggregator.storage.CsvStorage;
import aggregator.storage.DataStorage;
import aggregator.storage.JsonStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

public class Main {
    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Map<String, ApiSource> sources = Map.of(
                "weather", new OpenMeteoSource(),
                "food", new OpenFoodFactsSource(),
                "github", new GitHubCommitsSource()
        );

        HttpClientService httpClient = new HttpClientService();
        DataAggregator aggregator = new DataAggregator(sources, httpClient, mapper);
        Map<String, DataStorage> storages = Map.of(
                "json", new JsonStorage(mapper),
                "csv", new CsvStorage(mapper)
        );
        CLIController controller = new CLIController(aggregator, storages, mapper);

        try {
            controller.execute(new CLIParser(args).parse());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Опрос был прерван.");
            System.exit(1);
        } catch (Exception exception) {
            System.err.println("Ошибка: " + exception.getMessage());
            System.err.println("Запустите программу с параметром --help для просмотра команд.");
            System.exit(1);
        }
    }
}
