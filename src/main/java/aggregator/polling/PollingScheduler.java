package aggregator.polling;

import aggregator.DataAggregator;
import aggregator.config.PollingProperties;
import aggregator.model.ApiRecord;
import aggregator.storage.DataStorage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class PollingScheduler {
    private static final Logger logger = LoggerFactory.getLogger(PollingScheduler.class);
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final DataAggregator aggregator;
    private final Map<String, DataStorage> storages;
    private final PollingProperties defaults;
    private final ScheduledThreadPoolExecutor executor;
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private final Object lifecycle = new Object();
    private volatile boolean running;
    private int activeTasks;
    private int n;
    private long t;
    private String format;
    private String file;
    private boolean append;
    private List<String> activeSources = List.of();

    public PollingScheduler(
            DataAggregator aggregator,
            @Qualifier("dataStorages") Map<String, DataStorage> storages,
            PollingProperties defaults,
            ScheduledThreadPoolExecutor executor
    ) {
        this.aggregator = aggregator;
        this.storages = Map.copyOf(storages);
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
            if (running) {
                throw new IllegalStateException("Опрос уже запущен");
            }

            int requestedN = request.n() == null ? defaults.getN() : request.n();
            long requestedT = request.t() == null ? defaults.getT() : request.t();
            String requestedFormat = request.format() == null ? defaults.getFormat() : request.format();
            String requestedFile = request.file() == null ? defaults.getFile() : request.file();
            boolean requestedAppend = request.append() == null ? defaults.isAppend() : request.append();
            Map<String, List<Map<String, String>>> configured = mergeSources(request.sources());

            validate(requestedN, requestedT, requestedFormat, requestedFile, configured);
            DataStorage storage = storages.get(requestedFormat.toLowerCase(Locale.ROOT));
            List<Runnable> tasks = createTasks(configured, storage);
            if (tasks.isEmpty()) {
                throw new IllegalArgumentException("Не задано ни одной задачи опроса");
            }

            executor.setCorePoolSize(requestedN);
            n = requestedN;
            t = requestedT;
            format = requestedFormat;
            file = requestedFile;
            append = requestedAppend;
            activeSources = configured.keySet().stream().map(source -> source.toLowerCase(Locale.ROOT)).sorted().toList();
            running = true;

            try {
                for (Runnable task : tasks) {
                    futures.add(executor.scheduleWithFixedDelay(task, 0, t, TimeUnit.SECONDS));
                }
            } catch (RuntimeException exception) {
                cancelTasks();
                awaitActiveTasks();
                running = false;
                activeSources = List.of();
                throw exception;
            }

            logger.info("Polling started: n={}, t={}, tasks={}, format={}, file={}", n, t, tasks.size(), format, file);
        }
    }

    private Map<String, List<Map<String, String>>> mergeSources(
            Map<String, List<Map<String, String>>> overrides
    ) {
        Map<String, List<Map<String, String>>> configured = new LinkedHashMap<>();
        if (defaults.getSources() != null) {
            defaults.getSources().forEach((source, parameterSets) ->
                    configured.put(source.toLowerCase(Locale.ROOT), parameterSets));
        }
        if (overrides != null) {
            overrides.forEach((source, parameterSets) ->
                    configured.put(source.toLowerCase(Locale.ROOT), parameterSets));
        }
        return configured;
    }

    private List<Runnable> createTasks(Map<String, List<Map<String, String>>> configured, DataStorage storage) {
        List<Runnable> tasks = new ArrayList<>();
        configured.forEach((source, parameterSets) -> {
            if (parameterSets == null || parameterSets.isEmpty()) {
                throw new IllegalArgumentException("Для API " + source + " нужен хотя бы один набор параметров");
            }
            for (Map<String, String> parameters : parameterSets) {
                if (parameters == null || parameters.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getKey().isBlank()
                                || entry.getValue() == null || entry.getValue().isBlank())) {
                    throw new IllegalArgumentException("Параметры API должны иметь непустые ключи и значения");
                }
                Map<String, String> snapshot = Map.copyOf(parameters);
                tasks.add(() -> executeTask(source, snapshot, storage));
            }
        });
        return tasks;
    }

    private void executeTask(String source, Map<String, String> parameters, DataStorage storage) {
        synchronized (lifecycle) {
            if (!running) {
                return;
            }
            activeTasks++;
        }

        logger.info("{} [START] {} parameters={}", LocalTime.now().format(LOG_TIME), source, parameters);
        String result = "OK";
        try {
            List<ApiRecord> records = aggregator.collect(List.of(source), Map.of(source, parameters));
            synchronized (lifecycle) {
                storage.save(records, Path.of(file), append);
                append = true;
            }
        } catch (Exception exception) {
            result = "ERROR: " + String.valueOf(exception.getMessage()).replaceAll("[\r\n]+", " ");
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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
            if (!running) {
                throw new IllegalStateException("Опрос не запущен");
            }
            stopCurrentRun();
        }
    }

    @PreDestroy
    public void shutdown() {
        synchronized (lifecycle) {
            if (running) {
                stopCurrentRun();
            }
        }
    }

    private void stopCurrentRun() {
        cancelTasks();
        awaitActiveTasks();
        running = false;
        activeSources = List.of();
        logger.info("Polling stopped");
    }

    private void cancelTasks() {
        futures.forEach(future -> future.cancel(false));
        futures.clear();
    }

    private void awaitActiveTasks() {
        boolean interrupted = false;
        while (activeTasks > 0) {
            try {
                lifecycle.wait();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public PollingStatus status() {
        synchronized (lifecycle) {
            return new PollingStatus(running, n, t, activeSources, futures.size());
        }
    }

    public List<ApiRecord> readData(String source) throws IOException {
        String selectedFormat;
        String selectedFile;
        synchronized (lifecycle) {
            selectedFormat = format == null ? defaults.getFormat() : format;
            selectedFile = file == null ? defaults.getFile() : file;
        }
        List<ApiRecord> records = storages.get(selectedFormat.toLowerCase(Locale.ROOT)).load(Path.of(selectedFile));
        if (source == null || source.isBlank()) {
            return records;
        }
        return records.stream().filter(record -> record.source().equalsIgnoreCase(source)).toList();
    }

    private void validate(
            int n,
            long t,
            String format,
            String file,
            Map<String, List<Map<String, String>>> sources
    ) {
        if (n < 1) {
            throw new IllegalArgumentException("n должен быть положительным числом");
        }
        if (t < 1) {
            throw new IllegalArgumentException("t должен быть положительным числом секунд");
        }
        if (format == null || !storages.containsKey(format.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Формат должен быть json или csv");
        }
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("Путь к файлу не может быть пустым");
        }
        Path.of(file);
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Не заданы источники API");
        }
        sources.keySet().forEach(source -> {
            if (!aggregator.sourceNames().contains(source.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Неизвестный API: " + source);
            }
        });
    }
}
