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
        IO.println("КОМАНДЫ");
        IO.println("  collect   Запрашивает данные из выбранных API и сохраняет их в файл.");
        IO.println("  read      Читает ранее сохранённый JSON/CSV-файл и выводит записи в консоль.");
        IO.println("  help      Показывает справку.");
        IO.println("  --help, -h Также показывают справку.");
        IO.println();
        IO.println("ПАРАМЕТРЫ collect");
        IO.println("  --apis <список>          Обязательный список источников через запятую: weather,users,comments.");
        IO.println("  --format <json|csv>      Обязательный формат выходного файла.");
        IO.println("  --file <путь>            Обязательный путь к файлу, например src/main/resources/results.json.");
        IO.println("  --mode <режим>           Режим записи: create/overwrite — создать или перезаписать файл; append — добавить записи. По умолчанию create.");
        IO.println("  --param <api.key=value>  Переопределяет query-параметр источника. Можно указывать несколько раз.");
        IO.println();
        IO.println("ПАРАМЕТРЫ read");
        IO.println("  --file <путь>            Обязательный путь к сохранённому файлу.");
        IO.println("  --format <json|csv>      Формат файла. Необязателен: определяется по расширению .json или .csv.");
        IO.println("  --source <имя_api>       Необязательный фильтр записей: weather, users или comments.");
        IO.println();
        IO.println("ИСТОЧНИКИ И ИХ QUERY-ПАРАМЕТРЫ");
        IO.println("  weather   Open-Meteo: latitude, longitude — координаты; current — текущие показатели; timezone — часовой пояс.");
        IO.println("  users     JSONPlaceholder: username — фильтр по имени пользователя. Ответ содержит профиль, адрес и компанию.");
        IO.println("  comments  DummyJSON: sortBy — поле сортировки; order — asc или desc. Ответ содержит комментарии и вложенные данные автора.");
        IO.println();
        IO.println("Параметры по умолчанию используются, если соответствующее значение не передано через --param.");
    }
}
