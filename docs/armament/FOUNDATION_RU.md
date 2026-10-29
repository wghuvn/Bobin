# Фундамент armament, версия контракта 1

Задача №2 для Bobin Craft 2.7.7-clean, Fabric 1.20.1, Java 17.
Основа — согласованный ARCHITECTURE_RU.md из задачи №1.

## Что добавлено

`com.bobincraft.armament.api`: `WeaponId`, `WeaponStateSnapshot`, `WeaponActionEvent`, `WeaponUserView`.
`com.bobincraft.armament.definition`: `WeaponDefinition`, `WeaponDefinitionValidator`.
`serialization` и `validation`: явные JSON-кодеки, строгий разбор, типизированные ошибки с путём поля.

Common/API зависят только от JDK и вспомогательных common-классов; кодеки используют существующий Gson. Нет ссылок на Minecraft, Fabric, LWJGL, armament/client, старое оружие или другие системы Bobin. Отдельная компиляция common без клиентских классов и проверка constant pool входят в проверки.

`client/resource`: неизменяемые описания visual, rig, sockets, transforms и cosmetic markers; JSON-кодеки и проверка ссылок между метаданными. Эти классы не открывают ресурсы, не загружают GLB, не вычисляют анимацию, не создают звуки и не регистрируют renderer.

Только интерфейсы: `client/model/WeaponModelAsset`, `WeaponModelLoader`, `client/animation/AnimationController<P>`, `client/render/WeaponRenderer<P,C>`. Конкретные типы mesh/pose/render context и backend остаются будущим этапом; Minecraft player нельзя подставлять глобально вместо явно переданного владельца.

Регистрации модуля, entrypoints, mixin, items, серверные службы, сетевые пакеты и файлы конкретного оружия не добавлены. Интерфейсы не имеют production-реализаций. Client-пакет отделён по направлению зависимостей в существующем `src/main/java`; перенос всего мода в split source sets не выполнялся.

## Common-контракты

- `WeaponId(namespace,path)`: явный lowercase namespace:path, не более 255 символов, без неявного namespace. Namespace — `[a-z0-9_-]+(?:[.][a-z0-9_-]+)*`; path — `[a-z0-9_-]+(?:[./][a-z0-9_-]+)*`. Пустые сегменты, абсолютные пути, `..`, обратные слеши, URI и пробелы запрещены. Это намеренно ограниченный безопасный профиль идентификаторов, а не полная копия всех допустимых Minecraft Identifier.
- `WeaponDefinition(schemaVersion,id,actions)`: версия 1, максимум 64 уникальных semantic action ID, без статов, client resource ID или механик. Пустой список допустим для фундамента. Это не регистрация предмета.
- `WeaponStateSnapshot`: UUID владельца и экземпляра, ID определения, hand, server tick, revision, optional action ID, sequence, start tick. Счётчики неотрицательны. Активное действие требует sequence>0 и start<=serverTick; idle — отсутствующий action_id и start=0. Idle может сохранять предыдущий sequence.
- `WeaponActionEvent`: идентичность, hand, action ID, положительный sequence и неотрицательный start tick. Это данные уведомления, не команда выстрела и не анимационный marker.
- `WeaponUserView`: UUID пользователя и optional snapshot по hand; не ограничен PlayerEntity. Реализация обязана вернуть снимок указанного владельца/руки. Реализации для существующих NPC не создавались.
- `WeaponDefinitionValidator.validate`: проверяет соответствие snapshot/event определению и наличие объявленного action ID. Не исполняет действие.

Records копируют входные коллекции. Нельзя изменять определения через исходный List/Map/Set после создания.

## JSON и JSON Schema

Шесть файлов Draft 2020-12 в `schemas/armament`:

| Файл | Назначение |
|---|---|
| `common.schema.json` | Общие ID, имена и transforms |
| `weapon.schema.json` | Common определение: schema_version, id, actions |
| `visual.schema.json` | model, rig_id, sockets_id, events_id и view profiles |
| `rig.schema.json` | Имена/индексы узлов, parent и role bindings |
| `sockets.schema.json` | Именованные sockets, узел и локальный transform |
| `animation-events.schema.json` | Длительности и cosmetic marker metadata; это не анимационные клипы |

Схемы используют локально зарегистрированные `urn:bobincraft:armament:*:1`, сетевого получения `$ref` не требуется. Корневые/вложенные фиксированные объекты запрещают неизвестные свойства; roles — именованный map с валидацией ключей/значений.

Дополнительно к стандартным структурным правилам Java проверяет то, что не выражается обычной JSON Schema: уникальность имён при разных содержимых объектов, циклы/родителей/корень, role bindings, нормализацию quaternion, сортировку и границы markers относительно duration, ссылки между документами. Поэтому структурно допустимый JSON может быть семантически отвергнут. В corpus это явно разделено `structural_valid` и `valid`.

Прочтение выполняется через `WeaponJson.readDefinition/readSnapshot/readActionEvent` и `WeaponVisualJson.readVisual/readRig/readSockets/readEvents`. Обратные операции — соответствующие `write*`. Не использовать `new Gson().fromJson(..., Record.class)` как обход валидации: явные кодеки всегда вызывают проверяемые конструкторы. JSON codec не является реализацией resource/model loader: он только обрабатывает переданную строку.

Пример диагностики: `$.views.npc.translation[0]: expected number`. `DefinitionValidationException.path()` и `.reason()` доступны отдельно. Дубликаты ключей обнаруживаются до их перезаписи; неизвестные поля и null отвергаются. Строки не преобразуются в числа/enum. Счётчики читаются точно в 64-bit integer без промежуточного double. Документ ограничен 1 048 576 UTF-16 символами и глубиной 64.

У Gson 2.10 `lenient=false` недостаточно для запрета raw control characters в строках; перед tokenizer выполняется проверка этого правила JSON, допустимых escape-последовательностей и lowercase literals, с регрессионными тестами. Комментарии, одинарные кавычки, unquoted keys, NaN/Infinity и trailing data не допускаются.

Поле `action_id` в snapshot отсутствует при idle; null не является его допустимой заменой. Snapshot/event используют canonical lowercase UUID и `main_hand`/`off_hand`. Их сериализация не является подключённым сетевым протоколом; версия будущего сетевого envelope будет отдельной задачей.

## Client-метаданные

Visual требует GLB/glTF resource ID и first_person/third_person/npc profiles. GUI/ground/fixed — необязательные профили. Безопасность реальных glTF URI, существование model/clip/node indices и GPU lifecycle будут проверяться будущим loader: сейчас никаких файлов модели не открывается.

Rig: 1..512 узлов с уникальными именами и индексами 0..65535, ровно один корень, без циклов/неизвестных родителей. Role `weapon_root` обязателен и указывает на корень; остальные роли произвольны в рамках lower_snake_case.

Sockets: 0..128 точек, уникальные имена, rig_id и локальные transforms. Для полного набора метаданных необходимо вызвать `WeaponVisualValidator.validate(visual,rig,sockets,events)` — он проверяет ID документов и наличие socket nodes. Отдельный JSON reader не может подтвердить ссылку на ещё не предоставленный документ.

Transform: translation — 3 конечных числа [-1024,1024], rotation — XYZW quaternion с squared norm в пределах 1e-5 от 1, scale — 3 положительных числа не более 1024. Единицы translation — метры/блоки. Никакого вычисления матриц/анимаций сейчас нет.

Events: до 128 clip metadata records, duration (0,3600] секунд, до 1024 markers в каждом. Имена уникальны, markers упорядочены, времена в пределах duration. `kind` может быть только `cosmetic`. Нет payload с кодом, скрипта, вызовов gameplay, audio или dispatcher. Данные фиктивного `test:*` corpus существуют только в `src/test/resources`, а не в runtime resources.

## Проверки

Обычный путь с доступом к репозиториям:

```sh
# JAVA_HOME должен указывать на реальный JDK 17
bash gradlew build -x test --no-daemon
bash gradlew test --no-daemon
```

В Windows: `gradlew.bat build -x test`, затем `gradlew.bat test`.

Изолированный fallback (не заменяет сборку Fabric):

```sh
ARMAMENT_JAVA_HOME=/path/to/jdk17 \
ARMAMENT_TEST_LIB_DIR=/path/to/verified-jars \
bash scripts/test_armament_api.sh
```

Требуются `gson-2.10.jar` и `junit-platform-console-standalone-1.10.3.jar`: версии Gson Minecraft 1.20.1 и JUnit Platform для уже используемого Jupiter 5.10.3. Jars не поставляются в исходном проекте и не добавлены в runtime зависимости. Скрипт не скачивает их, компилирует common отдельно и использует новый каталог результата для каждого запуска. Нужны bash и rg.

Проверка самих схем и того же corpus: `python3 scripts/verify_armament_schemas.py` (dev-инструменты `jsonschema`/`referencing`; в проверочной среде уже установлены, в Gradle/мод не добавлялись).

Фактический результат этой задачи и список файлов: [TASK2_REPORT_RU.md](TASK2_REPORT_RU.md), [CHANGED_FILES.txt](CHANGED_FILES.txt).
