package aggregator.storage;

import aggregator.model.ApiRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface DataStorage {
    void save(List<ApiRecord> records, Path path, boolean append) throws IOException;

    List<ApiRecord> load(Path path) throws IOException;
}
