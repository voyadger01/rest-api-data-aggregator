package aggregator.storage;

import aggregator.model.ApiRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvStorage implements DataStorage {
    private static final String HEADER = "source,fetchedAt,parameters,data";
    private final ObjectMapper mapper;

    public CsvStorage(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(List<ApiRecord> records, Path path, boolean append) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> lines = new ArrayList<>();
        if (!append || !Files.exists(path) || Files.size(path) == 0) {
            lines.add(HEADER);
        }
        for (ApiRecord record : records) {
            String parameters = mapper.writeValueAsString(record.parameters());
            String data = mapper.writeValueAsString(record.node());
            lines.add(csv(record.source()) + "," + csv(record.fetchedAt().toString()) + "," + csv(parameters) + "," + csv(data));
        }
        if (append) {
            Files.write(path, lines, StandardCharsets.UTF_8, Files.exists(path)
                    ? java.nio.file.StandardOpenOption.APPEND
                    : java.nio.file.StandardOpenOption.CREATE);
        } else {
            Files.write(path, lines, StandardCharsets.UTF_8);
        }
    }

    @Override
    public List<ApiRecord> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Файл не найден: " + path);
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return List.of();
        }
        if (!HEADER.equals(lines.get(0))) {
            throw new IOException("Некорректный заголовок CSV-файла: " + path);
        }

        List<ApiRecord> records = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> columns = parseCsvLine(lines.get(i));
            if (columns.size() != 4) {
                throw new IOException("Некорректная строка CSV: " + (i + 1));
            }
            Map<String, String> parameters = mapper.readValue(columns.get(2), new TypeReference<LinkedHashMap<String, String>>() {});
            JsonNode node = mapper.readTree(columns.get(3));
            records.add(new ApiRecord(columns.get(0), Instant.parse(columns.get(1)), parameters, node));
        }
        return records;
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private List<String> parseCsvLine(String line) throws IOException {
        List<String> columns = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        value.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    value.append(current);
                }
            } else if (current == '"') {
                if (value.length() != 0) {
                    throw new IOException("Некорректное экранирование CSV");
                }
                quoted = true;
            } else if (current == ',') {
                columns.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw new IOException("Незакрытая кавычка в CSV");
        }
        columns.add(value.toString());
        return columns;
    }
}
