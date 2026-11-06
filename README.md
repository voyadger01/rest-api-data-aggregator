# REST API Data Aggregator

Консольное однопоточное Java-приложение для опроса открытых REST API, приведения ответов к общей модели и сохранения результатов в JSON или CSV.

## Используемые API

Приложение работает с тремя открытыми API без API-ключей:

| Имя в программе | Источник | Что получает | Параметры запроса |
|---|---|---|---|
| `weather` | [Open-Meteo Forecast API](https://open-meteo.com/en/docs) | Текущая погода и почасовой прогноз: температура, влажность, осадки, ветер и другие данные | `latitude`, `longitude`, `current`, `hourly`, `forecast_days`, `timezone` |
| `food` | [Open Food Facts Search API](https://openfoodfacts.github.io/documentation/docs/Product-Opener/v2/search/get-search/) | Продукты с названием, брендами, категориями, составом и пищевой ценностью | `categories_tags_en`, `page`, `page_size`, `fields` |
| `github` | [GitHub REST API — commits](https://docs.github.com/en/rest/commits/commits#list-commits) | Коммиты репозитория Linux: SHA, автор, коммиттер, сообщение, даты и родительские коммиты | `per_page`, `page` |

Все три ответа содержат вложенную структуру и не менее пяти полей в объекте данных: у Open-Meteo это поля координат, часового пояса, `current_units`, `current`, `hourly_units` и `hourly`; у Open Food Facts — `count`, `page`, `page_count`, `page_size`, `products`, а каждый продукт содержит вложенные данные о пищевой ценности; у GitHub каждый элемент списка коммитов содержит, в частности, `sha`, `node_id`, `commit`, `url`, `html_url`, `author`, `committer` и `parents`. Параметры координат, категорий и пагинации меняют ответы источников.

Все ответы сохраняются в общей модели `ApiRecord`: источник, время получения, фактически использованные параметры запроса и JSON-данные ответа. Вложенные объекты и массивы исходного ответа сохраняются без потери структуры.

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

Тесты проверяют разбор аргументов CLI, объединение параметров и маппинг ответа агрегатором без сетевых запросов, HTTP-клиент через Mockito, а также чтение, запись и дозапись JSON/CSV.


## Запуск

Команда `collect` опрашивает указанные API и сохраняет полученные записи.

```bash
./gradlew run --args="collect --apis weather,food,github --format json --mode create --file data/results.json"
```

Для CSV:

```bash
./gradlew run --args="collect --apis weather,food,github --format csv --mode create --file data/results.csv"
```

Доступные режимы записи:

- `create` — создать файл или перезаписать существующий;
- `overwrite` — то же поведение, что и `create`;
- `append` — дозаписать новые записи. Для JSON приложение перечитывает существующий массив и сохраняет общий массив; для CSV добавляет строки без повторной записи заголовка.

### Свои параметры API

Параметр передаётся в формате `--param имя_api.параметр=значение`. Опцию можно указывать несколько раз.

Например, запрос погоды для Парижа:

```bash
./gradlew run --args="collect --apis weather --format json --file data/paris.json --param weather.latitude=48.8566 --param weather.longitude=2.3522"
```

Фильтр продуктов и настройка выдачи GitHub:

```bash
./gradlew run --args="collect --apis food,github --format json --file data/search.json --param food.categories_tags_en=beverages --param food.page_size=5 --param github.per_page=3 --param github.page=2"
```

Неуказанные параметры берутся из значений по умолчанию в соответствующем классе источника.

## Чтение сохранённых данных

Команда `read` выводит содержимое файла в консоль. Формат определяется по расширению `.json` или `.csv`.

```bash
./gradlew run --args="read --file data/results.json"
./gradlew run --args="read --file data/results.csv"
```

Чтобы вывести записи только одного источника:

```bash
./gradlew run --args="read --file data/results.json --source weather"
```

Можно указать формат явно:

```bash
./gradlew run --args="read --file data/export.dat --format csv"
```

Список команд и параметров:

```bash
./gradlew run --args="--help"
```

## Архитектура и обработка данных

1. `Main` собирает зависимости и реестр источников.
2. `CLIParser` разбирает аргументы командной строки и проверяет их.
3. `CLIController` выбирает операцию и нужное хранилище. В контроллере нет логики HTTP-запросов.
4. `DataAggregator` для каждого выбранного источника объединяет параметры по умолчанию с пользовательскими, вызывает HTTP-клиент и создаёт `ApiRecord`.
5. `HttpClientService` выполняет GET-запросы и проверяет HTTP-статус ответа.
6. `JsonStorage` или `CsvStorage` сохраняет записи либо читает их обратно.

Чтобы добавить новый источник, достаточно реализовать `ApiSource` в отдельном классе и зарегистрировать его в `Main`. Логика агрегации и хранения при этом не меняется.
