# REST API Data Aggregator

Консольное Java-приложение для сбора данных из трёх открытых REST API, приведения ответов к общей модели и сохранения в JSON или CSV.

## Используемые API

| Источник | API | Данные | Query-параметры |
|---|---|---|---|
| `weather` | [Open-Meteo](https://open-meteo.com/en/docs) | Текущая погода по координатам | `latitude`, `longitude`, `current`, `timezone` |
| `users` | [JSONPlaceholder](https://jsonplaceholder.typicode.com/) | Профили пользователей с адресами и информацией о компаниях | `username` |
| `comments` | [DummyJSON Comments](https://dummyjson.com/docs/comments) | Комментарии, реакции и данные авторов | `sortBy`, `order` |

## Требования

- JDK 25 или новее
- Gradle Wrapper, включённый в проект
- Подключение к интернету для запросов к API и первичной загрузки зависимостей Gradle

## Сборка

Linux/macOS:

```bash
./gradlew clean build
```

Windows:

```powershell
.\gradlew.bat clean build
```

## Тесты

```bash
./gradlew test
```

Тесты проверяют разбор аргументов CLI, конфигурацию источников, HTTP-клиент через Mockito, а также чтение, запись и дозапись JSON/CSV без сетевых вызовов.

## Запуск

Команда `collect` опрашивает указанные API и сохраняет полученные записи. Для примеров файлы сохраняются в `src/main/resources`.

Сбор данных в JSON:

```bash
./gradlew run --args="collect --apis weather,users,comments --format json --mode create --file src/main/resources/results.json"
```

Сбор данных в CSV:

```bash
./gradlew run --args="collect --apis weather,users,comments --format csv --mode create --file src/main/resources/results.csv"
```

## Архитектура и обработка данных

1. `Main` собирает зависимости и реестр источников.
2. `CLIParser` разбирает аргументы командной строки и проверяет их.
3. `CLIController` выбирает операцию и нужное хранилище. В контроллере нет логики HTTP-запросов.
4. `DataAggregator` для каждого выбранного источника объединяет параметры по умолчанию с пользовательскими, вызывает HTTP-клиент и создаёт `ApiRecord`.
5. `HttpClientService` выполняет GET-запросы и проверяет HTTP-статус ответа.
6. `JsonStorage` или `CsvStorage` сохраняет записи либо читает их обратно.

Чтобы добавить новый источник, достаточно реализовать `ApiSource` в отдельном классе и зарегистрировать его в `Main`. Логика агрегации и хранения при этом не меняется.
