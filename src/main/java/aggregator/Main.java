package aggregator;

import aggregator.cli.CLIController;
import aggregator.cli.CLIParser;
import aggregator.http.HttpClientService;
import aggregator.source.ApiSource;
import aggregator.source.GitHubCommitsSource;
import aggregator.source.OpenFoodFactsSource;
import aggregator.source.OpenMeteoSource;
import aggregator.storage.CsvStorage;
import aggregator.storage.DataStorage;
import aggregator.storage.JsonStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

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
            logger.error("API polling was interrupted", exception);
            System.exit(1);
        } catch (Exception exception) {
            logger.error("Application failed: {}", exception.getMessage(), exception);
            logger.info("Run the application with --help to see available commands");
            System.exit(1);
        }
    }
}
