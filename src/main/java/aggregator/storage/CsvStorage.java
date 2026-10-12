package aggregator.storage;

import aggregator.model.ApiRecord;

import java.nio.file.Path;
import java.util.List;

public class CsvStorage implements DataStorage {
    @Override
    public void save(List<ApiRecord> records, Path path) {

    }

    @Override
    public List<ApiRecord> load(Path path) {
        return List.of();
    }

}
