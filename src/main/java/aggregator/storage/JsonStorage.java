package aggregator.storage;

import aggregator.model.ApiRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonStorage implements DataStorage {
    private final ObjectMapper mapper;

    public JsonStorage(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public synchronized void save(List<ApiRecord> records, Path path, boolean append) throws IOException {
        List<ApiRecord> result = new ArrayList<>();
        if (append && Files.exists(path) && Files.size(path) > 0) {
            result.addAll(load(path));
        }
        result.addAll(records);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writer(SerializationFeature.INDENT_OUTPUT).writeValue(path.toFile(), result);
    }

    @Override
    public synchronized List<ApiRecord> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Файл не найден: " + path);
        }
        if (Files.size(path) == 0) {
            return List.of();
        }
        ApiRecord[] records = mapper.readValue(path.toFile(), ApiRecord[].class);
        return List.of(records);
    }
}
