package aggregator.cli;

import aggregator.DataAggregator;
import aggregator.model.ApiRecord;
import aggregator.storage.DataStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CLIController {
    private static final Logger logger = LoggerFactory.getLogger(CLIController.class);
    private final DataAggregator aggregator;
    private final Map<String, DataStorage> storages;
    private final ObjectMapper mapper;

    public CLIController(DataAggregator aggregator, Map<String, DataStorage> storages, ObjectMapper mapper) {
        this.aggregator = aggregator;
        this.storages = Map.copyOf(storages);
        this.mapper = mapper;
    }

    public void execute(CLIParser.Options options) throws IOException, InterruptedException {
        if (options.command().equals("help")) {
            printHelp();
            return;
        }

        DataStorage storage = storages.get(options.format());
        if (storage == null) {
            throw new IllegalArgumentException("Неподдерживаемый формат: " + options.format());
        }

        if (options.command().equals("collect")) {
            collect(options, storage);
        } else {
            read(options, storage);
        }
    }

    private void collect(CLIParser.Options options, DataStorage storage) throws IOException, InterruptedException {
        List<ApiRecord> records = aggregator.collect(options.apis(), options.parameters());
        storage.save(records, options.file(), options.append());

        logger.info(
                "Saved {} records to {} using {} format",
                records.size(),
                options.file().toAbsolutePath(),
                options.format()
        );

        IO.println("Получено и сохранено записей: " + records.size());
        IO.println("Файл: " + options.file().toAbsolutePath());
        IO.println("Источники: " + String.join(", ", options.apis()));
        IO.println("Формат: " + options.format());
        IO.println("Режим: " + (options.append() ? "append" : "create"));
    }

    private void read(CLIParser.Options options, DataStorage storage) throws IOException {
        List<ApiRecord> records = storage.load(options.file());
        if (options.source() != null && !options.source().isBlank()) {
            records = records.stream()
                    .filter(record -> record.source().equalsIgnoreCase(options.source()))
                    .toList();
        }

        logger.info("Read {} records from {}", records.size(), options.file().toAbsolutePath());
        IO.println(mapper.writer(SerializationFeature.INDENT_OUTPUT).writeValueAsString(records));
    }

    private void printHelp() {
        IO.println("REST API Data Aggregator");
        IO.println();
        IO.println("Сбор данных:");
        IO.println("  collect --apis weather,food,github --format json|csv --mode create|append --file data/results.json");
        IO.println("  collect --apis weather --format json --file data/weather.json --param weather.latitude=48.8566 --param weather.longitude=2.3522");
        IO.println();
        IO.println("Чтение файла:");
        IO.println("  read --file data/results.json [--format json|csv] [--source weather]");
        IO.println();
        IO.println("Доступные API: " + String.join(", ", aggregator.sourceNames()));
        IO.println("Параметр API задаётся в формате --param api.key=value и может повторяться.");
    }
}
