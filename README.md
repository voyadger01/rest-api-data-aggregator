# REST API Data Aggregator

Многопоточное Java-приложение на Spring Boot. Оно периодически опрашивает три открытых REST API, приводит ответы к общей модели `ApiRecord` и сохраняет результаты в JSON или CSV.

## Используемые API

| Источник | API | Данные | Query-параметры |
|---|---|---|---|
| `weather` | [Open-Meteo](https://open-meteo.com/en/docs) | Текущая погода по координатам | `latitude`, `longitude`, `current`, `timezone` |
| `users` | [JSONPlaceholder](https://jsonplaceholder.typicode.com/) | Профили пользователей с адресами и информацией о компаниях | `username` |
| `comments` | [DummyJSON Comments](https://dummyjson.com/docs/comments) | Комментарии, реакции и данные авторов | `sortBy`, `order` |

В `application.yml` для каждого источника настроено по два набора параметров. Каждый набор является отдельной задачей планировщика.

## Конфигурация

Настройки находятся в `src/main/resources/application.yml`:

- `aggregator.polling.n` — максимальное число одновременно выполняемых задач;
- `aggregator.polling.t` — минимальная задержка в секундах между завершением задачи и её следующим запуском;
- `aggregator.polling.format` — `json` или `csv`;
- `aggregator.polling.file` — путь к файлу результатов;
- `aggregator.polling.append` — добавлять ли новые записи к существующим;
- `aggregator.polling.sources` — наборы параметров запросов для каждого API.

В теле `POST /polling/start` все поля необязательны. Неуказанные настройки берутся из YAML. Переданные наборы параметров полностью заменяют YAML-наборы только для соответствующих источников; остальные источники сохраняют настройки из конфигурации.

## Сборка, запуск и тестирование

Требуется JDK 25.

```bash
./gradlew clean test
./gradlew jacocoTestReport
./gradlew check
./gradlew bootRun
```

В Windows используйте `gradlew.bat`.

`check` запускает проверку JaCoCo и завершится ошибкой, если покрытие строками ниже 70%. HTML-отчёт находится в `build/reports/jacoco/test/html/index.html`, XML-отчёт — в `build/reports/jacoco/test/jacocoTestReport.xml`.

Тесты используют Mockito и MockMvc. Вызовы внешних API в тестах не выполняются. Многопоточные сценарии синхронизированы через `CountDownLatch` и ограниченные ожидания.

## REST API

### Запустить опрос

```bash
curl -X POST http://localhost:8080/polling/start \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Можно переопределить настройки и наборы параметров для одного или нескольких источников:

```bash
curl -X POST http://localhost:8080/polling/start \
  -H 'Content-Type: application/json' \
  -d '{
    "n": 4,
    "t": 20,
    "format": "csv",
    "file": "src/main/resources/results.csv",
    "append": true,
    "sources": {
      "weather": [
        {"latitude":"48.8566", "longitude":"2.3522", "current":"temperature_2m,relative_humidity_2m", "timezone":"auto"},
        {"latitude":"35.6762", "longitude":"139.6503", "current":"temperature_2m,relative_humidity_2m", "timezone":"auto"}
      ]
    }
  }'
```

### Остановить опрос

```bash
curl -X POST http://localhost:8080/polling/stop
```

Остановка отменяет будущие запуски, дожидается завершения уже выполняющихся задач и только затем разрешает новый запуск.

### Получить статус

```bash
curl http://localhost:8080/polling/status
```

Возвращаются состояние опроса, значения `n` и `t`, активные источники и число запланированных задач.

### Прочитать данные

```bash
curl http://localhost:8080/data
curl 'http://localhost:8080/data?source=weather'
```

### Коды ошибок

- `400 Bad Request` — некорректное тело запроса или параметры запуска;
- `409 Conflict` — повторный запуск уже работающего опроса или остановка неработающего;
- `500 Internal Server Error` — ошибка чтения файла через `GET /data`; ошибки фоновой записи фиксируются в `[END]`-логе соответствующей задачи.

## Архитектура

```
    Client[REST-клиент] --> Controller[PollingController]
    Controller --> Scheduler[PollingScheduler]
    Scheduler --> Pool[ScheduledThreadPoolExecutor]
    Scheduler --> Aggregator[DataAggregator]
    Aggregator --> Registry[Реестр ApiSource]
    Registry --> Weather[OpenMeteoSource]
    Registry --> Users[JsonPlaceholderUsersSource]
    Registry --> Comments[DummyJsonCommentsSource]
    Aggregator --> Http[HttpClientService]
    Http --> External[Внешние REST API]
    Scheduler --> Storage[DataStorage]
    Storage --> Json[JsonStorage]
    Storage --> Csv[CsvStorage]
```

- `ApiSource` задаёт общий контракт источника: имя, URL, параметры по умолчанию и извлечение данных из ответа. Конкретные источники являются Spring-компонентами и автоматически попадают в реестр. Для подключения нового API достаточно добавить класс, реализующий `ApiSource` и помеченный `@Component`; существующие классы агрегатора и конфигурации менять не требуется.
- `HttpClientService` отвечает за HTTP-запросы.
- `DataAggregator` объединяет параметры, вызывает источник, разбирает JSON и создаёт `ApiRecord`.
- `DataStorage` отделяет формат хранения от бизнес-логики; реализации `JsonStorage` и `CsvStorage` синхронизируют чтение и запись.
- `PollingScheduler` отвечает за планирование и жизненный цикл задач. Для каждой пары «источник + набор параметров» создаётся отдельная задача `scheduleWithFixedDelay`, поэтому следующая попытка начинается не ранее чем через `t` секунд после завершения предыдущей попытки этой же задачи. Размер пула равен `n`.
- Пул потоков объявлен Spring-бином и закрывается вместе с контекстом приложения.
- Контроллеры только принимают HTTP-запросы и делегируют операции планировщику.

Каждый опрос записывает две строки в лог: `[START]` непосредственно перед обращением к источнику и `[END]` после завершения с результатом `OK` или `ERROR`. Время указывается с точностью до миллисекунд, также указываются API и параметры запроса. Логи сохраняются в `logs/application.log` и выводятся в консоль.

## Требования к отчёту

Для сдачи этапа 2 отдельно подготовьте PDF-отчёт: список выбранных API, схему архитектуры и обработки данных, эндпоинты с примерами вызовов, инструкции по сборке/запуску/тестированию и скриншот HTML-отчёта JaCoCo с процентами покрытия.
