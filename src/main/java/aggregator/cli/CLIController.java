package aggregator.cli;

import aggregator.DataAggregator;
import aggregator.model.ApiRecord;
import aggregator.storage.DataStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CLIController {
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
        System.out.println("Получено и сохранено записей: " + records.size());
        System.out.println("Файл: " + options.file().toAbsolutePath());
        System.out.println("Источники: " + String.join(", ", options.apis()));
        System.out.println("Формат: " + options.format());
        System.out.println("Режим: " + (options.append() ? "append" : "create"));
    }

    private void read(CLIParser.Options options, DataStorage storage) throws IOException {
        List<ApiRecord> records = storage.load(options.file());
        if (options.source() != null && !options.source().isBlank()) {
            records = records.stream()
                    .filter(record -> record.source().equalsIgnoreCase(options.source()))
                    .toList();
        }
        System.out.println(mapper.writer(SerializationFeature.INDENT_OUTPUT).writeValueAsString(records));
    }

    private void printHelp() {
        System.out.println("REST API Data Aggregator");
        System.out.println();
        System.out.println("Сбор данных:");
        System.out.println("  collect --apis weather,food,github --format json|csv --mode create|append --file data/results.json");
        System.out.println("  collect --apis weather --format json --file data/weather.json --param weather.latitude=48.8566 --param weather.longitude=2.3522");
        System.out.println();
        System.out.println("Чтение файла:");
        System.out.println("  read --file data/results.json [--format json|csv] [--source weather]");
        System.out.println();
        System.out.println("Доступные API: " + String.join(", ", aggregator.sourceNames()));
        System.out.println("Параметр API задаётся в формате --param api.key=value и может повторяться.");
    }
}
