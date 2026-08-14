# PLAN.md — мини-ассистент «по образу Коли» (Java 8 / Maven)

> «Коля» — внутренний референсный проект компании-заказчика, упомянутый в задании
> как образец подходов (в частности `HmacSigner` для аудит-журнала). Его исходников
> на этой машине нет и не будет — упоминания ниже носят пояснительный характер (почему
> выбрана альтернативная реализация hash-chain), а не задачу на поиск/копирование.

Версии зависимостей ниже проверены WebSearch на 2026-08-13; перед фактическим
добавлением в `pom.xml` — точечно перепроверить актуальную патч-версию (правило
«сверка с документацией перед использованием» применяется в момент реализации).

## 0. Обозначения

- **Effort: L / M / H** — низкий/средний/высокий уровень трудозатрат и рассуждения.
  L = механическая работа, extended thinking не нужен. H = архитектурное решение,
  делать в основной сессии, не делегировать субагенту без присмотра.
- **TDD:** RED (падающий тест, отдельный коммит) → GREEN (реализация, тест зелёный,
  отдельный коммит) — минимум два коммита на пункт с TDD.
- **[∥ group=N]** — можно выполнять параллельно (например, разными субагентами).
- **CP-N** — commit point: перед этим коммитом весь `mvn test` обязан быть зелёным.
- **PUSH** — рекомендованная точка `git push`.
- **SESSION BREAK** — точка завершения текущей сессии Claude Code и старта новой
  (ориентир ~300k токенов/сессию, см. `other/agent-logs/DECISIONS.md`).

---

## 1. Архитектура и структура пакетов

```
project/
  pom.xml
  README.md
  PLAN.md
  config/
    config.example.yaml
  src/main/java/ru/assistant/
    App.java
    config/
      AppConfig.java
      ConfigLoader.java
      EnvSecretResolver.java
    mail/
      Msg.java
      MailChannel.java
      MockMailChannel.java
      OutlookMailChannel.java
    llm/
      ChatMessage.java / ToolSpec.java / ToolCall.java / ChatResponse.java
      LlmClient.java
      MockLlmClient.java
      HttpLlmClient.java
      LlmException.java
    tools/
      Tool.java
      ToolError.java
      ToolRegistry.java
      CurrentDatetimeTool.java
      AddReminderTool.java
      FindItemsTool.java
      ReminderStore.java
    agent/
      AgentToolLoop.java
      MailProcessingService.java
      SeenStore.java
    infra/
      AtomicJsonFileStore.java
      LogEvents.java
      AuditLog.java
  src/test/java/ru/assistant/...   # зеркалит структуру main
```

Ключевые архитектурные решения (H-уровня, фиксировать в `DECISIONS.md` при реализации):

1. **Идемпотентность — двухуровневая защита.** `SeenStore` не доверяет `MailChannel`
   (даже мок может «показать» письмо повторно, как и реальный Outlook при глитче
   синхронизации) — перед tool-loop `MailProcessingService` обязан вызвать
   `SeenStore.markIfNew(id)`, ключ — `Msg.id` (EntryID/Message-ID), не тема/тело.
2. **Момент отметки «seen».** Письмо помечается seen **до** обработки (чтобы
   конкурентные повторные `fetchUnread()` не породили дублирование), но если
   обработка упала — отметка **откатывается** через `SeenStore.unmark(id)`, иначе
   письмо теряется навсегда при временном сбое. Повторная попытка при временной
   недоступности LLM — приемлемый риск (один ящик, невысокая нагрузка), ограничена
   `agent.maxSteps`/`llm.timeoutMs`.
3. **Аудит hash-chain.** Каждая запись хранит `prevHash` и
   `hash = SHA-256(prevHash + canonicalJson(entry))`. Append-only JSONL,
   `verify()` проходит цепочку и проверяет целостность. Это ближе к hash-chain, чем
   к HMAC из «Коли» (нет доступа к исходнику, нет отдельного HMAC-секрета) —
   осознанное отступление, фиксируется в `DECISIONS.md` с обоснованием: hash-chain
   даёт тот же эффект неизменности без доп. секрета.
4. **HTTP LLM контракт.** `HttpLlmClient` — под OpenAI-совместимый
   `chat/completions` с `tools`/`tool_calls` (де-факто стандарт), тестируется через
   `okhttp3:mockwebserver`, без реальной сети. Перед единственным опциональным живым
   smoke-тестом — уточнить у пользователя реальный `llm.endpoint`/формат.
5. **Dry-run режим.** Флаг `--mock --once` (мок-канал, один проход цикла вместо
   бесконечного опроса) — чтобы fat-jar можно было реально запустить и увидеть цикл
   целиком без Outlook на этой машине.

---

## 2. Версии зависимостей (проверено WebSearch 2026-08-13, перепроверить точечно перед реализацией)

| Библиотека | Версия | Примечание |
|---|---|---|
| Java | 8 (`maven.compiler.release=8`) | зафиксировано заданием |
| maven-shade-plugin | 3.6.2 | совместим с Java 8 |
| okhttp | 4.12.0 | линия 4.x — гарантированно Java 8+ |
| okhttp mockwebserver (test) | 4.12.0 | тесты `HttpLlmClient` без реальной сети |
| jackson-databind + jackson-dataformat-yaml | 2.18.x (одна minor-версия везде) | |
| snakeyaml | транзитивно через jackson-dataformat-yaml | не нужен явной зависимостью |
| slf4j-api | 2.0.x | |
| logback-classic | **1.3.15** (НЕ 1.4/1.5/1.6.x — требуют Java 11+) | последняя ветка с поддержкой JDK 8 |
| JUnit | 4.13.2 | зафиксировано заданием |
| **JaCoCo** (jacoco-maven-plugin) | 0.8.12 | добавляется в T0.1 для измерения покрытия (см. T10.2) |
| net.sf.jacob-project:jacob | см. примечание | |

**Примечание по JACOB:** задание указывает `net.sf.jacob-project:jacob:1.20`. При
подключении зависимости в T0.1 выяснилось, что на Maven Central под этим groupId
фактически опубликована только **1.14.3** (версия 1.19, упоминаемая на
mvnrepository.com, физически не резолвится через `repo.maven.apache.org` — проверено
`mvn clean compile`). Решение: использовать `net.sf.jacob-project:jacob:1.14.3`,
задокументировано в README и `DECISIONS.md`. Реальный JACOB на этой машине не
запускается — риск минимален (влияет только на сигнатуры COM-классов в контракте).

---

## 3. Граф зависимостей задач и параллельные треки

```
T0 (скелет) ──► CP1 (initial commit)
   │
   ├──► T1 (config)
   ├──► T2 (Msg model)
   │
   ├──► [∥ group=A] T3 (MailChannel+Mock+SeenStore)      — требует T2
   ├──► [∥ group=A] T4 (LlmClient+Mock+HttpLlmClient)    — требует только T0
   ├──► [∥ group=A] T5 (Tool iface + 3 инструмента)      — требует только T0
   ├──► [∥ group=A] T8.3 (PII-маскирование в логах)      — требует только T0
   └──► [∥ group=A] T8.4 (AuditLog hash-chain)           — требует только T0

group A (T3, T4, T5, T8.3, T8.4) — до 5 независимых веток, кандидаты на параллельных
субагентов. T3.4 (OutlookMailChannel-контракт) — подветка внутри T3, независима от
T3.2/T3.3 (нужен только T3.1) — может идти 6-й параллельной веткой.

T6 (tool-loop) ──requires──► T4 + T5
   ЕДИНСТВЕННАЯ H-задача ядра — НЕ параллелим, НЕ делегируем без присмотра,
   строго последовательно в основной сессии.

T7 (оркестрация + golden-тесты) ──requires──► T3 + T6

T8.1/T8.2 (LLM/COM graceful-фолбэк, вайринг) ──requires──► T7 + T8.3 + T8.4

T9 (fat-jar, dry-run) ──requires──► T7 + T8 полностью

T10 (security-review + coverage-отчёт) ──requires──► весь код готов (после T9)

T11.1 (README) ──requires──► всё готово (после T9/T10)
T11.2 (архитектура для непрограммиста) [∥] — можно параллельно с T8/T9/T10
T11.3 (экспорт сессии) — механически, в конце
T11.4 (финальный чек-лист) — в самом конце
```

---

## 4. Декомпозиция задач

### Phase 0 — Скелет проекта

**T0.1 Maven-скелет.** Effort: **L**.
`pom.xml` (packaging jar, Java 8, зависимости из раздела 2, `jacob` — compile scope),
структура пакетов из раздела 1, `logback.xml`, `config/config.example.yaml` (без
секретов, `apiKeyEnv: LLM_API_KEY` как пример), `.gitignore` (`target/`,
`*.local.yaml`, IDE-файлы). В `maven-surefire-plugin` — `classpathDependencyExcludes`
для `net.sf.jacob-project:jacob`, сразу, чтобы `mvn test` был зелёным на любой
машине с первого дня. Также подключить `jacoco-maven-plugin` (0.8.12) с `prepare-agent`
+ `report` goals — понадобится в T10.2. Не TDD-задача (нет логики) — но это первые
файлы приложения.

> **CP1 — initial commit.** Сразу после T0.1, до дальнейшей функциональности.
> **PUSH** рекомендован сразу после CP1.

---

### Phase 1 — Конфигурация

**T1.1 AppConfig + ConfigLoader.** Effort: **M**.
- RED: `ConfigLoaderTest` — фикстура `test-config.yaml` (все поля §3.5 задания:
  `llm.endpoint/model/apiKeyEnv/timeoutMs`, `agent.maxSteps`, `store.path`,
  `mail.pollSeconds/profile/folder`); тест грузит файл, проверяет каждое поле.
- GREEN: POJO `AppConfig` (вложенные `LlmConfig`/`AgentConfig`/`StoreConfig`/
  `MailConfig`), `ConfigLoader.load(Path)` через `ObjectMapper(new YAMLFactory())`.
- RED#2: битый/отсутствующий YAML → понятная ошибка, без секретов в сообщении.
- GREEN#2: валидация обязательных полей в loader'е.

**T1.2 EnvSecretResolver.** Effort: **L**.
- RED: инжектируемая `name -> value` (не реальный `System.getenv`, для тестируемости)
  резолвит секрет по `apiKeyEnv`; отсутствующая переменная → WARN, не NPE.
- GREEN: обёртка `Function<String,String>` вокруг `System.getenv` в проде,
  инжектируемая напрямую в тестах.

> CP2 — после T1.1+T1.2, тесты зелёные. Коммит.

---

### Phase 2 — Модель письма

**T2.1 Msg.** Effort: **L**.
- RED: конструктор/геттеры + `equals/hashCode` по `id` (нужен в Set/Map для SeenStore).
- GREEN: POJO `Msg(id, from, subject, body, receivedAtIso)`.

Коммитится вместе с CP2 либо отдельным маленьким коммитом сразу после.

---

### Phase 3 — MailChannel, Mock, SeenStore `[∥ group=A]`

**T3.1 Интерфейс MailChannel.** Effort: **L**. `List<Msg> fetchUnread()`,
`void reply(Msg original, String body) throws MailChannelException`. Не TDD
(интерфейс без поведения) — тестируется через реализации.

**T3.2 MockMailChannel.** Effort: **M**.
- RED: канал сконфигурирован списком `Msg`; `fetchUnread()` их возвращает;
  `reply()` записывает ответ в `getSentReplies()` и помечает письмо прочитанным
  (имитация Outlook — повторный `fetchUnread()` не возвращает); без `reply()` письмо
  остаётся в очереди.
- GREEN: реализация с `Set<String> readIds`.
- RED#2 / GREEN#2: флаги `throwOnFetch`/`throwOnReply` (для T8.2).

**T3.3 SeenStore (идемпотентность).** Effort: **H** — архитектурно критично.
- RED: временный файл (`@Rule TemporaryFolder`) — `markIfNew(id)` → `true` первый
  раз, `false` при повторном вызове в рамках одного инстанса.
- GREEN: `SeenStore` на `AtomicJsonFileStore`, атомарная проверка-и-запись.
- RED#2/GREEN#2: рестарт процесса — новый инстанс `SeenStore` с тем же файлом видит
  ранее отмеченный `id` (`markIfNew` → `false`). Синхронный flush на диск.
- RED#3/GREEN#3: повреждённый/пустой файл при старте → не падать, пустой сет + WARN.
- RED#4/GREEN#4: `unmark(id)` после `markIfNew(id)` → следующий `markIfNew(id)` снова
  `true` (нужен для компенсации при неуспехе обработки, T7.1).

**T3.3a AtomicJsonFileStore (общая утилита).** Effort: **M**. Может быть сделана до
T3.3, переиспользуется также в T5.3. TDD: запись во временный файл + атомарный
`Files.move` на целевой путь; тест — целевой файл всегда валиден, даже если запись
прервана между шагами.

**T3.4 OutlookMailChannel — контракт (JACOB).** Effort: **M**, не покрывается юнит-
тестами (COM недоступен). `[∥]` относительно T3.2/T3.3 — нужен только T3.1.
- Реализация через JACOB (`ActiveXComponent` на `Outlook.Application`, обход `Items`
  Inbox с `Unread=true`, `EntryID` → `Msg.id`, `Body`/`SenderEmailAddress`/`Subject`;
  `reply()` — `Reply()`/`Send()`). Перед написанием — свериться с документацией JACOB
  (WebSearch/примеры `Dispatch`/`ActiveXComponent`), не по памяти.
- Все COM-исключения оборачиваются в `MailChannelException` (доменная ошибка), не
  текут наружу как `ComFailException` — нужно для T8.2, тестируется через
  `MockMailChannel.throwOnFetch`. В коде/README явно отметить: класс объективно не
  покрыт юнит-тестами (нет Outlook+DLL на этой машине), проверяется на
  экзаменационной машине.

> CP3 — после T3.1–T3.3a, тесты зелёные (T3.4 не даёт тестов, коммитится отдельно
> сразу после, не блокирует CP3). **PUSH** после CP3.

---

### Phase 4 — LlmClient, Mock, Http `[∥ group=A]`

**T4.1 Модели чата + интерфейс LlmClient.** Effort: **H** (влияет на весь tool-loop
и HTTP-контракт). `ChatMessage(role, content, toolCallId?)`, `ToolSpec(name,
description, jsonSchema)`, `ToolCall(id, name, argumentsJson)`,
`ChatResponse(content?, List<ToolCall>, finishReason)`.
`LlmClient.chat(List<ChatMessage>, List<ToolSpec>) -> ChatResponse throws
LlmException`. Контракт фиксируется до начала T4.2.

**T4.2 MockLlmClient.** Effort: **M**.
- RED/GREEN: клиент сконфигурирован очередью `List<ChatResponse>`; каждый `chat()`
  возвращает следующий элемент; пустая очередь → fail fast в тесте, не NPE в проде.
- RED#2/GREEN#2: `throwOnNextCall` → бросает `LlmException` (для T8.1).

**T4.3 HttpLlmClient (okhttp) + MockWebServer.** Effort: **M/H**.
- Перед реализацией — WebFetch документации okhttp (`OkHttpClient.Builder()
  .callTimeout(...)`) и OpenAI-совместимого формата tool-calling JSON.
- RED/GREEN: enqueue успешного JSON с `tool_calls` → корректный парсинг в
  `ChatResponse`.
- RED#2/GREEN#2: enqueue 500/timeout → `LlmException`, не сырой `IOException`;
  маппинг таймаута из `llm.timeoutMs`.
- RED#3/GREEN#3: минимальный retry (первая попытка 500, вторая 200 → успех).
- Живой smoke-тест — отдельный self-skipping `@Test`
  (`Assume.assumeTrue(System.getenv("LIVE_LLM_SMOKE_KEY") != null)`), по умолчанию
  пропускается в `mvn test`, запускается только по явной команде пользователя.
  Реализуется в самом конце, не блокирует Phase 4.

> CP4 — после T4.1–T4.3 (без живого smoke), тесты зелёные. Коммит.

---

### Phase 5 — Инструменты `[∥ group=A]`

**T5.1 Интерфейс Tool + ToolError + ToolRegistry.** Effort: **M**. `Tool.name()`,
`description()`, `jsonSchema()`, `execute(Map<String,Object>) throws ToolError`.
`ToolError` — доменная ошибка, безопасная для возврата модели (без внутренних
деталей/секретов/stacktrace). `ToolRegistry` — `Map<String,Tool>` + `get(name)` →
`Optional`.

**T5.2 CurrentDatetimeTool.** Effort: **L**.
- RED/GREEN: `Clock.fixed(...)` инжектируется, `execute()` возвращает ожидаемую
  ISO-строку.

**T5.3 ReminderStore (JSON-стор).** Effort: **M** (переиспользует T3.3a).
- RED/GREEN: `add(text, dueIso)` → id, персистится; новый инстанс с тем же файлом
  видит запись.
- RED#2/GREEN#2: `find(query)` — регистронезависимый поиск по подстроке в `text`.

**T5.4 AddReminderTool.** Effort: **L/M**.
- RED/GREEN: `execute({"text":..., "dueIso":...})` → вызывает `ReminderStore.add`,
  возвращает подтверждение с датой.
- RED#2/GREEN#2: невалидный `dueIso` → `ToolError`, не сырой exception.

**T5.5 FindItemsTool.** Effort: **L**.
- RED/GREEN: `execute({"query":"Иван"})` → результаты `ReminderStore.find`.

> CP5 — после T5.1–T5.5, тесты зелёные. Коммит. **PUSH** после CP4+CP5 (можно одним
> пушем, если обе ветки завершены близко по времени).

---

### Phase 6 — Tool-loop (архитектурное ядро, не параллелить)

**T6.1 AgentToolLoop.** Effort: **H**. Строго последовательно, после T4 и T5,
основная сессия, `alwaysThinkingEnabled`.

Контракт: `run(String userMessage) -> String finalReplyText`, максимум `maxSteps`
итераций.

- Сценарий A (простой инструмент): один `tool_call current_datetime` → финальный
  текст. Базовый цикл (сообщения ↔ модель ↔ инструменты).
- Сценарий B (без инструментов): модель сразу отдаёт финальный текст → early exit.
- Сценарий C (maxSteps): модель всегда просит tool_call → цикл останавливается ровно
  после `maxSteps`, предсказуемый fallback-текст, не виснет.
- Сценарий D (неизвестное имя инструмента): `ToolRegistry` не находит → error-result
  обратно модели, не падает, продолжает в рамках `maxSteps`.
- Сценарий E (невалидный JSON аргументов): та же обработка ошибки, что и D.
- Сценарий F (исключение внутри инструмента, например `ToolError` от
  `AddReminderTool`): конвертируется в безопасный текст ошибки для модели, цикл
  продолжает. Единая точка обработки — любой `Throwable` из выполнения инструмента
  → безопасный текст, никогда сырой stacktrace наружу.

Каждый сценарий — отдельная пара RED→GREEN.

> CP6 — после всех сценариев A–F, тесты зелёные. Коммит (крупный, ядро готово).
> **PUSH** после CP6 — важная веха.

**SESSION BREAK #1** — здесь, если диалог подходит к порогу ~300k токенов, или для
гигиены контекста перед оркестрацией+идемпотентностью. При возобновлении: прочитать
`STATE.md` (следующий шаг → Phase 7) + `PROGRESS.md`; из `PLAN.md` — только раздел
Phase 7.

---

### Phase 7 — Оркестрация (MailProcessingService) + Golden-сценарии

**T7.1 MailProcessingService.** Effort: **H** — связывает идемпотентность, tool-loop
и MailChannel (см. раздел 1, пункт 2).

Контракт `processCycle()`: `fetchUnread()` → на каждое письмо:
`seenStore.markIfNew(id)` → если новое: тело письма как user-сообщение →
`AgentToolLoop.run(...)` → `mailChannel.reply(msg, result)` → при исключении на любом
шаге — `seenStore.unmark(id)` + WARN-лог, переход к следующему письму без падения
всего цикла.

- RED/GREEN: 1 письмо, финальный ответ без инструментов → `reply()` вызван с
  ожидаемым текстом, письмо помечено seen.
- RED#2/GREEN#2 — **ключевой тест идемпотентности сквозного потока**:
  `MockMailChannel` возвращает ОДНО И ТО ЖЕ письмо на двух последовательных
  `processCycle()` (эмуляция глитча Outlook) → после первого цикла `reply` вызван 1
  раз; после второго — НЕ повторно.
- RED#3/GREEN#3 — **рестарт процесса**: новый `MailProcessingService` с новым
  инстансом `SeenStore` (тот же файл) и тем же письмом от канала → повторной
  отправки нет.

**T7.2 Golden-сценарии.** Effort: **M**. 4 теста в `GoldenScenariosTest` — детальный
маппинг в разделе 5.

> CP7 — после T7.1+T7.2, тесты зелёные. Коммит. **PUSH** после CP7 — вторая крупная
> веха (полный happy-path + идемпотентность работают).

---

### Phase 8 — Graceful-фолбэки, логи, аудит

**T8.1 LLM graceful fallback.** Effort: **H** (критично для UX и сохранности писем).
- RED/GREEN: `throwOnNextCall` → `processCycle()` не бросает наружу, WARN
  (`llm_failed`, без ПДн), НЕ вызывает `reply()` (не плодить автоматические письма
  при полном отказе LLM — просто WARN + retry на следующем опросе), письмо не
  помечено seen (`unmark`).

**T8.2 COM graceful fallback.** Effort: **M**.
- RED/GREEN: `throwOnFetch=true` → WARN (`mail_channel_error`), без исключения;
  следующий `processCycle()` работает нормально.
- RED#2/GREEN#2: `throwOnReply=true` для одного из двух писем — первое обработано,
  второе — WARN, не seen, цикл продолжает (try/catch на каждое письмо внутри цикла,
  не один try/catch на весь метод).

**T8.3 PII-маскирование + LogEvents.** Effort: **M**. `[∥ group=A]`.
- RED/GREEN: `LogEvents.safePreview(String body)` — результат не содержит исходную
  подстроку целиком (например `"len=" + body.length() + " sha256=" + hashPrefix`).
- Константы event-keys: `agent_mail_seen`, `agent_tool_call`, `agent_reply_sent`,
  `llm_failed`, `mail_channel_error`, `agent_maxsteps_exceeded`.
- Ревью-пункт (→ T10.1): нигде `logger.info/warn/debug` не получает сырое
  `msg.getBody()` или сырые аргументы tool_call с текстом письма.

**T8.4 AuditLog (hash-chain).** Effort: **H** — безопасность/неизменяемость.
`[∥ group=A]`.
- RED/GREEN: `append(event)` несколько раз → `verify()` → `true`.
- RED#2/GREEN#2: изменить символ в файле после нескольких `append()` → `verify()` →
  `false`/исключение с номером записи.
- Вайринг: `MailProcessingService`/`AgentToolLoop` вызывают `append(...)` на ключевых
  точках (письмо обработано, каждый tool_call с маскированными аргументами из T8.3).

> CP8 — после T8.1–T8.4 (реализация + вайринг), тесты зелёные. Коммит. **PUSH**.

---

### Phase 9 — Fat-jar, dry-run, финальная сборка

**T9.1 App.main() — сборка графа зависимостей.** Effort: **M**. Composition root +
`AppSmokeTest` (mock-конфиг, флаг `--once` — без него бесконечный
`Thread.sleep(pollSeconds)`-цикл не тестируется юнит-тестом) → ассерт, что процесс не
бросил исключение.

**T9.2 maven-shade-plugin.** Effort: **L** (но проверить реальным `mvn package`).
Main-Class манифест → `App`.

**T9.3 Реальная проверка запуска (verification-before-completion).** Effort: **L**,
ОБЯЗАТЕЛЬНО реально выполнить и показать вывод: `mvn -f project/pom.xml clean
package` (локальный Maven из `other/tools/apache-maven-3.9.9/`), затем `java -jar
target/<...>.jar --mock --once` — показать реальный лог цикла (event-keys, без ПДн).

> CP9 — после T9.1–T9.3, `mvn test` зелёный, `mvn package` успешен, fat-jar
> запускается в dry-run. Коммит. **PUSH**.

**SESSION BREAK #2** — здесь, если сессия ещё не прерывалась после CP6. При
возобновлении: `STATE.md` (следующий шаг → Phase 10) + `PROGRESS.md`; из `PLAN.md` —
разделы Phase 10 и «8. Финальный чек-лист».

---

### Phase 10 — Security-review + отчёт о покрытии

**T10.1 Самостоятельный security-review.** Effort: **M/H**. Можно использовать
skill `security-review` как чек-лист, финальное решение — за агентом/пользователем.

Проверить явно:
- `git log`/`git show` по всем коммитам — нет закоммиченных секретов, нет
  `config.local.yaml` с реальными значениями (только `config.example.yaml` с
  плейсхолдерами).
- `.gitignore` действительно исключает локальные конфиги с секретами.
- Все вызовы логгера (grep `logger\.(info|warn|debug|error)`) — ни один не передаёт
  сырое тело письма/ПДн-содержащие аргументы напрямую.
- Инъекции через аргументы tool-call: `AddReminderTool`/`FindItemsTool` не
  выполняют `eval`, не строят пути к файлам из пользовательского ввода (пути стора
  фиксированы конфигом), не форматируют текст обратно в интерпретируемый контекст —
  зафиксировать как проверенный факт.
- `ToolError`/`LlmException`-сообщения, попадающие в письмо-ответ, не содержат
  внутренних путей/стектрейсов/секретов.
- `AuditLog`/`SeenStore`/`ReminderStore` не хранят полный текст письма без
  необходимости.

**T10.2 Отчёт о покрытии (JaCoCo).** Effort: **L**. Запустить `mvn test` (плагин
из T0.1 уже подключён) → `target/site/jacoco/index.html` — проверить, что пакеты
`config`, `mail` (кроме `OutlookMailChannel`), `llm` (кроме сетевой части
`HttpLlmClient`, которая покрыта через MockWebServer), `tools`, `agent`, `infra`
достигают ориентира ~80%+ по строкам (CLAUDE.md §3). Если какой-то пакет ниже цели —
добавить недостающие тесты по нему до коммита T10-CP. Зафиксировать итоговые % в
README (T11.1) и в `DECISIONS.md`, если решено осознанно оставить какой-то класс
ниже порога (например, `OutlookMailChannel` — объективно непокрываем без Outlook).

> CP10 — после исправлений по итогам ревью и покрытия (если были), тесты зелёные.
> Коммит (может быть "no changes needed" коммитом с записью в `DECISIONS.md`, если
> ревью ничего не нашло — тогда коммита кода не будет, только запись в
> `other/agent-logs/`).

---

### Phase 11 — Документация и сдача

**T11.1 README.md.** Effort: **L**. Разделы: build (через локальный Maven
`other/tools/apache-maven-3.9.9/bin/mvn` или обёртку), run (`--mock --once` для демо
без Outlook, реальный режим для экзаменационной машины), test, конфигурация
(`config.example.yaml`, переменные окружения), итоговый % покрытия из T10.2, известные
отступления от буквы задания (нет тестового ящика/JACOB DLL на этой машине —
`DECISIONS.md`; JACOB 1.19 вместо 1.20 — раздел 2), раздел **«Как я работал с ИИ»**
(стратегия промптов, что проверяли у модели перед использованием библиотек, что
отклонили и почему — например HMAC → SHA-256 hash-chain).

**T11.2 `other/архитектура-для-непрограммиста.md`.** Effort: **L**. `[∥]` —
параллельно с Phase 9/10. Простыми словами, без синтаксиса кода/Java-терминов: что
делает программа (аналогия «электронный секретарь»), из каких частей состоит
(почтовый модуль, «мозг»-ИИ, блокнот-инструменты, журнал), как это работает вместе на
примере одного письма (пришло → отмечено как уже виденное → передано «мозгу» →
записывает напоминание/ищет → отправляется ответ), что происходит при сбоях
(аналогии на «не отвечает наугад, ждёт следующей попытки» / «не падает, пробует
снова»). Явно не про код.

**T11.3 Экспорт сессии Claude Code.** Effort: **L**, не забыть в конце (или
периодически, чтобы не потерять историю). Скопировать релевантные
`~/.claude/projects/<проект>/*.jsonl` в `project/claude-session-export/`.

**T11.4 Финальный чек-лист (§11 задания).** Effort: **M** — проверить каждый пункт
руками/командами. См. раздел 8.

> CP11 — финальный коммит после T11.1–T11.4. Финальный **PUSH**.

---

## 5. Golden-сценарии (§10 задания) → тесты

| # | Письмо | Ожидаемое поведение | Где покрывается |
|---|---|---|---|
| 1 | «Напомни завтра в 10 позвонить Ивану» | `add_reminder` вызван, ответ с датой | `GoldenScenariosTest#golden1_addReminder`: script = [tool_call `add_reminder{text, dueIso}`, финальный текст-подтверждение] → `ReminderStore` содержит запись, `reply` содержит дату. «Завтра» вычисляется через тот же `Clock.fixed`, что инжектирован в `CurrentDatetimeTool`, чтобы избежать флаки |
| 2 | «Что у меня запланировано?» | Поиск/список | `#golden2_findItems`: `ReminderStore` предварительно содержит записи; script = [tool_call `find_items{query:""}`, финальный текст со списком] → ответ содержит записи |
| 3 | «Какое сегодня число?» | `current_datetime`, ответ с датой | `#golden3_currentDatetime`: script = [tool_call `current_datetime{}`, финальный текст с датой из `Clock.fixed`] |
| 4 | Пустое/мусорное письмо | Graceful, без падения, без лишних действий | `#golden4_emptyOrGarbage`: `body=""` и случайный мусорный текст; script = [финальный текст без tool_calls] → `reply` вызван ровно раз, `ReminderStore`/`AuditLog` без новых tool-записей |

Все 4 теста используют общую инфраструктуру (`MockMailChannel` + `MockLlmClient` +
временные файлы `SeenStore`/`ReminderStore`) — частные случаи T7.1, выделены в
отдельный класс, чтобы на защите можно было сослаться прямо на конкретный пример §10.

---

## 6. Точки завершения сессии (бюджет ~300k токенов/сессию)

| Точка | После задачи | Что прочитать при возобновлении |
|---|---|---|
| SESSION BREAK #1 | CP6 (tool-loop готов) | `STATE.md` (→ Phase 7), `PROGRESS.md`; из `PLAN.md` только раздел Phase 7 |
| SESSION BREAK #2 | CP9 (fat-jar собран и запускается) | `STATE.md` (→ Phase 10), `PROGRESS.md`; разделы Phase 10 и «8. Финальный чек-лист» |
| (опционально) #0.5 | CP4/CP5, если контекст занят параллельными ветками | `STATE.md`, `PROGRESS.md`; раздел Phase 6 |

Общее правило: при любом незапланированном прерывании — сразу обновить `STATE.md`
(текущая задача/последний шаг/следующий шаг/блокеры) до завершения сессии.

---

## 7. Задача: «архитектура для непрограммиста»

См. T11.2 — файл `other/архитектура-для-непрограммиста.md`. Без кода/Java-терминов,
простыми словами и аналогиями. Можно писать раньше по времени, как только раздел 1
плана не будет меняться — финальная вычитка после стабилизации кода.

---

## 8. Финальный чек-лист готовности (§11 задания)

- [x] `mvn package` → fat-jar собирается — T9.2/T9.3 (переподтверждено в T11.4: `BUILD SUCCESS`, `target/mail-agent.jar`)
- [x] fat-jar запускается (`--mock --once` dry-run — нет живого Outlook на этой машине) — T9.3 (переподтверждено в T11.4: exit code 0, `audit.jsonl` с 3 событиями цикла)
- [x] `mvn test` зелёный без Outlook (JACOB исключён через `classpathDependencyExcludes`) — T0.1 + весь набор тестов (переподтверждено в T11.4: 86/86)
- [x] `MailChannel`: JACOB-реализация (контракт) + мок — T3.1, T3.2, T3.4
- [x] ≥2 инструмента, tool-loop работает на моке — T5.2–T5.5, T6.1 (реализовано 3: current_datetime, add_reminder, find_items)
- [x] идемпотентность (seen) + переживает рестарт — T3.3, T7.1
- [x] конфиг-driven, секреты из env, в git ничего секретного — T1.1, T1.2, T10.1 (переподтверждено в T11.4: `git log --all -p` без ключей/секретов)
- [x] graceful-фолбэк на LLM и COM — T8.1, T8.2
- [x] структурные логи, без ПДн — T8.3
- [x] аудит-журнал действий (hash-chain) — T8.4
- [x] ~80%+ покрытие детерминированной логики, подтверждено JaCoCo-отчётом — T10.2 (96.0% по целевым пакетам, переподтверждено в T11.4)
- [x] `PLAN.md` + экспорт сессии Claude Code + `README.md` — этот файл, T11.3, T11.1
- [x] `other/архитектура-для-непрограммиста.md` — T11.2
- [x] security-review проведён и задокументирован — T10.1
- [x] golden-сценарии §10 явно покрыты тестами — T7.2 / раздел 5

---

## 9. Открытые моменты для внимания при реализации (не блокируют утверждение плана)

1. **JACOB 1.20 vs 1.19** — принять решение при T3.4, зафиксировать в `DECISIONS.md`.
2. **Формат HTTP LLM API** — OpenAI-совместимый по умолчанию; если реальный эндпоинт
   пользователя отличается — уточнить перед T4.3/перед живым smoke-тестом.
3. **AuditLog: hash-chain vs HMAC** — выбран hash-chain (SHA-256); расширение до HMAC
   возможно без изменения внешнего контракта `AuditLog.append/verify`, если
   потребуется на защите.
4. **Момент отметки seen при сбое** — стратегия «не терять письма, допускать
   повторные попытки при простое LLM» вместо «промолчать один раз» — локализованное
   изменение в `MailProcessingService`, если на защите потребуется другое поведение.

### Критичные файлы для реализации
- `project/pom.xml`
- `project/src/main/java/ru/assistant/agent/AgentToolLoop.java`
- `project/src/main/java/ru/assistant/agent/MailProcessingService.java`
- `project/src/main/java/ru/assistant/agent/SeenStore.java`
- `project/src/main/java/ru/assistant/infra/AuditLog.java`
