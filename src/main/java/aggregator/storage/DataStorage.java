package aggregator.storage;

import aggregator.model.ApiRecord;

import java.nio.file.Path;
import java.util.List;

public interface DataStorage {
    void save(List<ApiRecord> records, Path path);
    List<ApiRecord> load(Path path);

}
