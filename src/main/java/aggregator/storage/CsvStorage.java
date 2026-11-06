package aggregator.storage;

import aggregator.model.ApiRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CsvStorage implements DataStorage {
    private static final List<String> FIXED_COLUMNS = List.of("id", "source", "timestamp");
    private final ObjectMapper mapper;

    public CsvStorage(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(List<ApiRecord> records, Path path, boolean append) throws IOException {
        List<ApiRecord> allRecords = new ArrayList<>();
        if (append && Files.exists(path) && Files.size(path) > 0) {
            allRecords.addAll(load(path));
        }
        allRecords.addAll(records);

        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<Map<String, CsvValue>> rows = new ArrayList<>();
        Set<String> columns = new LinkedHashSet<>(FIXED_COLUMNS);

        for (ApiRecord record : allRecords) {
            Map<String, CsvValue> row = createRow(record);
            rows.add(row);
            columns.addAll(row.keySet());
        }

        List<String> header = new ArrayList<>(columns);
        StringBuilder content = new StringBuilder();
        content.append(String.join(",", header)).append("\r\n");

        for (Map<String, CsvValue> row : rows) {
            for (int i = 0; i < header.size(); i++) {
                if (i > 0) {
                    content.append(',');
                }
                CsvValue value = row.getOrDefault(header.get(i), new CsvValue("", false));
                content.append(escape(value));
            }
            content.append("\r\n");
        }

        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    @Override
    public List<ApiRecord> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Файл не найден: " + path);
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return List.of();
        }

        List<List<CsvCell>> csvRows = parseCsv(content);
        if (csvRows.isEmpty()) {
            return List.of();
        }

        List<String> header = csvRows.getFirst().stream().map(CsvCell::value).toList();
        if (header.size() < FIXED_COLUMNS.size()
                || !header.subList(0, FIXED_COLUMNS.size()).equals(FIXED_COLUMNS)) {
            throw new IOException("Некорректный заголовок CSV-файла: " + path);
        }

        List<ApiRecord> records = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < csvRows.size(); rowIndex++) {
            List<CsvCell> row = csvRows.get(rowIndex);
            if (row.size() != header.size()) {
                throw new IOException("Некорректная строка CSV: " + (rowIndex + 1));
            }

            JsonNode data = createDataRoot(header, row);
            Map<String, String> parameters = new LinkedHashMap<>();
            for (int columnIndex = FIXED_COLUMNS.size(); columnIndex < header.size(); columnIndex++) {
                String column = header.get(columnIndex);
                CsvCell cell = row.get(columnIndex);
                if (column.startsWith("data.")) {
                    if (hasValue(cell)) {
                        insertValue(data, column.substring("data.".length()).split("\\."), parseValue(cell));
                    }
                } else if (column.startsWith("parameters.") && hasValue(cell)) {
                    parameters.put(column.substring("parameters.".length()), cell.value());
                }
            }

            records.add(new ApiRecord(
                    row.get(1).value(),
                    Instant.parse(row.get(2).value()),
                    Map.copyOf(parameters),
                    data
            ));
        }
        return records;
    }

    private Map<String, CsvValue> createRow(ApiRecord record) {
        Map<String, CsvValue> row = new LinkedHashMap<>();
        row.put("id", new CsvValue(createId(record), false));
        row.put("source", new CsvValue(record.source(), true));
        row.put("timestamp", new CsvValue(record.fetchedAt().toString(), true));
        flatten(record.node(), "data", row);
        record.parameters().forEach((key, value) -> row.put("parameters." + key, new CsvValue(value, true)));
        return row;
    }

    private String createId(ApiRecord record) {
        String value = record.source() + "|" + record.fetchedAt();
        UUID uuid = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
        return Long.toUnsignedString(uuid.getMostSignificantBits());
    }

    private void flatten(JsonNode node, String path, Map<String, CsvValue> row) {
        if (node.isObject()) {
            if (node.isEmpty()) {
                row.put(path, new CsvValue("{}", false));
                return;
            }
            node.fields().forEachRemaining(entry -> flatten(entry.getValue(), path + "." + entry.getKey(), row));
            return;
        }

        if (node.isArray()) {
            if (node.isEmpty()) {
                row.put(path, new CsvValue("[]", false));
                return;
            }
            for (int i = 0; i < node.size(); i++) {
                flatten(node.get(i), path + "." + i, row);
            }
            return;
        }

        row.put(path, new CsvValue(node.isNull() ? "null" : node.asText(), node.isTextual()));
    }

    private String escape(CsvValue cell) {
        String value = cell.value();
        if (cell.text() || value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private List<List<CsvCell>> parseCsv(String content) throws IOException {
        List<List<CsvCell>> rows = new ArrayList<>();
        List<CsvCell> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean wasQuoted = false;

        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);

            if (quoted) {
                if (current == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        value.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    value.append(current);
                }
                continue;
            }

            if (current == '"') {
                if (value.length() != 0 || wasQuoted) {
                    throw new IOException("Некорректное экранирование CSV");
                }
                quoted = true;
                wasQuoted = true;
            } else if (current == ',') {
                row.add(new CsvCell(value.toString(), wasQuoted));
                value.setLength(0);
                wasQuoted = false;
            } else if (current == '\n' || current == '\r') {
                row.add(new CsvCell(value.toString(), wasQuoted));
                value.setLength(0);
                if (!row.isEmpty()) {
                    rows.add(row);
                }
                row = new ArrayList<>();
                wasQuoted = false;
                if (current == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                value.append(current);
            }
        }

        if (quoted) {
            throw new IOException("Незакрытая кавычка в CSV");
        }
        if (!row.isEmpty() || value.length() > 0 || wasQuoted) {
            row.add(new CsvCell(value.toString(), wasQuoted));
            rows.add(row);
        }
        return rows;
    }

    private JsonNode parseValue(CsvCell cell) throws IOException {
        String value = cell.value();
        if (cell.quoted()) {
            return TextNode.valueOf(value);
        }
        if (value.equals("null")) {
            return NullNode.instance;
        }
        if (value.equals("true")) {
            return mapper.getNodeFactory().booleanNode(true);
        }
        if (value.equals("false")) {
            return mapper.getNodeFactory().booleanNode(false);
        }
        if (value.equals("{}") || value.equals("[]") || value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
            return mapper.readTree(value);
        }
        return TextNode.valueOf(value);
    }

    private JsonNode createDataRoot(List<String> header, List<CsvCell> row) throws IOException {
        for (int i = FIXED_COLUMNS.size(); i < header.size(); i++) {
            String column = header.get(i);
            CsvCell cell = row.get(i);
            if (column.equals("data") && hasValue(cell)) {
                return parseValue(cell);
            }
            if (column.startsWith("data.") && hasValue(cell)) {
                String firstSegment = column.substring("data.".length()).split("\\.")[0];
                if (isIndex(firstSegment)) {
                    return mapper.createArrayNode();
                }
            }
        }
        return mapper.createObjectNode();
    }

    private void insertValue(JsonNode root, String[] path, JsonNode value) throws IOException {
        JsonNode current = root;

        for (int i = 0; i < path.length; i++) {
            boolean last = i == path.length - 1;
            String segment = path[i];
            String next = last ? "" : path[i + 1];

            if (current.isObject()) {
                ObjectNode object = (ObjectNode) current;
                if (last) {
                    object.set(segment, value);
                } else {
                    JsonNode child = object.get(segment);
                    if (child == null) {
                        child = isIndex(next) ? mapper.createArrayNode() : mapper.createObjectNode();
                        object.set(segment, child);
                    }
                    current = child;
                }
            } else if (current.isArray()) {
                if (!isIndex(segment)) {
                    throw new IOException("Некорректный индекс массива в CSV: " + segment);
                }
                ArrayNode array = (ArrayNode) current;
                int index = Integer.parseInt(segment);
                while (array.size() <= index) {
                    array.add(NullNode.instance);
                }
                if (last) {
                    array.set(index, value);
                } else {
                    JsonNode child = array.get(index);
                    if (child.isNull()) {
                        child = isIndex(next) ? mapper.createArrayNode() : mapper.createObjectNode();
                        array.set(index, child);
                    }
                    current = child;
                }
            } else {
                throw new IOException("Некорректная структура пути CSV");
            }
        }
    }

    private boolean hasValue(CsvCell cell) {
        return !cell.value().isEmpty() || cell.quoted();
    }

    private boolean isIndex(String value) {
        return value.matches("[0-9]+");
    }

    private record CsvValue(String value, boolean text) {
    }

    private record CsvCell(String value, boolean quoted) {
    }
}
