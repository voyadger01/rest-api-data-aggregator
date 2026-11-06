package aggregator.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CLIParser {
    private final String[] args;

    public CLIParser(String[] args) {
        this.args = args;
    }

    public Options parse() {
        if (isHelpRequested()) {
            return createHelpOptions();
        }

        String command = parseCommand();
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Map<String, String>> parameters = new LinkedHashMap<>();

        parseArguments(values, parameters);

        return createOptions(command, values, parameters);
    }

    private boolean isHelpRequested() {
        return args.length == 0
                || "help".equals(args[0])
                || List.of(args).contains("--help")
                || List.of(args).contains("-h");
    }

    private Options createHelpOptions() {
        return new Options("help", List.of(), null, false, null, null, Map.of());
    }

    private String parseCommand() {
        String command = args[0].toLowerCase(Locale.ROOT);

        if (!command.equals("collect") && !command.equals("read")) {
            throw new IllegalArgumentException("Неизвестная команда: " + args[0]);
        }

        return command;
    }

    private void parseArguments(
            Map<String, String> values,
            Map<String, Map<String, String>> parameters
    ) {
        int index = 1;

        while (index < args.length) {
            index = parseOption(index, values, parameters);
        }
    }

    private int parseOption(
            int index,
            Map<String, String> values,
            Map<String, Map<String, String>> parameters
    ) {
        String option = args[index];

        if (!option.startsWith("--")) {
            throw new IllegalArgumentException("Ожидался параметр, получено: " + option);
        }

        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("Не указано значение для " + option);
        }

        String value = args[index + 1];

        if (option.equals("--param")) {
            addParameter(parameters, value);
        } else if (isSupportedOption(option)) {
            addOption(values, option, value);
        } else {
            throw new IllegalArgumentException("Неизвестный параметр: " + option);
        }

        return index + 2;
    }

    private boolean isSupportedOption(String option) {
        return List.of("--apis", "--format", "--mode", "--file", "--source")
                .contains(option);
    }

    private void addOption(Map<String, String> values, String option, String value) {
        if (values.put(option, value) != null) {
            throw new IllegalArgumentException("Параметр указан несколько раз: " + option);
        }
    }

    private void addParameter(
            Map<String, Map<String, String>> parameters,
            String value
    ) {
        int separator = value.indexOf('.');
        int equals = value.indexOf('=');

        if (separator <= 0 || equals <= separator + 1 || equals == value.length() - 1) {
            throw new IllegalArgumentException("Формат --param: api.key=value");
        }

        String api = value.substring(0, separator).toLowerCase(Locale.ROOT);
        String key = value.substring(separator + 1, equals);
        String parameterValue = value.substring(equals + 1);

        parameters.computeIfAbsent(api, ignored -> new LinkedHashMap<>())
                .put(key, parameterValue);
    }

    private Options createOptions(
            String command,
            Map<String, String> values,
            Map<String, Map<String, String>> parameters
    ) {
        List<String> apis = values.containsKey("--apis")
                ? splitApis(values.get("--apis"))
                : List.of();

        String format = values.get("--format");
        String mode = values.getOrDefault("--mode", "create").toLowerCase(Locale.ROOT);
        String source = values.get("--source");

        validateMode(mode);

        Path file = values.containsKey("--file")
                ? Path.of(values.get("--file"))
                : null;

        if (file == null) {
            throw new IllegalArgumentException("Не указан обязательный параметр --file");
        }

        if (command.equals("collect")) {
            validateCollectOptions(values, apis, format, file);
        }

        if (command.equals("read") && format == null) {
            format = inferFormat(file);
        }

        validateFormat(format);

        return new Options(
                command,
                apis,
                format.toLowerCase(Locale.ROOT),
                mode.equals("append"),
                file,
                source,
                parameters
        );
    }

    private void validateMode(String mode) {
        if (!mode.equals("create") && !mode.equals("overwrite") && !mode.equals("append")) {
            throw new IllegalArgumentException("Режим должен быть create, overwrite или append");
        }
    }

    private void validateCollectOptions(
            Map<String, String> values,
            List<String> apis,
            String format,
            Path file
    ) {
        if (apis.isEmpty()) {
            throw new IllegalArgumentException("Для collect укажите --apis");
        }

        if (format == null) {
            throw new IllegalArgumentException("Для collect укажите --format json или csv");
        }

        validateFormat(format);

        String extension = file.getFileName() == null
                ? ""
                : file.getFileName().toString().toLowerCase(Locale.ROOT);

        if ((extension.endsWith(".json") && !format.equalsIgnoreCase("json"))
                || (extension.endsWith(".csv") && !format.equalsIgnoreCase("csv"))) {
            throw new IllegalArgumentException("Формат файла не совпадает с параметром --format");
        }

        if (values.containsKey("--source")) {
            throw new IllegalArgumentException("--source используется только с командой read");
        }
    }

    private void validateFormat(String format) {
        if (format == null
                || (!format.equalsIgnoreCase("json") && !format.equalsIgnoreCase("csv"))) {
            throw new IllegalArgumentException("Формат должен быть json или csv");
        }
    }

    private List<String> splitApis(String value) {
        List<String> apis = new ArrayList<>();

        for (String api : value.split(",")) {
            String name = api.trim().toLowerCase(Locale.ROOT);

            if (!name.isEmpty() && !apis.contains(name)) {
                apis.add(name);
            }
        }

        return apis;
    }

    private String inferFormat(Path file) {
        String name = file.getFileName() == null
                ? ""
                : file.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".json")) {
            return "json";
        }

        if (name.endsWith(".csv")) {
            return "csv";
        }

        throw new IllegalArgumentException(
                "Не удалось определить формат по расширению файла; укажите --format"
        );
    }

    public record Options(
            String command,
            List<String> apis,
            String format,
            boolean append,
            Path file,
            String source,
            Map<String, Map<String, String>> parameters
    ) {
    }
}
