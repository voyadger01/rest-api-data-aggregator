# REST API Data Aggregator

Приложение на Java 25 и Spring Boot. Оно периодически опрашивает три REST API параллельно, приводит ответы к общей модели `ApiRecord` и сохраняет их в JSON или CSV.
## Используемые API

| Источник | API | Данные | Query-параметры |
|---|---|---|---|
| `weather` | [Open-Meteo](https://open-meteo.com/en/docs) | Текущая погода по координатам | `latitude`, `longitude`, `current`, `timezone` |
| `users` | [JSONPlaceholder](https://jsonplaceholder.typicode.com/) | Профили пользователей с адресами и информацией о компаниях | `username` |
| `comments` | [DummyJSON Comments](https://dummyjson.com/docs/comments) | Комментарии, реакции и данные авторов | `sortBy`, `order` |

## Конфигурация

Настройки находятся в `src/main/resources/application.yml`:

- `aggregator.polling.n` — максимальное число одновременно выполняемых задач;
- `aggregator.polling.t` — задержка в секундах между завершением задачи и её следующим запуском;
- `aggregator.polling.format` — `json` или `csv`;
- `aggregator.polling.file` — путь к файлу результатов;
- `aggregator.polling.append` — добавлять ли новые записи к существующим;
- `aggregator.polling.sources` — наборы параметров. Каждый элемент списка — отдельная задача (API + конкретные параметры). В конфигурации задано минимум по два набора для каждого API.

В `POST /polling/start` все поля необязательны. Неуказанные верхнеуровневые настройки берутся из YAML. Если в запросе переданы наборы для конкретного API, они заменяют YAML-наборы только этого API; остальные API остаются из конфигурации.

## Запуск

Требуется JDK 25.

```bash
./gradlew bootRun
```

Windows:

```bat
gradlew.bat bootRun
```

Сборка и тесты:

```bash
./gradlew clean test
./gradlew jacocoTestReport
./gradlew check
```

HTML-отчёт JaCoCo: `build/reports/jacoco/test/html/index.html`.

## REST API

### Запустить опрос

```bash
curl -X POST http://localhost:8080/polling/start \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Можно переопределить отдельные настройки и наборы параметров:

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

Остановка отменяет будущие запуски и дожидается завершения уже выполняющихся задач.

### Статус

```bash
curl http://localhost:8080/polling/status
```

Возвращает `running`, фактические `n` и `t`, активные источники и число запланированных задач.

### Прочитать данные

```bash
curl http://localhost:8080/data
curl 'http://localhost:8080/data?source=weather'
```

## Архитектура

- `DataAggregator` и `ApiSource` — переиспользованная логика этапа 1.
- `PollingScheduler` — отдельный компонент периодического опроса. Для каждой пары API + параметры создаётся отдельная задача `scheduleWithFixedDelay`; поэтому повтор одной задачи начинается не раньше чем через `t` секунд после её завершения.
- `ScheduledThreadPoolExecutor` объявлен Spring-бином. Его размер устанавливается в `n`; Spring закрывает пул при завершении контекста.
- `JsonStorage` и `CsvStorage` синхронизируют чтение и запись. Дополнительно запись из разных задач сериализуется планировщиком, чтобы режим `append=false` применялся только к первой записи.
- `PollingController` содержит только HTTP-маршрутизацию и делегирует работу планировщику.

Каждый запуск задачи пишет `[START]` и `[END]` с временем до миллисекунд, API, параметрами и результатом. Логи пишутся в `logs/application.log` и консоль.

Повторный `POST /polling/start`, пока опрос уже запущен, возвращает HTTP 409. Некорректные настройки возвращают HTTP 400. Попытка остановить не запущенный опрос также возвращает HTTP 409.

