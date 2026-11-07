package aggregator.polling;

import aggregator.DataAggregator;
import aggregator.config.PollingProperties;
import aggregator.model.ApiRecord;
import aggregator.storage.DataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class PollingScheduler {
    private static final Logger logger = LoggerFactory.getLogger(PollingScheduler.class);
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final DataAggregator aggregator;
    private final Map<String, DataStorage> storages;
    private final PollingProperties defaults;
    private final java.util.concurrent.ScheduledThreadPoolExecutor executor;
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private final Object lifecycle = new Object();
    private volatile boolean running;
    private volatile int activeTasks;
    private int n;
    private long t;
    private String format;
    private String file;
    private boolean append;
    private List<String> activeSources = List.of();

    public PollingScheduler(DataAggregator aggregator, @org.springframework.beans.factory.annotation.Qualifier("dataStorages") Map<String, DataStorage> storages,
                            PollingProperties defaults, java.util.concurrent.ScheduledThreadPoolExecutor executor) {
        this.aggregator = aggregator;
        this.storages = storages;
        this.defaults = defaults;
        this.executor = executor;
        this.n = defaults.getN();
        this.t = defaults.getT();
        this.format = defaults.getFormat();
        this.file = defaults.getFile();
        this.append = defaults.isAppend();
    }

    public void start(PollingStartRequest request) {
        synchronized (lifecycle) {
            if (running) throw new IllegalStateException("Опрос уже запущен");
            n = request.n() == null ? defaults.getN() : request.n();
            t = request.t() == null ? defaults.getT() : request.t();
            format = request.format() == null ? defaults.getFormat() : request.format();
            file = request.file() == null ? defaults.getFile() : request.file();
            append = request.append() == null ? defaults.isAppend() : request.append();
            Map<String, List<Map<String, String>>> configured = new LinkedHashMap<>(defaults.getSources());
            if (request.sources() != null) {
                request.sources().forEach((source, sets) -> configured.put(source.toLowerCase(java.util.Locale.ROOT), sets));
            }
            validate(n, t, format, file, configured);
            DataStorage storage = storages.get(format.toLowerCase(java.util.Locale.ROOT));
            List<Runnable> tasks = createTasks(configured, storage);
            if (tasks.isEmpty()) throw new IllegalArgumentException("Не задано ни одной задачи опроса");
            activeSources = configured.keySet().stream().sorted().toList();
            executor.setCorePoolSize(n);
            running = true;
            try {
                for (Runnable task : tasks) {
                    futures.add(executor.scheduleWithFixedDelay(task, 0, t, TimeUnit.SECONDS));
                }
            } catch (RuntimeException exception) {
                futures.forEach(future -> future.cancel(false));
                futures.clear();
                running = false;
                throw exception;
            }
            logger.info("Polling started: n={}, t={}, tasks={}, format={}, file={}", n, t, tasks.size(), format, file);
        }
    }

    private List<Runnable> createTasks(Map<String, List<Map<String, String>>> configured, DataStorage storage) {
        List<Runnable> tasks = new ArrayList<>();
        configured.forEach((source, parameterSets) -> {
            if (parameterSets == null || parameterSets.isEmpty())
                throw new IllegalArgumentException("Для API " + source + " нужен хотя бы один набор параметров");
            for (Map<String, String> parameters : parameterSets) {
                if (parameters == null || parameters.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)) {
                    throw new IllegalArgumentException("Параметры API должны иметь непустые ключи и значения");
                }
                Map<String, String> snapshot = Map.copyOf(parameters);
                tasks.add(() -> executeTask(source.toLowerCase(java.util.Locale.ROOT), snapshot, storage));
            }
        });
        return tasks;
    }

    private void executeTask(String source, Map<String, String> parameters, DataStorage storage) {
        synchronized (lifecycle) { activeTasks++; }
        String details = source + " " + parameters;
        logger.info("{} [START] {} parameters={}", LocalTime.now().format(LOG_TIME), source, parameters);
        String result = "OK";
        try {
            List<ApiRecord> records = aggregator.collect(List.of(source), Map.of(source, parameters));
            synchronized (lifecycle) {
                storage.save(records, Path.of(file), append);
                append = true;
            }
        } catch (Exception exception) {
            result = "ERROR: " + exception.getMessage();
            logger.error("Polling task failed: {}", details, exception);
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            logger.info("{} [END] {} parameters={} result={}", LocalTime.now().format(LOG_TIME), source, parameters, result);
            synchronized (lifecycle) {
                activeTasks--;
                lifecycle.notifyAll();
            }
        }
    }

    public void stop() {
        synchronized (lifecycle) {
            if (!running) throw new IllegalStateException("Опрос не запущен");
            running = false;
            futures.forEach(future -> future.cancel(false));
            futures.clear();
            boolean interrupted = false;
            while (activeTasks > 0) {
                try { lifecycle.wait(); }
                catch (InterruptedException exception) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
            activeSources = List.of();
            logger.info("Polling stopped");
        }
    }

    public PollingStatus status() {
        return new PollingStatus(running, n, t, activeSources, futures.size());
    }

    public List<ApiRecord> readData(String source) throws java.io.IOException {
        String selectedFormat;
        String selectedFile;
        synchronized (lifecycle) {
            selectedFormat = format == null ? defaults.getFormat() : format;
            selectedFile = file == null ? defaults.getFile() : file;
        }
        List<ApiRecord> records = storages.get(selectedFormat.toLowerCase(java.util.Locale.ROOT)).load(Path.of(selectedFile));
        if (source == null || source.isBlank()) return records;
        return records.stream().filter(record -> record.source().equalsIgnoreCase(source)).toList();
    }

    private void validate(int n, long t, String format, String file,
                          Map<String, List<Map<String, String>>> sources) {
        if (n < 1) throw new IllegalArgumentException("n должен быть положительным числом");
        if (t < 1) throw new IllegalArgumentException("t должен быть положительным числом секунд");
        if (format == null || !storages.containsKey(format.toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("Формат должен быть json или csv");
        if (file == null || file.isBlank()) throw new IllegalArgumentException("Путь к файлу не может быть пустым");
        if (sources == null || sources.isEmpty()) throw new IllegalArgumentException("Не заданы источники API");
        sources.keySet().forEach(source -> {
            if (!aggregator.sourceNames().contains(source.toLowerCase(java.util.Locale.ROOT)))
                throw new IllegalArgumentException("Неизвестный API: " + source);
        });
    }
}
