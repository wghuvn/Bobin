# Задача №2 — результат реализации фундамента

Дата: 29 октября 2026. Bobin Craft 2.7.7-clean / Fabric 1.20.1 / Java 17.

## Выполнено

- Добавлены `WeaponId`, `WeaponDefinition`, `WeaponStateSnapshot`, `WeaponActionEvent`, `WeaponUserView` в подпакетах `com.bobincraft.armament`.
- Добавлены шесть JSON Schema Draft 2020-12: общие типы плюс weapon, visual, rig, sockets, animation-events.
- Добавлены неизменяемые client metadata records, явные codecs, структурная и семантическая валидация, в том числе bundle references.
- Подготовлены только интерфейсы будущих model loader, animation controller и renderer; конкретных реализаций нет.
- Добавлены пять JUnit test-классов, corpus положительных/отрицательных случаев и два проверочных скрипта.

Валидация: неизвестные/отсутствующие/null поля; дубликаты JSON keys; строгие типы без coercion; точные long; версии; безопасные ID; лимиты; циклы и ссылки узлов; уникальность имён/индексов; sockets; normalized quaternion; cosmetic-only markers, их порядок и временные границы. Стандартные JSON Schema и Java semantic checks разграничены явно. В Gson 2.10 дополнительно закрыто принятие raw control characters, невалидного escape `\'` и смешанного регистра true/false/null. Есть регрессионные тесты самого parser, а не только неверных определений.

Формат и назначение API: [FOUNDATION_RU.md](FOUNDATION_RU.md).
Полный список добавленных файлов: [CHANGED_FILES.txt](CHANGED_FILES.txt).

## Фактические проверки

| Проверка | Результат |
|---|---|
| Новый production-пакет: `javac --release 17` на настоящем Temurin 17.0.21+9 | PASS, все 22 Java-файла нового production-пакета скомпилированы |
| Common отдельно, без client/Minecraft/Fabric classes в classpath | PASS |
| Production class-файлы | Все 30 имеют major 61 (Java 17); число включает вложенные типы |
| Изолированные JUnit-тесты нового пакета | **164 выполнено, 164 успешно, 0 ошибок, 0 пропусков** |
| Пять test-классов | API, валидация/serialization, client metadata, строгий JSON, границы зависимостей |
| Проверка JSON Schema | PASS: шесть корректных схем, **83 structural cases**; semantic-only отказы отдельно проверены Java |
| Сверка всех 2381 исходных файлов с ZIP задачи №1 | **0 изменений, 0 удалений** |
| Полная Gradle build | BLOCKED до компиляции, exit 1 |
| Отдельная Gradle test | BLOCKED до выполнения тестов, exit 1 |
| Minecraft client/server и регрессия существующих систем | Не запускались |

Проверка JUnit выполнена в новом отдельном каталоге результата; XML относится к последнему прогону. Использованы Gson 2.10 и JUnit Platform Console 1.10.3 (Jupiter 5.10.3). Это проверочные jars вне проекта, загруженные из Maven Central с проверкой опубликованных контрольных сумм. Новых зависимостей в Gradle нет. JDK также загружен из официального Temurin release с проверкой опубликованного SHA-256.

Логи и machine-readable результаты:

- [Изолированная компиляция и JUnit](validation/isolated-tests.log)
- [JUnit XML](validation/TEST-junit-jupiter.xml)
- [Схемы](validation/schema-validation.log)
- [Gradle build](validation/gradle-build.log)
- [Gradle test и HTTP 403](validation/gradle-test.log)
- [Сводка JSON](validation/results.json)

## Точная причина блокировки полной сборки

Среда Linux, поэтому вместо `gradlew.bat build -x test` выполнен эквивалент:

```text
bash gradlew build -x test --no-daemon
bash gradlew test --no-daemon --info
```

Оба запуска используют JDK 17, Gradle wrapper 8.8 и текущую конфигурацию проекта без изменений. Для сети переданы настройки существующего proxy и системного Java truststore; проверка TLS не отключалась. Gradle distribution успешно получен с исходным checksum wrapper.

При конфигурации проекта Gradle получает:

```text
HTTP 403 Forbidden:
https://maven.fabricmc.net/fabric-loom/fabric-loom.gradle.plugin/1.7.4/fabric-loom.gradle.plugin-1.7.4.pom
```

Независимый HTTPS-запрос через тот же proxy также получает `Tunnel connection failed: 403 Forbidden`. В действующей restricted network policy домен `maven.fabricmc.net` отсутствует. Поэтому Fabric Loom 1.7.4 не разрешается, `compileJava`/`test` всего проекта не запускаются. Это подтверждённое ограничение доступа, не доказательство дефекта нового Java API или отсутствия версии Loom.

Для продолжения полной проверки необходимо разрешить `maven.fabricmc.net` в настройках сети среды и повторить build/test. Следующие стадии скачивания Minecraft ещё не проверены; после снятия этого блока могут выявиться другие требования доступа. Сетевые настройки и зависимости проекта в рамках задачи не изменялись.

**Изолированная компиляция нового API доказана. Успешная полная сборка мода и игровая совместимость не заявляются.**

## Границы изменений

Все исходные файлы архива остались побайтно неизменными, включая:

- `build.gradle`, опасный `retiredFiles`/exclude блок, `gradle.properties`, wrapper;
- `fabric.mod.json`, существующие mixin и entrypoints;
- NPC, AI, drones, grenades, GROM-7, RPG, технику, combat, destruction и worldgen;
- все исходные runtime resources, определения, звуки, модели и текстуры.

Новый пакет не подключён к регистрации мода, тикерам, рендеру или сети. Старые оружейные классы не используются. Отсутствие изменения существующего кода подтверждено checksum-сверкой; функциональная регрессия Minecraft не проверена из-за блока Gradle.

Оружие, игровые действия, анимационные клипы, модели, GLB backend, звук, NPC adapters и runtime data files не создавались. `test:*` данные — только синтетические metadata fixtures для тестов, не зарегистрированное оружие.

Задача остановлена на фундаменте. К GLB-загрузке/рендерингу без подтверждения пользователя перехода нет.
